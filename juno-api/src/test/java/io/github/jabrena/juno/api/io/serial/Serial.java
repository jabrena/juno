package io.github.jabrena.juno.api.io.serial;

import java.util.ArrayList;
import java.util.List;

/**
 * Test-only stand-in for the USB serial intrinsics. Output is recorded in {@link #output} as the text the monitor would
 * show, and {@link #baudRate} keeps the last rate the program opened the port at.
 */
public final class Serial {
    /** Every printed piece of text, in order; a {@code println} adds its line ending to the piece. */
    public static final List<String> output = new ArrayList<>();
    public static int baudRate;

    private Serial() {
    }

    public static void reset() {
        output.clear();
        baudRate = 0;
    }

    public static void begin(BaudRate baudRate) {
        begin(baudRate.bitsPerSecond());
    }

    public static void begin(int baudRate) {
        Serial.baudRate = baudRate;
    }

    public static void print(boolean value) {
        if (value) {
            print("true");
        } else {
            print("false");
        }
    }

    public static void println(boolean value) {
        if (value) {
            println("true");
        } else {
            println("false");
        }
    }

    public static void print(int value) {
        output.add(String.valueOf(value));
    }

    public static void println(int value) {
        output.add(value + "\n");
    }

    public static void print(long value) {
        output.add(String.valueOf(value));
    }

    public static void println(long value) {
        output.add(value + "\n");
    }

    public static void print(float value) {
        output.add(String.valueOf(value));
    }

    public static void println(float value) {
        output.add(value + "\n");
    }

    public static void print(double value) {
        output.add(String.valueOf(value));
    }

    public static void println(double value) {
        output.add(value + "\n");
    }

    public static void print(String message) {
        output.add(message);
    }

    public static void println(String message) {
        output.add(message + "\n");
    }
}
