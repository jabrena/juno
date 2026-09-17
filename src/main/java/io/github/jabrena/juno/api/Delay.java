package io.github.jabrena.juno.api;

/** Timing operations recognized as compiler intrinsics by Juno. */
public final class Delay {
    private Delay() {
    }

    public static native void millis(int milliseconds);

    public static native void micros(int microseconds);
}
