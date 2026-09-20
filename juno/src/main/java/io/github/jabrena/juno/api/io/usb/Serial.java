package io.github.jabrena.juno.api.io.usb;

/** USB serial output recognized as compiler intrinsics by Juno. */
public final class Serial {
    private Serial() {
    }

    /**
     * Starts serial communication at a standard Arduino CLI monitor rate.
     *
     * @param baudRate the data rate to use
     */
    public static void begin(BaudRate baudRate) {
        begin(baudRate.bitsPerSecond());
    }

    /**
     * Starts serial communication at a custom data rate. Prefer {@link #begin(BaudRate)} when the
     * rate is represented by {@link BaudRate}.
     *
     * @param baudRate the data rate in bits per second
     */
    public static native void begin(int baudRate);

    /**
     * Prints an integer without a trailing line ending.
     *
     * @param value the integer to print
     */
    public static native void print(int value);

    /**
     * Prints an integer followed by a line ending.
     *
     * @param value the integer to print
     */
    public static native void println(int value);

    /**
     * Prints a compile-time string literal without a trailing line ending. Juno has no heap for a
     * runtime string value.
     *
     * @param message the compile-time string literal to print
     */
    public static native void print(String message);

    /**
     * Prints a compile-time string literal followed by a line ending. Juno has no heap for a runtime
     * string value.
     *
     * @param message the compile-time string literal to print
     */
    public static native void println(String message);
}
