package io.github.jabrena.juno.backend;

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
}
