package io.github.jabrena.juno.api;

/**
 * Test double shadowing the {@code juno} artifact's native {@code Clock}: simulated time that only
 * moves when the program waits ({@link Delay}) or reads the clock. Each reading costs
 * {@link #millisPerReading} (one millisecond unless a test changes it), so busy-waiting frame loops
 * and time-budgeted searches still make progress, and every run of a program is exactly repeatable.
 */
public final class Clock {
    /**
     * Simulated milliseconds each reading costs. Raising it shrinks what a time-budgeted search can
     * do within its budget, roughly like running on the much slower board.
     */
    public static int millisPerReading = 1;

    private static int now;

    private Clock() {
    }

    public static int millis() {
        now = now + millisPerReading;
        return now;
    }

    public static int micros() {
        return millis() * 1000;
    }

    /** Current simulated time, without advancing it. */
    public static int elapsed() {
        return now;
    }

    static void advance(int milliseconds) {
        now = now + milliseconds;
    }
}
