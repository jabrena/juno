package io.github.jabrena.juno.board;

import io.github.jabrena.juno.RuntimeLimits;

/**
 * The Arduino core a {@link Board} is built with. The core, not the board, decides how generated
 * code reaches the platform runtime (which {@code delay()}/{@code yield()} symbols are callable,
 * which bundled libraries exist), so boards sharing a core share that behavior.
 */
public enum ArduinoCore {
    /** {@code arduino:avr}: the classic UNO R3's ATmega328P core, with 2 KB of RAM. */
    CLASSIC_AVR("AVR", Architecture.AVR, RuntimeLimits.AVR_THREAD_STACK_BYTES,
            RuntimeLimits.AVR_ARENA_CAPACITY_BYTES, RuntimeLimits.AVR_ARENA_CAPACITY_BYTES,
            RuntimeLimits.AVR_THREAD_STACK_BYTES),
    /** {@code arduino:renesas_uno}: the UNO R4's classic, non-RTOS core. */
    RENESAS_UNO("Renesas", Architecture.THUMB2, RuntimeLimits.MIN_THREAD_STACK_BYTES, 0, 0, 0),
    /**
     * {@code arduino:zephyr}: provides its own {@code yield()} and inlines {@code delay()}/
     * {@code delayMicroseconds()}, so generated code must reach those through shim functions.
     */
    ZEPHYR("Zephyr", Architecture.THUMB2, 4096, 0, 0, 0),
    /** {@code arduino:megaavr}: the UNO WiFi Rev2's ATmega4809 core, with no RTOS and 6 KB of RAM. */
    MEGAAVR("megaAVR", Architecture.AVR, RuntimeLimits.AVR_THREAD_STACK_BYTES,
            RuntimeLimits.AVR_ARENA_CAPACITY_BYTES, RuntimeLimits.AVR_MAX_ARENA_BYTES,
            RuntimeLimits.AVR_MAX_THREAD_STACK_BYTES);

    private final String displayName;
    private final Architecture architecture;
    private final int defaultThreadStackBytes;
    private final int defaultArenaBytes;
    private final int maxArenaBytes;
    private final int maxThreadStackBytes;

    ArduinoCore(String displayName, Architecture architecture, int defaultThreadStackBytes, int defaultArenaBytes,
                int maxArenaBytes, int maxThreadStackBytes) {
        this.displayName = displayName;
        this.architecture = architecture;
        this.defaultThreadStackBytes = defaultThreadStackBytes;
        this.defaultArenaBytes = defaultArenaBytes;
        this.maxArenaBytes = maxArenaBytes;
        this.maxThreadStackBytes = maxThreadStackBytes;
    }

    /** The instruction set this core's boards run, which selects the code generator. */
    public Architecture architecture() {
        return architecture;
    }

    /** Stack bytes of each extra task when the runtime configuration does not choose one. */
    public int defaultThreadStackBytes() {
        return defaultThreadStackBytes;
    }

    /** Default arena bytes for this AVR core; meaningful only when {@link #architecture()} is AVR. */
    public int defaultArenaBytes() {
        return defaultArenaBytes;
    }

    /** Largest arena accepted by this AVR core; meaningful only when {@link #architecture()} is AVR. */
    public int maxArenaBytes() {
        return maxArenaBytes;
    }

    /** Largest task stack accepted by this AVR core; meaningful only when {@link #architecture()} is AVR. */
    public int maxThreadStackBytes() {
        return maxThreadStackBytes;
    }

    public String displayName() {
        return displayName;
    }
}
