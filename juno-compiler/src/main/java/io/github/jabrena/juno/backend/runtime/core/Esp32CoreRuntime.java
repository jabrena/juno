package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import io.github.jabrena.juno.backend.runtime.ShimLibraries;
import io.github.jabrena.juno.backend.runtime.ThreadRuntime;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
import io.github.jabrena.juno.backend.runtime.spi.WatchdogRuntime;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.board.ArduinoCore;

import java.util.Map;

/**
 * The Nano ESP32's {@code arduino:esp32} core. {@code delay()}/{@code delayMicroseconds()} and {@code yield()} are
 * plain C symbols the assembly calls directly. The board builds with {@code BOARD_HAS_PIN_REMAP}, which turns
 * {@code pinMode}/{@code digitalWrite} and friends into macros translating the printed pin number ({@code D13}) to
 * the ESP32-S3 GPIO number (48); the real symbols take GPIO numbers, so the assembly reaches them through
 * {@code juno_*} wrappers compiled with the macros in effect. Threads are FreeRTOS tasks handing a baton (see
 * {@link ThreadRuntime#esp32Port}). SD cards use the shared SdFat helpers over the core's default SPI pins, and the
 * USB HID mouse is the core's {@code USBHIDMouse} rather than the {@code Mouse} library. A capability the board does
 * not declare is not wired to this core and fails at compile time.
 */
final class Esp32CoreRuntime implements CoreRuntime {
    private static final Map<String, String> PIN_REMAP_WRAPPERS = Map.of(
            "pinMode", "juno_pin_mode",
            "digitalWrite", "juno_digital_write",
            "digitalRead", "juno_digital_read",
            "analogRead", "juno_analog_read",
            "analogWrite", "juno_analog_write");

    private final RuntimeSource runtimeSource = new RuntimeSource();
    private final ShimLibraries shimLibraries = new ShimLibraries();
    private final ThreadRuntime threadRuntime = new ThreadRuntime();
    private final ArduinoCore core;

    /** @param core the ESP32 core this runtime serves: {@link ArduinoCore#ESP32} or {@link ArduinoCore#M5STACK_ESP32} */
    Esp32CoreRuntime(ArduinoCore core) {
        this.core = core;
    }

    @Override
    public String delayMillisFunction() {
        return "delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "delayMicroseconds";
    }

    @Override
    public String gpioFunction(String arduinoFunction) {
        return PIN_REMAP_WRAPPERS.getOrDefault(arduinoFunction, arduinoFunction);
    }

    /** {@code LED_BUILTIN} under the core's default "By Arduino pin" numbering. */
    @Override
    public int builtinLedPin() {
        return 13;
    }

    @Override
    public String ledMatrixType() {
        throw unsupported("an LED matrix");
    }

    @Override
    public int ledMatrixColumns() {
        throw unsupported("an LED matrix");
    }

    /** {@code WiFi.h} covers the station calls; {@code WiFiUdp.h} adds the UDP socket {@code Udp} and SNTP use. */
    @Override
    public String wifiIncludes(boolean udp) {
        return udp ? "#include <WiFi.h>\n#include <WiFiUdp.h>\n" : "#include <WiFi.h>\n";
    }

    /** Station and access-point helpers over the core's {@code WiFi} class ({@code softAP} for the access point). */
    @Override
    public String wifiHelpers() {
        return runtimeSource.load("esp32/wifi.cpp");
    }

    /** A {@code WiFiServer} on the station interface; the shared request parser reads its accepted clients. */
    @Override
    public String httpServerTransport() {
        return runtimeSource.load("esp32/http-server-transport.cpp");
    }

