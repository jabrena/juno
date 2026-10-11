package io.github.jabrena.juno.board;

import io.github.jabrena.juno.RuntimeLimits;

/**
 * The Arduino core a {@link Board} is built with. The core, not the board, decides how generated
 * code reaches the platform runtime (which {@code delay()}/{@code yield()} symbols are callable,
 * which bundled libraries exist), so boards sharing a core share that behavior.
 */
public enum ArduinoCore {
    /** {@code arduino:renesas_uno}: the UNO R4's classic, non-RTOS core. */
    RENESAS_UNO("Renesas", Architecture.THUMB2, RuntimeLimits.MIN_THREAD_STACK_BYTES),
    /**
     * {@code arduino:zephyr}: provides its own {@code yield()} and inlines {@code delay()}/
     * {@code delayMicroseconds()}, so generated code must reach those through shim functions.
     */
    ZEPHYR("Zephyr", Architecture.THUMB2, 4096),
    /**
     * {@code arduino:megaavr}: the UNO WiFi Rev2's ATmega4809 core. It has no RTOS and no thread runtime, so its
     * thread stack size is never used.
     */
    MEGAAVR("megaAVR", Architecture.AVR, 0);

    private final String displayName;
    private final Architecture architecture;
    private final int defaultThreadStackBytes;

    ArduinoCore(String displayName, Architecture architecture, int defaultThreadStackBytes) {
        this.displayName = displayName;
        this.architecture = architecture;
        this.defaultThreadStackBytes = defaultThreadStackBytes;
    }

    /** The instruction set this core's boards run, which selects the code generator. */
    public Architecture architecture() {
        return architecture;
    }

    /** Stack bytes of each extra task when the runtime configuration does not choose one. */
    public int defaultThreadStackBytes() {
        return defaultThreadStackBytes;
    }

    public String displayName() {
        return displayName;
    }
}
