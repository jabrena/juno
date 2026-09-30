package io.github.jabrena.juno.backend;

/**
 * The UNO Q's {@code arduino:zephyr} core: it provides {@code yield()} itself but inlines
 * {@code delay()}/{@code delayMicroseconds()}, so they are reached through shim wrappers.
 */
record ZephyrCoreRuntime() implements CoreRuntime {

    @Override
    public String delayMillisFunction() {
        return "juno_delay";
    }

    @Override
    public String delayMicrosFunction() {
        return "juno_delay_microseconds";
    }

    /**
     * PH10, {@code LED3}'s red channel — the first entry of the UNO Q board overlay's
     * {@code builtin-led-gpios} and the pin {@code LED_BUILTIN} resolves to in the Zephyr core; D13
     * on this board's header has no LED wired to it. Position within {@code digital-pin-gpios} in
     * {@code arduino_uno_q_stm32u585xx.overlay}, hardware-confirmed 2026-09-30.
     */
    @Override
    public int builtinLedPin() {
        return 50;
    }

    @Override
    public String wifiIncludes(boolean udp) {
        return udp ? "#include <WiFi.h>\n#include <WiFiUdp.h>\n" : "#include <WiFi.h>\n";
    }

    @Override
    public String yieldFunction() {
        return """
                // The Zephyr core provides yield() itself (the generated assembly's `bl yield` reaches
                // it directly) but inlines delay() and delayMicroseconds(), so the assembly calls them
                // through these wrappers instead.
                extern "C" void juno_delay(uint32_t ms) {
                  delay(ms);
                }

                extern "C" void juno_delay_microseconds(uint32_t us) {
                  delayMicroseconds(us);
                }

                """;
    }
}
