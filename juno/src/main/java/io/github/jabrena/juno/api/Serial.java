package io.github.jabrena.juno.api;

/** USB serial output recognized as compiler intrinsics by Juno. */
public final class Serial {
    private Serial() {
    }

    public static native void begin(int baudRate);

    public static native void print(int value);

    public static native void println(int value);
}
