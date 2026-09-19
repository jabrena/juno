package io.github.jabrena.juno.api;

/** USB serial output recognized as compiler intrinsics by Juno. */
public final class Serial {
    private Serial() {
    }

    public static native void begin(int baudRate);

    public static native void print(int value);

    public static native void println(int value);

    /** {@code message} must be a compile-time string literal; Juno has no heap for a runtime string value. */
    public static native void print(String message);

    /** {@code message} must be a compile-time string literal; Juno has no heap for a runtime string value. */
    public static native void println(String message);
}
