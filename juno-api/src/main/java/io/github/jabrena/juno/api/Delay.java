package io.github.jabrena.juno.api;

/** Timing operations recognized as compiler intrinsics by Juno. */
public final class Delay {
    private Delay() {
    }

    public static native void millis(int milliseconds);

    public static native void micros(int microseconds);

    /**
     * Delays {@code seconds} seconds. Not itself a compiler intrinsic — a plain wrapper around
     * {@link #millis}, the same way {@link io.github.jabrena.juno.api.lcd.LcdKeypadShield} is
     * built entirely from lower-level intrinsics.
     */
    public static void seconds(int seconds) {
        millis(seconds * 1000);
    }
}
