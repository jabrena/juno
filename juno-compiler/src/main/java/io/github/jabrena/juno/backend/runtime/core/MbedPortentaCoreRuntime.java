package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.ResolvedRuntimeConfig;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.Architecture;

/**
 * The Arduino Portenta H7 M7 variant of the Arduino Mbed core. Its standard Arduino delay and GPIO functions are real
 * symbols, so the Thumb-2 backend can use direct calls. The Portenta's built-in RGB LED is exposed by the variant as
 * digital pin 24 ({@code LED_BUILTIN}).
 *
 * <p>The basic Arduino runtime pieces shared with the UNO R4 are inherited. Thread execution uses Mbed OS's RTOS
 * threads rather than the UNO R4's bare-metal stack switcher.
 */
final class MbedPortentaCoreRuntime extends RenesasCoreRuntime implements CoreRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();

    @Override
    public String wifiIncludes(boolean udp) {
        return "#include <WiFi.h>\n";
    }

    @Override
    public String wifiHelpers() {
        // The Mbed Portenta WiFi library requires the password argument even for an open AP.
        return shimLibraries.wifiHelpers().replace("WiFi.beginAP(ssid);", "WiFi.beginAP(ssid, \"\");");
    }

    @Override
    public String i2cBus() {
        // The Portenta variant exposes its secondary I2C bus as Wire1.
        return "Wire1";
    }

    @Override
    public boolean hasSe050C2() {
        return true;
    }

    @Override
    public String httpsServerTransport() {
        return runtimeSource.load("esp32/https-server-transport.cpp");
    }

    /**
     * The Portenta Mbed Wi-Fi library otherwise looks for its CA file on the module's {@code /wlan} filesystem.
     * That filesystem is not present on every board, so keep the same Mozilla root set used by the Nano ESP32 in the
     * generated image and install it before each TLS client connects.
     */
    @Override
    public String httpsHelpers() {
        return trustedRootsDefinition() + """
                struct JunoPortentaHttpsClient : WiFiSSLClient {
                  JunoPortentaHttpsClient() {
                    setCACert(juno_portenta_ca);
                  }
                };
                """;
    }

    @Override
    public String httpsClientDeclaration() {
        return "JunoPortentaHttpsClient client;";
    }

    /**
     * The roots the Portenta's TLS clients trust. {@code JUNO_TLS_CA} replaces the Mozilla bundle with a custom CA, as on
     * the ESP32 cores. The Portenta's Mbed TLS build compiles out SECP521R1, so the bundle leaves out the P-521 root that
     * would otherwise make it refuse the whole list.
     */
    private String trustedRootsPem() {
        String customCa = System.getenv("JUNO_TLS_CA");
        return customCa != null && !customCa.isBlank()
                ? customCa
                : CertificateBundle.pemWithoutCurve(runtimeSource.load("esp32/ca-certificates.pem"), 521);
    }

    /**
     * The trusted roots as one file-scope definition, {@code juno_portenta_ca}, shared by the HTTPS and STARTTLS clients.
     * The guard keeps the list in flash once when a program uses both, since the bundle is about 120 KB of text.
     */
    private String trustedRootsDefinition() {
        return "#ifndef JUNO_PORTENTA_TRUST_ROOTS\n#define JUNO_PORTENTA_TRUST_ROOTS\n"
                + CertificateBundle.cPem("juno_portenta_ca", trustedRootsPem()) + "#endif\n";
    }

    /**
     * Smtp's STARTTLS on Mbed: a TCP socket that a TLS wrapper takes over in place once the server accepts STARTTLS. The
     * Arduino WiFiSSLClient starts TLS from the first byte, and ESP_SSLClient is a BearSSL client the Mbed core does not
     * link. The server's chain is checked against the same roots as HTTPS, and the host name must match.
     */
    @Override
    public String smtpStarttlsInclude() {
        return "#include <netsocket/TCPSocket.h>\n#include <netsocket/TLSSocketWrapper.h>\n";
    }

    @Override
    public String smtpStarttlsHelpers() {
        return trustedRootsDefinition() + """
                struct JunoMbedSmtpClient {
                  TCPSocket tcp;
                  TLSSocketWrapper tls;
                  const char* host = nullptr;
                  nsapi_error_t trust = NSAPI_ERROR_OK;
                  int peeked = -1;
                  bool open = false;
                  bool secure = false;

                  JunoMbedSmtpClient() : tls(&tcp, nullptr, TLSSocketWrapper::TRANSPORT_KEEP) {}

                  // The greeting and the STARTTLS reply travel in clear text; everything after the handshake uses TLS.
                  Socket* active() {
                    return secure ? static_cast<Socket*>(&tls) : static_cast<Socket*>(&tcp);
                  }

                  bool connect(const char* name, uint16_t port) {
                    host = name;
                    NetworkInterface* network = WiFi.getNetwork();
                    SocketAddress address;
                    if (network->gethostbyname(name, &address) != NSAPI_ERROR_OK) return false;
                    address.set_port(port);
                    if (tcp.open(network) != NSAPI_ERROR_OK) return false;
                    tcp.set_timeout(15000);
                    open = tcp.connect(address) == NSAPI_ERROR_OK;
                    return open;
                  }

                  void setCACert(const char* pem) {
                    trust = tls.set_root_ca_cert(pem);
                  }

                  bool connectSSL() {
                    if (trust != NSAPI_ERROR_OK) return false;
                    tls.set_hostname(host);
                    for (int attempt = 0; attempt < 200; attempt++) {
                      nsapi_error_t err = tls.connect();
                      if (err == NSAPI_ERROR_OK || err == NSAPI_ERROR_IS_CONNECTED) {
                        secure = true;
                        return true;
                      }
                      if (err != NSAPI_ERROR_IN_PROGRESS && err != NSAPI_ERROR_ALREADY) return false;
                      delay(100);
                    }
                    return false;
                  }

                  size_t print(const char* text) {
                    size_t length = strlen(text);
                    size_t sent = 0;
                    active()->set_timeout(15000);
                    while (sent < length) {
                      nsapi_size_or_error_t count = active()->send(text + sent, length - sent);
                      if (count <= 0) {
                        open = false;
                        break;
                      }
                      sent += static_cast<size_t>(count);
                    }
                    return sent;
                  }

                  int available() {
                    if (peeked >= 0) return 1;
                    if (!open) return 0;
                    active()->set_timeout(0);
                    uint8_t value = 0;
                    nsapi_size_or_error_t count = active()->recv(&value, 1);
                    if (count == 1) {
                      peeked = value;
                      return 1;
                    }
                    if (count != NSAPI_ERROR_WOULD_BLOCK) open = false;
                    return 0;
                  }

                  int read() {
                    if (available() == 0) return -1;
                    int value = peeked;
                    peeked = -1;
                    return value;
                  }

                  bool connected() const {
                    return open;
                  }

                  void stop() {
                    if (secure) tls.close();
                    tcp.close();
                    open = false;
                  }
                };
                """;
    }

    @Override
    public String smtpStarttlsClient() {
        return "JunoMbedSmtpClient client;";
    }

    @Override
    public String smtpStarttlsTrust() {
        return "client.setCACert(juno_portenta_ca);";
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        return new WatchdogRuntime("#include <mbed.h>\n",
                "mbed::Watchdog::get_instance().start(" + timeoutMillis + "u);",
                "mbed::Watchdog::get_instance().kick();", timeoutMillis);
    }

    @Override
    public String uuidRandomHelpers() {
        return runtimeSource.load("mbed/uuid-random.cpp");
    }

    @Override
    public String mouseIncludes() {
        return "#include <USBMouse.h>\n";
    }

    @Override
    public String mouseHelpers() {
        return """
                static USBMouse juno_portenta_mouse;
                class JunoPortentaMouse {
                 public:
                  void begin() {}
                  void move(signed char x, signed char y) { juno_portenta_mouse.move(x, y); }
                };
                static JunoPortentaMouse Mouse;
                """;
    }

    @Override
    public boolean preemptsAsynchronously() {
        return false;
    }

    @Override
    public String threadIncludes() {
        return "#include <mbed.h>\n#include <new>\n";
    }

    @Override
    public String threadPort(ResolvedRuntimeConfig config) {
        return threadRuntime.mbedPort(config.threadStackBytes());
    }

    @Override
    public int builtinLedPin() {
        return 24;
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public String gpioFunction(String arduinoFunction) {
        return arduinoFunction.equals("pinMode") ? "juno_pin_mode" : arduinoFunction;
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    @Override
    public String parallelBusHelpers() {
        throw unsupported("ParallelBus");
    }

    @Override
    public String gpioAnalogPins() {
        return "15, 16, 17, 18, 19, 20";
    }

    @Override
    public String yieldFunction() {
        return """
                // The Arduino Mbed core provides yield(); the generated assembly's `bl yield` reaches it directly.
                // Portenta's pinMode overload has C++ linkage, so expose the Arduino-pin overload to the assembly ABI.
                extern "C" void juno_pin_mode(int32_t pin, int32_t mode) {
                  pinMode(static_cast<pin_size_t>(pin), static_cast<PinMode>(mode));
                }
                """;
    }

    private CompileException unsupported(String feature) {
        return Architecture.THUMB2.unsupported(feature + " on the Portenta H7");
    }
}
