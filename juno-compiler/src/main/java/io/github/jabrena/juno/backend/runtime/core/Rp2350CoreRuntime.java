package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.SntpServer;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.ArduinoCore;

/**
 * The Raspberry Pi Pico 2 W's {@code rp2040:rp2040} core (Earle Philhower's arduino-pico) on its RP2350 Cortex-M33:
 * {@code delay()}/{@code delayMicroseconds()} are real {@code extern "C"} symbols the assembly calls directly, and the
 * core's own weak {@code yield()} serves the generated loop backedges. GPIO, delays, serial and the CYW43439's Wi-Fi
 * (through the core's {@code WiFi} library, whose {@code WiFiClass} mirrors WiFiS3's station and access-point calls)
 * are wired; optional facilities reuse the core's BearSSL, watchdog, BLE, USB HID and the shared Thumb-2 thread port.
 */
final class Rp2350CoreRuntime implements CoreRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();

    @Override
    public int carrierCount() {
        return ArduinoCore.RP2350.carrierCount();
    }

    /** Two carriers run on arduino-pico's FreeRTOS SMP, a board menu option. */
    @Override
    public String fqbnOptions(int carrierCount) {
        return carrierCount > 1 ? ",os=freertos" : "";
    }

    /** arduino-pico's FreeRTOS CORE0 task has a 4 KiB stack, too small for main: main gets a task of its own. */
    @Override
    public String mainLauncher(int carrierCount) {
        return carrierCount > 1 ? "juno_launch_main" : "";
    }

    @Override
    public String runtimeLock() {
        return """
                // Recursive lock shared by both Cortex-M33 carriers.
                static volatile uint32_t juno_runtime_owner = 0u;
                static volatile uint32_t juno_runtime_depth = 0u;

                extern "C" bool juno_runtime_owned() {
                  return juno_runtime_owner == get_core_num() + 1u;
                }

                extern "C" void juno_runtime_enter() {
                  uint32_t owner = get_core_num() + 1u;
                  for (;;) {
                    if (juno_runtime_owner == owner) {
                      juno_runtime_depth++;
                      return;
                    }
                    uint32_t expected = 0u;
                    if (__atomic_compare_exchange_n(&juno_runtime_owner, &expected, owner, false, __ATOMIC_ACQUIRE,
                                                    __ATOMIC_RELAXED)) {
                      juno_runtime_depth = 1u;
                      return;
                    }
                    juno_gc_lock_wait();
                    yield();
                  }
                }

                extern "C" void juno_runtime_exit() {
                  if (juno_runtime_owner == get_core_num() + 1u && juno_runtime_depth != 0u
                      && --juno_runtime_depth == 0u) {
                    __atomic_store_n(&juno_runtime_owner, 0u, __ATOMIC_RELEASE);
                  }
                }
                """;
    }

    @Override
    public String memoryBarrierInstruction() {
        return "dmb ish";
    }

    @Override
    public String wideAccessHelpers() {
        return """
                extern "C" uint64_t juno_wide_load(const uint32_t* address) {
                  juno_runtime_enter();
                  uint64_t value = static_cast<uint64_t>(address[0]) | (static_cast<uint64_t>(address[1]) << 32);
                  juno_runtime_exit();
                  return value;
                }

                extern "C" void juno_wide_store(uint32_t* address, uint32_t low, uint32_t high) {
                  juno_runtime_enter();
                  address[0] = low;
                  address[1] = high;
                  juno_runtime_exit();
                }
                """;
    }

    @Override
    public String delayMillisFunction() {
        return "delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "delayMicroseconds";
    }

    /**
     * {@code PIN_LED} of the core's {@code rpipico2w} variant: the LED hangs off the CYW43439's {@code WL_GPIO0}, not an
     * RP2350 GPIO, and the core's CYW43 layer overrides the weak {@code pinMode}/{@code digitalWrite} to route pins 64
     * and above to the radio chip.
     */
    @Override
    public int builtinLedPin() {
        return 64;
    }

    /** {@code A0..A2} are the Pico's three header ADC inputs, GP26..GP28; it has no {@code A3..A5}. */
    @Override
    public String gpioAnalogPins() {
        return "26, 27, 28, -1, -1, -1";
    }

    /**
     * The {@code GPn}/{@code GPIOn} names of the header pins. GP23..GP25 and GP29 are not broken out on the Pico 2 W:
     * the CYW43439 radio uses them.
     */
    @Override
    public String gpioAdditionalDigitalPins() {
        return "0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 26, 27, 28, -1";
    }

    @Override
    public String gpioAdditionalAnalogPins() {
        return "26, 27, 28, -1";
    }

    /**
     * The core's {@code Wire} defaults to GP4/GP5, which no Grove socket reaches; Juno's I2C bus is I2C0 on GP8 (SDA)
     * and GP9 (SCL) instead, the pins of the Grove Shield for Pi Pico's I2C0 socket.
     */
    @Override
    public String i2cPinSetup() {
        return "Wire.setSDA(8);\n  Wire.setSCL(9);\n  ";
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    /** The core's {@code WiFi.h} also declares {@code WiFiClient}, {@code WiFiServer} and {@code WiFiUDP}. */
    @Override
    public String wifiIncludes(boolean udp) {
        return "#include <WiFi.h>\n";
    }

    /** The shared station and access-point helpers: the core's {@code WiFi} has WiFiS3's {@code begin}/{@code beginAP}. */
    @Override
    public String wifiHelpers() {
        return shimLibraries.wifiHelpers();
    }

    @Override
    public String httpServerTransport() {
        return runtimeSource.load("renesas/http-server-transport.cpp");
    }

    @Override
    public String httpsServerTransport() {
        return runtimeSource.load("rp2350/https-server-transport.cpp");
    }

    @Override
    public String udpDeclaration() {
        return "static WiFiUDP juno_udp;";
    }

    @Override
    public String tcpClientHelpers() {
        return "using JunoTcpClient = WiFiClient;\n";
    }

    @Override
    public String httpsInclude() {
        return "#include <WiFiClientSecure.h>\n";
    }

    @Override
    public String httpsHelpers() {
        return httpsHelpers(false);
    }

    @Override
    public String httpsClientDeclaration() {
        return "JunoPicoTlsClient client;";
    }

    @Override
    public String httpsHelpers(boolean runtimeTrustAnchor) {
        String trust = runtimeTrustAnchor
                ? ""
                : CertificateBundle.cPem("juno_pico_ca", runtimeSource.load("esp32/ca-certificates.pem"));
        // BearSSL takes parsed trust anchors, not PEM text: the bundle is parsed once, on the first HTTPS client.
        String defaultTrust = runtimeTrustAnchor ? "" : """
                static BearSSL::X509List* juno_pico_anchors = nullptr;
                      if (juno_pico_anchors == nullptr) juno_pico_anchors = new BearSSL::X509List(juno_pico_ca);
                      setTrustAnchors(juno_pico_anchors);""";
        String runtimeTrust = runtimeTrustAnchor ? "" : "static const char* juno_runtime_trusted_ca = nullptr;\n";
        return trust + runtimeTrust + """
                // BearSSL checks a certificate's validity dates against the board's clock, which starts in 1970: the
                // first HTTPS connection sets it over NTP (JUNO_SNTP_HOST) when nothing has set it yet.
                static void juno_pico_tls_clock() {
                  if (time(nullptr) > 1700000000) return;
                  NTP.begin(${JUNO_SNTP_HOST});
                  NTP.waitSet(10000);
                }

                struct JunoPicoTlsClient : BearSSL::WiFiClientSecure {
                  JunoPicoTlsClient() {
                    if (juno_runtime_trusted_ca != nullptr) {
                      setTrustAnchors(new BearSSL::X509List(juno_runtime_trusted_ca));
                    } else {
                      ${JUNO_DEFAULT_TRUST}
                    }
                  }

                  int connect(const char* host, uint16_t port) override {
                    juno_pico_tls_clock();
                    return BearSSL::WiFiClientSecure::connect(host, port);
                  }
                };
                """.replace("${JUNO_DEFAULT_TRUST}", defaultTrust)
                .replace("${JUNO_SNTP_HOST}", SntpServer.from(System.getenv()).cppHost());
    }

    @Override
    public String httpsTrustHelpers() {
        return """
                static const char* juno_runtime_trusted_ca = nullptr;

                extern "C" int32_t juno_http_set_trusted_certificate(const uint8_t* certificate, int32_t length) {
                  if (certificate == nullptr || length <= 0) return 0;
                  juno_runtime_trusted_ca = reinterpret_cast<const char*>(certificate);
                  return 1;
                }
                """;
    }

    /** The core already defines a weak {@code yield()}; the shim leaves it in place. */
    @Override
    public String yieldFunction() {
        return "// The arduino-pico core provides yield(); the generated assembly's `bl yield` reaches it directly.\n";
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        return new WatchdogRuntime("#include <hardware/watchdog.h>\n", "watchdog_enable(" + timeoutMillis + "u, true);",
                "watchdog_update();", timeoutMillis);
    }

    @Override
    public String parallelBusHelpers() {
        throw unsupported("ParallelBus");
    }

    /** SdFat on the core's default hardware SPI bus. */
    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes);
    }

    @Override
    public String flashHelpers() {
        throw unsupported("BoardMemory flash figures");
    }

    @Override
    public String uuidRandomHelpers() {
        throw unsupported("UUID.randomUUID()");
    }

    @Override
    public String threadIncludes() {
        return "";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return threadRuntime.renesasPort(
                config.threadStackBytes(ArduinoCore.RP2350.defaultThreadStackBytes()));
    }

    @Override
    public String threadPort(RuntimeConfig config, int carrierCount) {
        return threadRuntime.rp2350Port(
                config.threadStackBytes(ArduinoCore.RP2350.defaultThreadStackBytes()), carrierCount);
    }

    @Override
    public String bleIncludes() {
        return "#include <BLE.h>\n";
    }

    @Override
    public String bleHelpers() {
        return runtimeSource.load("rp2350/ble.cpp");
    }

    @Override
    public BleNames bleNames() {
        return new BleNames("JunoPicoBleDevice", "JunoPicoBleCharacteristic", "juno_pico_ble");
    }

    @Override
    public String mouseIncludes() {
        return "#include <Mouse.h>\n";
    }

    private CompileException unsupported(String feature) {
        return Architecture.THUMB2.unsupported(feature + " on the Raspberry Pi Pico 2 W");
    }
}