    /**
     * The same {@code WiFiServer}, each accepted client wrapped in an mbedTLS server session; both of the core's
     * mbedTLS generations (2.x in {@code arduino:esp32} 2.0, 3.x in the 3.x cores such as the Cardputer's) are handled.
     */
    @Override
    public String httpsServerTransport() {
        return runtimeSource.load("esp32/https-server-transport.cpp");
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

    /**
     * The TLS client checks the server's chain against Mozilla's trusted roots, bundled as the core's
     * {@code setCACertBundle} format from {@code esp32/ca-certificates.pem} (curl's extract of Mozilla's list, MPL 2.0).
     * The core's TLS library has no calendar clock, so certificate validity dates are not checked.
     */
    @Override
    public String httpsHelpers() {
        return httpsHelpers(false);
    }

    @Override
    public String httpsHelpers(boolean runtimeTrustAnchor) {
        if (runtimeTrustAnchor) {
            return """
                    struct JunoEspTlsClient : WiFiClientSecure {
                      JunoEspTlsClient() {
                        if (juno_runtime_trusted_ca != nullptr) {
                          setCACert(juno_runtime_trusted_ca);
                        }
                      }
                    };
                    """;
        }
        String customCa = System.getenv("JUNO_TLS_CA");
        String defaultTrust;
        String defaultTrustConfiguration;
        if (customCa != null && !customCa.isBlank()) {
            defaultTrust = CertificateBundle.cPem("juno_custom_ca", customCa);
            defaultTrustConfiguration = "setCACert(juno_custom_ca);";
        } else {
            defaultTrust = CertificateBundle.fromPem(runtimeSource.load("esp32/ca-certificates.pem"))
                    .cArray("juno_ca_bundle");
            // The Arduino-ESP32 2.x core takes the bundle alone; the ESP-IDF 5 core behind the M5Stack adds its size.
            defaultTrustConfiguration = this.core == ArduinoCore.M5STACK_ESP32
                    ? "setCACertBundle(juno_ca_bundle, sizeof(juno_ca_bundle));"
                    : "setCACertBundle(juno_ca_bundle);";
        }
        // The default client also checks this runtime anchor, which only programs using HttpClient.setTrustedCertificate
        // define; without them it stays null and the bundle is used.
        return defaultTrust + """
                static const char* juno_runtime_trusted_ca = nullptr;
                struct JunoEspTlsClient : WiFiClientSecure {
                  JunoEspTlsClient() {
                    if (juno_runtime_trusted_ca != nullptr) {
                      setCACert(juno_runtime_trusted_ca);
                    } else {
                      ${JUNO_DEFAULT_TRUST}
                    }
                  }
                };
                """.replace("${JUNO_DEFAULT_TRUST}", defaultTrustConfiguration);
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

    @Override
    public String httpsClientDeclaration() {
        return "JunoEspTlsClient client;";
    }

    /**
     * The STARTTLS client checks the server's chain against the same Mozilla roots as {@link #httpsHelpers}. They go in
     * as one PEM string: {@code ESP_SSLClient}'s {@code setCACert} builds a trust anchor for every certificate in it.
     * Unlike the core's TLS client, BearSSL also checks the certificate dates against {@code time()}, which the board
     * leaves near 1970, so the system clock is set once over SNTP first. Without a clock it fails closed with {@code -13}.
     */
    @Override
    public String smtpStarttlsTrust() {
        return CertificateBundle.cPem("juno_smtp_ca", runtimeSource.load("esp32/ca-certificates.pem")) + """
                  if (time(nullptr) < 1700000000) {
                    configTime(0, 0, "pool.ntp.org", "time.google.com");
                    for (int attempt = 0; attempt < 100 && time(nullptr) < 1700000000; attempt++) delay(100);
                  }
                  if (time(nullptr) < 1700000000) return -13;
                  client.setCACert(juno_smtp_ca);""";
    }

    /**
     * The ESP32's task watchdog fires from an interrupt, so the halt keeps interrupts on: the main task stays subscribed
     * and is never fed again, and the watchdog resets the board after its timeout instead of the board hanging.
     */
    @Override
    public String panicHalt() {
        return "for (;;) {}";
    }

    @Override
    public String yieldFunction() {
        return runtimeSource.load("esp32/core-glue.cpp");
    }

    @Override
    public String watchdogYieldFunction() {
        return yieldFunction() + runtimeSource.load("esp32/watchdog-yield.cpp");
    }

    @Override
    public WatchdogRuntime watchdog(int timeoutMillis) {
        int timeoutSeconds = Math.ceilDiv(timeoutMillis, 1000);
        return new WatchdogRuntime("#include <esp_task_wdt.h>\n", taskWatchdogSetup(timeoutSeconds),
                "esp_task_wdt_reset();", Math.multiplyExact(timeoutSeconds, 1000));
    }

    /**
     * Arduino-ESP32 2.x takes the timeout in seconds; the ESP-IDF 5 core behind the M5Stack takes a configuration
     * structure ({@code timeout_ms}, {@code idle_core_mask}, {@code trigger_panic}) with the idle tasks unsubscribed.
     * That core starts the task watchdog itself at boot, so {@code esp_task_wdt_init} reports
     * {@code ESP_ERR_INVALID_STATE} and leaves its own timeout in place; {@code esp_task_wdt_reconfigure} applies ours.
     */
    private String taskWatchdogSetup(int timeoutSeconds) {
        return switch (core) {
            case M5STACK_ESP32 -> "esp_task_wdt_config_t config = {" + timeoutSeconds * 1000 + "u, 0u, true};\n"
                    + "if (esp_task_wdt_init(&config) == ESP_ERR_INVALID_STATE) esp_task_wdt_reconfigure(&config);\n"
                    + "esp_task_wdt_add(nullptr);";
            default -> "esp_task_wdt_init(" + timeoutSeconds + "u, true);\nesp_task_wdt_add(nullptr);";
        };
    }

    @Override
    public String parallelBusHelpers() {
        throw unsupported("ParallelBus");
    }

    @Override
    public String sdHelpers(int openFiles, int entries, int keyBytes, int valueBytes, int lineBytes) {
        return shimLibraries.sdHelpers(openFiles, entries, keyBytes, valueBytes, lineBytes,
                "", "juno_sd.begin(juno_nano_pin(chipSelectPin))");
    }

    @Override
    public String flashHelpers() {
        throw unsupported("flash usage queries");
    }

    @Override
    public String uuidRandomHelpers() {
        return runtimeSource.load("esp32/uuid-random.cpp");
    }

    /** The core's BLE library, which the adapter below drives through the names of {@link #bleNames()}. */
    @Override
    public String bleIncludes() {
        return """
                #include <BLEAdvertisedDevice.h>
                #include <BLEClient.h>
                #include <BLEDevice.h>
                #include <BLERemoteCharacteristic.h>
                #include <BLERemoteService.h>
                #include <BLEScan.h>
                """;
    }

    @Override
    public String bleHelpers() {
        return runtimeSource.load("esp32/ble.cpp");
    }

    @Override
    public BleNames bleNames() {
        return new BleNames("JunoBLEDevice", "JunoBLECharacteristic", "juno_ble");
    }

    /** The core's TinyUSB HID mouse, which the adapter below names {@code Mouse}. */
    @Override
    public String mouseIncludes() {
        return "#include <USB.h>\n#include <USBHIDMouse.h>\n";
    }

    @Override
    public String mouseHelpers() {
        return runtimeSource.load("esp32/mouse.cpp");
    }

    @Override
    public String threadIncludes() {
        return "#include <freertos/FreeRTOS.h>\n#include <freertos/semphr.h>\n#include <freertos/task.h>\n";
    }

    /** FreeRTOS tasks carry the stacks; the backend preempts at safepoints rather than from a tick. */
    @Override
    public String threadPort(RuntimeConfig config) {
        return threadRuntime.esp32Port(config.maxThreads(),
                config.threadStackBytes(core.defaultThreadStackBytes()));
    }

    @Override
    public boolean preemptsAsynchronously() {
        return false;
    }

    private CompileException unsupported(String feature) {
        return Architecture.XTENSA.unsupported(feature + " on the ESP32 core");
    }
}
