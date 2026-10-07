package io.github.jabrena.juno.api.io.usb;

/** Test double shadowing the {@code juno} artifact's native {@code Serial}: output is discarded. */
public final class Serial {
    private Serial() {
    }

    public static void begin(BaudRate baudRate) {
    }

    public static void begin(int baudRate) {
    }

    public static void print(boolean value) {
    }

    public static void println(boolean value) {
    }

    public static void print(int value) {
    }

    public static void println(int value) {
    }

    public static void print(long value) {
    }

    public static void println(long value) {
    }

    public static void print(float value) {
    }

    public static void println(float value) {
    }

    public static void print(double value) {
    }

    public static void println(double value) {
    }

    public static void print(String message) {
    }

    public static void println(String message) {
    }
}
