package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.ResolvedRuntimeConfig;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.SntpServer;
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

    /**
     * The Hat Carrier's Raspberry Pi header reaches STM32 pins that have no Arduino number, so the core's raw pin
     * indices are accepted under the names {@code GPIO159} (PI5), {@code GPIO160} (PI6) and {@code GPIO161} (PI7).
     * Those are the variant's own indices into its pin table, which the core's {@code digitalWrite} also takes.
     */
    @Override
    public String gpioAdditionalDigitalPins() {
        return "159, 160, 161, -1";
    }

    /** The H7's SPI FIFO fed directly: the core's own transfer sends one byte per driver call. */
    @Override
    public String spiWriteSource() {
        return "mbed/spi-write.cpp";
    }

    @Override
    public String sdIncludes() {
        // Mbed's headers before SdFat's: SdFat defines an F(str) macro that Mbed's templates would then expand.
        return "#include <SPI.h>\n#include <SDMMCBlockDevice.h>\n#include <SdFat.h>\n";
    }

    /**
     * SdFat over the core's SDMMC block device: the Hat Carrier's microSD slot is on the H7's SDMMC2 bus, not SPI, so
     * the card has no chip select and SdFat mounts its volume through {@code JunoSdmmcDevice}. The SD and
     * {@code RandomAccessFile} helpers are otherwise the shared ones.
     */
    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes,
                runtimeSource.load("mbed/sdmmc-block-device.cpp"),
                "juno_sdmmc.begin() && static_cast<FatVolume&>(juno_sd).begin(&juno_sdmmc)");
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
     * The Mbed TLS peer certificate and the TLS socket behind the Wi-Fi client, which the date check reads, and the
     * Mbed TLS client the HTTPS client builds on.
     */
    @Override
    public String httpsInclude() {
        return """
                #include "TLSSocket.h"
                #include "MbedSSLClient.h"
                #include "mbedtls/ssl.h"
                #include "mbedtls/x509_crt.h"
                """;
    }

    /**
     * The Portenta Mbed Wi-Fi library otherwise looks for its CA file on the module's {@code /wlan} filesystem.
     * That filesystem is not present on every board, so keep the same Mozilla root set used by the Nano ESP32 in the
     * generated image, as DER in flash (see {@link #trustedRootsDefinition}). Every TLS socket shares one chain built
     * from it, in place of the Arduino client's {@code setCACert}, which parses the whole PEM bundle again, into heap
     * copies, for each socket: that cost ~200 KB per connection and left room for a single session.
     *
     * <p>The Portenta core ships Mbed TLS prebuilt without certificate dates, so the handshake accepts an expired
     * certificate. Each connection therefore first sets the clock over SNTP ({@code JUNO_SNTP_HOST}, default
     * {@code pool.ntp.org}), then checks the validity dates of every certificate the server sent, and fails if one
     * is outside them. If no time can be set, the connection fails.
     */
    @Override
    public String httpsHelpers() {
        SntpServer server = SntpServer.from(System.getenv());
        return trustedRootsDefinition() + """
                // Sets the system clock over SNTP unless it is already set; false when it cannot be set.
                static bool juno_portenta_tls_clock_once() {
                  WiFiUDP juno_tls_clock_udp;
                  if (juno_tls_clock_udp.begin(2391) == 0) return false;
                  uint8_t packet[48];
                  memset(packet, 0, sizeof(packet));
                  packet[0] = 0x1B;  // leap indicator 0, version 3, mode 3 (client)
                  uint32_t seconds = 0;
                  if (juno_tls_clock_udp.beginPacket("${HOST}", static_cast<uint16_t>(${PORT})) == 1
                      && juno_tls_clock_udp.write(packet, sizeof(packet)) == sizeof(packet)
                      && juno_tls_clock_udp.endPacket() > 0) {
                    uint32_t start = static_cast<uint32_t>(millis());
                    while (static_cast<uint32_t>(millis()) - start < 2000u) {
                      if (juno_tls_clock_udp.parsePacket() >= static_cast<int>(sizeof(packet))) {
                        juno_tls_clock_udp.read(packet, sizeof(packet));
                        if ((packet[0] & 0x07) == 4 && packet[1] != 0) {
                          seconds = (static_cast<uint32_t>(packet[40]) << 24) | (static_cast<uint32_t>(packet[41]) << 16)
                              | (static_cast<uint32_t>(packet[42]) << 8) | static_cast<uint32_t>(packet[43]);
                        }
                        break;
                      }
                      delay(10);
                    }
                  }
                  juno_tls_clock_udp.stop();
                  if (seconds <= 2208988800ul) return false;
                  set_time(static_cast<time_t>(seconds - 2208988800ul));
                  return time(nullptr) > 1700000000;
                }

                // The first UDP exchange after Wi-Fi joins can be lost, so the time is asked for up to three times.
                static bool juno_portenta_tls_clock() {
                  for (int attempt = 0; attempt < 3; attempt++) {
                    if (time(nullptr) > 1700000000) return true;
                    if (juno_portenta_tls_clock_once()) return true;
                    delay(500);
                  }
                  return time(nullptr) > 1700000000;
                }

                // A date as one number that orders like the date itself.
                static uint64_t juno_time_key(int year, int month, int day, int hour, int minute, int second) {
                  return static_cast<uint64_t>(year) * 10000000000ull + static_cast<uint64_t>(month) * 100000000ull
                      + static_cast<uint64_t>(day) * 1000000ull + static_cast<uint64_t>(hour) * 10000ull
                      + static_cast<uint64_t>(minute) * 100ull + static_cast<uint64_t>(second);
                }

                // The Mbed TLS client behind each HTTPS client. Before every handshake it gives the socket the host name
                // (SNI) and the shared roots, in place of MbedSSLClient's own hook, which would parse the bundle again.
                struct JunoPortentaTlsClient : arduino::MbedSSLClient {
                  const char* juno_host = nullptr;

                  JunoPortentaTlsClient() {
                    onBeforeConnect(mbed::callback(this, &JunoPortentaTlsClient::juno_configure));
                  }

                  int juno_configure() {
                    TLSSocket* tls = static_cast<TLSSocket*>(getSocket());
                    if (tls == nullptr) return -1;
                    if (juno_host != nullptr) tls->set_hostname(juno_host);
                    tls->set_ca_chain(juno_portenta_ca_chain());
                    return 0;
                  }
                };

                struct JunoPortentaHttpsClient : WiFiSSLClient {
                  // Run with the runtime held, before the connect leaves it: the clock and the shared roots are set
                  // up once, by whichever connection comes first, and neither may be set up by two at a time.
                  friend bool juno_http_prepare(JunoPortentaHttpsClient&) {
                    return juno_portenta_tls_clock() && juno_portenta_ca_chain() != nullptr;
                  }

                  void newMbedClient() override {
                    client.reset(new JunoPortentaTlsClient());
                    client->setNetwork(getNetwork());
                  }

                  // Every certificate in the peer's chain must be within its validity dates. The prebuilt Mbed TLS
                  // date helpers answer "not past" for every date, so the dates are compared here, as YYYYMMDDhhmmss.
                  bool juno_certificates_current() {
                    if (!client) return false;
                    TLSSocket* tls = static_cast<TLSSocket*>(client->getSocket());
                    if (tls == nullptr) return false;
                    const mbedtls_x509_crt* chain = mbedtls_ssl_get_peer_cert(tls->get_ssl_context());
                    if (chain == nullptr) return false;
                    time_t nowSeconds = time(nullptr);
                    struct tm now;
                    gmtime_r(&nowSeconds, &now);
                    uint64_t current = juno_time_key(now.tm_year + 1900, now.tm_mon + 1, now.tm_mday, now.tm_hour,
                        now.tm_min, now.tm_sec);
                    for (const mbedtls_x509_crt* crt = chain; crt != nullptr; crt = crt->next) {
                      if (juno_time_key(crt->valid_to.year, crt->valid_to.mon, crt->valid_to.day, crt->valid_to.hour,
                              crt->valid_to.min, crt->valid_to.sec) < current) return false;
                      if (juno_time_key(crt->valid_from.year, crt->valid_from.mon, crt->valid_from.day,
                              crt->valid_from.hour, crt->valid_from.min, crt->valid_from.sec) > current) return false;
                    }
                    return true;
                  }

                  int connect(const char* host, uint16_t port) override {
                    if (!juno_portenta_tls_clock()) return 0;
                    if (juno_portenta_ca_chain() == nullptr) return 0;
                    if (!client) newMbedClient();
                    static_cast<JunoPortentaTlsClient*>(client.get())->juno_host = host;
                    if (WiFiSSLClient::connect(host, port) != 1) return 0;
                    if (juno_certificates_current()) return 1;
                    stop();
                    return 0;
                  }
                };
                """.replace("${HOST}", server.host()).replace("${PORT}", Integer.toString(server.port()));
    }

    /**
     * Four requests in flight, as on the ESP32 cores: with the trusted roots parsed once and shared, a TLS session costs
     * only its own buffers, and the Mbed heap has room for four of them; a fifth waits for a lease.
     */
    @Override
    public int maxConcurrentHttpRequests() {
        return 4;
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
     * The trusted roots as one file-scope definition shared by the HTTPS and STARTTLS clients, kept once by its guard
     * when a program uses both: every root's DER bytes in flash, and {@code juno_portenta_ca_chain()}, which builds one
     * Mbed TLS chain from them on first use. {@code mbedtls_x509_crt_parse_der_nocopy} parses each root in place, so
     * the heap holds only the parsed fields and the certificates stay in flash. A root that does not parse is skipped;
     * with none parsed the chain is missing and no connection is made.
     */
    private String trustedRootsDefinition() {
        return "#ifndef JUNO_PORTENTA_TRUST_ROOTS\n#define JUNO_PORTENTA_TRUST_ROOTS\n#include \"mbedtls/x509_crt.h\"\n"
                + "struct JunoPortentaRoot {\n  const uint8_t* der;\n  uint16_t length;\n};\n"
                + CertificateBundle.cDerTable("juno_portenta_roots_der", "JunoPortentaRoot", trustedRootsPem())
                + """
                static mbedtls_x509_crt juno_portenta_roots;
                static bool juno_portenta_roots_parsed = false;

                static mbedtls_x509_crt* juno_portenta_ca_chain() {
                  if (!juno_portenta_roots_parsed) {
                    mbedtls_x509_crt_init(&juno_portenta_roots);
                    int parsed = 0;
                    for (const JunoPortentaRoot& root : juno_portenta_roots_der) {
                      if (mbedtls_x509_crt_parse_der_nocopy(&juno_portenta_roots, root.der, root.length) == 0) parsed++;
                    }
                    if (parsed == 0) {
                      mbedtls_x509_crt_free(&juno_portenta_roots);
                      return nullptr;
                    }
                    juno_portenta_roots_parsed = true;
                  }
                  return &juno_portenta_roots;
                }
                #endif
                """;
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

                  void setCAChain(mbedtls_x509_crt* chain) {
                    if (chain == nullptr) {
                      trust = NSAPI_ERROR_PARAMETER;
                      return;
                    }
                    tls.set_ca_chain(chain);
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
        return "client.setCAChain(juno_portenta_ca_chain());";
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

    /**
     * The core's USBHID library, named through {@code PluggableUSBHID.h} first: a bare {@code <USBMouse.h>} resolves to
     * Mbed OS's own {@code drivers/usb} copy, whose mouse never joins the core's composite USB device, so the host sees
     * only the serial port and no mouse.
     */
    @Override
    public String mouseIncludes() {
        return "#include <PluggableUSBHID.h>\n#include <USBMouse.h>\n";
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

    /** Every Juno thread is an Mbed RTOS thread with a stack of its own, so a blocking call can let the others run. */
    @Override
    public boolean releasesDuringNativeCalls() {
        return true;
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
