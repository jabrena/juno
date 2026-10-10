package io.github.jabrena.juno.board;

import io.github.jabrena.juno.RuntimeLimits;

/**
 * The Arduino core a {@link Board} is built with. The core, not the board, decides how generated
 * code reaches the platform runtime (which {@code delay()}/{@code yield()} symbols are callable,
 * which bundled libraries exist), so boards sharing a core share that behavior.
 */
public enum ArduinoCore {
    /** {@code arduino:renesas_uno}: the UNO R4's classic, non-RTOS core. */
    RENESAS_UNO("Renesas", RuntimeLimits.MIN_THREAD_STACK_BYTES),
    /**
     * {@code arduino:zephyr}: provides its own {@code yield()} and inlines {@code delay()}/
     * {@code delayMicroseconds()}, so generated code must reach those through shim functions.
     */
    ZEPHYR("Zephyr", 4096);

    private final String displayName;
    private final int defaultThreadStackBytes;

    ArduinoCore(String displayName, int defaultThreadStackBytes) {
        this.displayName = displayName;
        this.defaultThreadStackBytes = defaultThreadStackBytes;
    }

    /** Stack bytes of each extra task when the runtime configuration does not choose one. */
    public int defaultThreadStackBytes() {
        return defaultThreadStackBytes;
    }

    public String displayName() {
        return displayName;
    }
}
