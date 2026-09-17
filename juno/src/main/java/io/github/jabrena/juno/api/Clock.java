package io.github.jabrena.juno.api;

/** Monotonic clock operations recognized as compiler intrinsics by Juno. */
public final class Clock {
    private Clock() {
    }

    public static native int millis();

    public static native int micros();
}
