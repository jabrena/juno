package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.board.ArduinoCore;

/**
 * Everything the generated code needs to know about the {@link ArduinoCore} it links against: the
 * symbols {@code Delay} lowers to and the runtime shim's {@code yield()}/delay glue. The Thumb-2
 * code generator itself is identical for every core; each board-to-core difference lives in exactly
 * one implementation here instead of being branched on across the backend.
 */
sealed interface CoreRuntime permits RenesasCoreRuntime, ZephyrCoreRuntime {

    static CoreRuntime of(ArduinoCore core) {
        return switch (core) {
            case RENESAS_UNO -> new RenesasCoreRuntime();
            case ZEPHYR -> new ZephyrCoreRuntime();
        };
    }

    /** The symbol {@code Delay.millis} branches to, taking the delay in r0. */
    String delayMillisFunction();

    /** The symbol {@code Delay.micros} branches to, taking the delay in r0. */
    String delayMicrosFunction();

    /**
     * Core-provided headers for station-mode Wi-Fi and, when requested, UDP. The classes exposed by
     * these headers share Arduino's {@code WiFi}/{@code WiFiUDP} API even though the library names
     * differ between cores.
     */
    String wifiIncludes(boolean udp);

    /**
     * The shim's {@code yield()} and delay glue, emitted ahead of {@code juno_panic}. It must provide
     * (or leave to the core) the plain {@code yield} symbol every generated loop backedge calls, and
     * define {@link #delayMillisFunction()}/{@link #delayMicrosFunction()} when those aren't core
     * symbols. May contain the {@code ${JUNO_WATCHDOG_REFRESH}} placeholder, which {@link RuntimeShim}
     * substitutes afterwards.
     */
    String yieldFunction();
}
