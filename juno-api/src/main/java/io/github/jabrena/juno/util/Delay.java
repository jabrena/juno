package io.github.jabrena.juno.util;

/**
 * Timing operations recognized as compiler intrinsics by Juno. On a regular JVM they delegate to
 * {@link Thread#sleep(long, int)}; the Juno compiler lowers calls directly to the board runtime.
 */
public final class Delay {
    private Delay() {
    }

    /** Delays the current thread for {@code milliseconds}. */
    public static void millis(int milliseconds) {
        if (milliseconds > 0) {
            sleep(milliseconds, 0);
        }
    }

    /** Delays the current thread for {@code microseconds}. */
    public static void micros(int microseconds) {
        if (microseconds > 0) {
            sleep(microseconds / 1_000L, microseconds % 1_000 * 1_000);
        }
    }

    /**
     * Delays {@code seconds} seconds. Not itself a compiler intrinsic — a plain wrapper around
     * {@link #millis}, the same way {@link io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield} is
     * built entirely from lower-level intrinsics.
     */
    public static void seconds(int seconds) {
        millis(seconds * 1000);
    }

    private static void sleep(long milliseconds, int nanoseconds) {
        try {
            Thread.sleep(milliseconds, nanoseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
