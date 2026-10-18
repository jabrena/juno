package io.github.jabrena.juno.api.time;

/**
 * Test-only stand-in for the monotonic clock intrinsic. Time is a simulated microsecond counter that tests move
 * forward with {@link io.github.jabrena.juno.api.util.Delay} and with each pin read, so timing code runs
 * deterministically on the JVM.
 */
public final class Clock {
    /** The simulated time in microseconds since the last {@link #reset()}. */
    public static int now;

    private Clock() {
    }

    public static void reset() {
        now = 0;
    }

    public static int millis() {
        return now / 1000;
    }

    public static int micros() {
        return now;
    }
}
