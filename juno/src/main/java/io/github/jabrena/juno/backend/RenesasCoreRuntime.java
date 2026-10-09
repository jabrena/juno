package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.RuntimeConfig;
import io.github.jabrena.juno.board.ArduinoCore;

/**
 * The UNO R4's {@code arduino:renesas_uno} core: {@code delay()}/{@code delayMicroseconds()} are real
 * symbols the assembly calls directly, and the shim overrides the core's weak {@code yield()} to keep
 * USB serviced.
 */
record RenesasCoreRuntime() implements CoreRuntime {

    @Override
    public String delayMillisFunction() {
        return "delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "delayMicroseconds";
    }

    @Override
    public int builtinLedPin() {
        return 13;
    }

    /** {@code Arduino_LED_Matrix.h} of the renesas_uno core declares {@code ArduinoLEDMatrix}, 12x8. */
    @Override
    public String ledMatrixType() {
        return "ArduinoLEDMatrix";
    }

    @Override
    public int ledMatrixColumns() {
        return 12;
    }

    @Override
    public String wifiIncludes(boolean udp) {
        // WiFiS3.h exposes WiFi, WiFiClient, WiFiServer, WiFiSSLClient and WiFiUDP.
        return "#include <WiFiS3.h>\n";
    }

    @Override
    public String wifiHelpers() {
        return ShimLibraries.wifiHelpers();
    }

    @Override
    public String httpServerTransport() {
        return """

                using JunoHttpClient = WiFiClient;
                alignas(WiFiServer) static unsigned char juno_http_server_storage[sizeof(WiFiServer)];
                static WiFiServer* juno_http_server_instance = nullptr;
                static JunoHttpClient juno_http_server_client;

                extern "C" void juno_http_server_begin(int32_t port) {
                  juno_http_server_instance = new (juno_http_server_storage) WiFiServer(static_cast<uint16_t>(port));
                  juno_http_server_instance->begin();
                }

                static bool juno_http_server_next_client() {
                  if (juno_http_server_instance == nullptr) return false;
                  WiFiClient client = juno_http_server_instance->available();
                  if (!client) return false;
                  juno_http_server_client = client;
                  return true;
                }

                static void juno_http_server_release() {
                }
                """;
    }

    @Override
    public String udpDeclaration() {
        return "static WiFiUDP juno_udp;";
    }

    @Override
    public String httpsInclude() {
        return "#include <WiFiSSLClient.h>\n";
    }

    @Override
    public String httpsHelpers() {
        return "";
    }

    @Override
    public String httpsClientDeclaration() {
        return "WiFiSSLClient client;";
    }

    @Override
    public String yieldFunction() {
        return """
                // Overrides the core's weak yield(): Serial's bool conversion is UNO R4's supported hook
                // into TinyUSB's tud_task(), so this keeps USB serviced from every yield() call site
                // (delay() and, per the generated assembly, every loop backedge — see
                // Thumb2AsmBackend's own `bl yield` emission) — not just programs that call Serial
                // directly. The core's first successful Serial bool conversion itself calls delay(10),
                // which calls yield() again before that first conversion is marked complete. Guard that
                // one nested call or USB connection timing turns the first yield into unbounded recursion
                // and eventual stack exhaustion. Declared extern "C" so it resolves under the plain
                // "yield" symbol the generated assembly's `bl yield` branches to directly.
                static bool juno_yield_active = false;

                extern "C" void yield() {
                  ${JUNO_WATCHDOG_REFRESH}
                #ifndef NO_USB
                  if (juno_yield_active) return;
                  juno_yield_active = true;
                  static_cast<void>(static_cast<bool>(Serial));
                  juno_yield_active = false;
                #endif
                }

                """;
    }

    @Override
    public String threadIncludes() {
        return "";
    }

    @Override
    public String threadPort(RuntimeConfig config) {
        return ThreadRuntime.renesasPort(config.maxThreads(),
                config.threadStackBytes(ArduinoCore.RENESAS_UNO.defaultThreadStackBytes()));
    }
}
