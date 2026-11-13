package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.time.Clock;

/**
 * The board's {@link Clock}: each method is a clock intrinsic the compiler lowers to the Arduino core call, so it has
 * no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link Clock} instead.
 */
public final class NativeClock implements Clock {
    /** The board's timer: the only instance. */
    public static final NativeClock INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.MCU#getClock()} returns: the board's timer, unless a
     * whole-program emulator in a test replaced it.
     */
    private static Clock clock;

    static {
        INSTANCE = new NativeClock();
        clock = INSTANCE;
    }

    private NativeClock() {
    }

    /** The clock {@link io.github.jabrena.juno.hardware.MCU#getClock()} hands out. */
    public static Clock clock() {
        return clock;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.MCU#getClock()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setClock(Clock replacement) {
        clock = replacement;
    }

    @Override
    public native int millis();

    @Override
    public native int micros();
}
