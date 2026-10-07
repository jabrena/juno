package io.github.jabrena.juno.api.io.serial;

/**
 * JVM oracle for QemuRunIT: shadows the juno artifact's native {@code Serial} and writes to standard output, so
 * the same program source run on a JVM yields the output the compiled program must reproduce under QEMU.
 */
public final class Serial {
    private Serial() {
    }

    public static void begin(BaudRate baudRate) {
    }

    public static void begin(int baudRate) {
    }

    public static void print(boolean value) {
        System.out.print(value);
    }

    public static void println(boolean value) {
        System.out.println(value);
    }

    public static void print(int value) {
        System.out.print(value);
    }

    public static void println(int value) {
        System.out.println(value);
    }

    public static void print(long value) {
        System.out.print(value);
    }

    public static void println(long value) {
        System.out.println(value);
    }

    public static void print(String message) {
        System.out.print(message);
    }

    public static void println(String message) {
        System.out.println(message);
    }
}
