package io.github.jabrena.juno.api;

/**
 * Test double shadowing the {@code juno} artifact's native {@code Delay}: waiting advances
 * {@link Clock}'s simulated time instantly and then runs {@link #afterAdvance}, which the TFT
 * emulator uses to script touches and to stop a program at a chosen moment.
 */
public final class Delay {
    /** Runs every time simulated time advances. */
    public static Runnable afterAdvance = () -> { };

    private static int pendingMicros;

    private Delay() {
    }

    public static void millis(int milliseconds) {
        Clock.advance(milliseconds);
        afterAdvance.run();
    }

    public static void micros(int microseconds) {
        pendingMicros = pendingMicros + microseconds;
        if (pendingMicros >= 1000) {
            Clock.advance(pendingMicros / 1000);
            pendingMicros = pendingMicros % 1000;
            afterAdvance.run();
        }
    }

    public static void seconds(int seconds) {
        millis(seconds * 1000);
    }
}
