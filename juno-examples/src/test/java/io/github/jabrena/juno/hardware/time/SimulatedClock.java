package io.github.jabrena.juno.hardware.time;

import io.github.jabrena.juno.hardware.internal.NativeClock;

import io.github.jabrena.juno.util.Delay;

/**
 * The board's clock for whole-program tests: a {@link Clock} that {@code ArduinoUno.getClock()} returns once
 * {@link #install()} has run, with simulated time that only moves when the program waits ({@link Delay}) or reads the
 * clock. Each reading costs {@link #millisPerReading} (one millisecond unless a test changes it), so busy-waiting
 * frame loops and time-budgeted searches still make progress, and every run of a program is exactly repeatable.
 */
public final class SimulatedClock implements Clock {
    /**
     * Simulated milliseconds each reading costs. Raising it shrinks what a time-budgeted search can
     * do within its budget, roughly like running on the much slower board.
     */
    public static int millisPerReading = 1;

    private static final SimulatedClock INSTANCE = new SimulatedClock();

    private static int now;

    private SimulatedClock() {
    }

    /** Makes {@code ArduinoUno.getClock()} from now on return this simulated time instead of the board's timer. */
    public static void install() {
        NativeClock.setClock(INSTANCE);
    }

    /**
     * The current simulated time. A reading costs {@link #millisPerReading}, except from a virtual thread, whose
     * readings are free: like its waits (see {@link Delay}), they must not drive the program's single timeline.
     */
    @Override
    public int millis() {
        if (Thread.currentThread().isVirtual()) {
            return now;
        }
        now = now + millisPerReading;
        return now;
    }

    @Override
    public int micros() {
        return millis() * 1000;
    }

    /** Current simulated time, without advancing it. */
    public static int elapsed() {
        return now;
    }

    /** Moves simulated time forward; called by the {@link Delay} double. */
    public static void advance(int milliseconds) {
        now = now + milliseconds;
    }
}
