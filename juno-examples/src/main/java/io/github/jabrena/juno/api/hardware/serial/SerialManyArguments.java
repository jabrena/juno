package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints the results of calls that pass more than six argument words over the Arduino Nano ESP32's native USB serial
 * port at 115200 baud, once every two seconds, so the board's output can be compared line by line with a desktop JVM
 * run of the same source. The Xtensa windowed ABI passes six words in registers and the rest on the stack: seven
 * {@code int}s, a {@code long} that no longer fits the registers at word five, four {@code double}s, a {@code long}
 * that exactly fills the last register pair, an instance method whose receiver takes a word, and an interface call.
 *
 * <p>Every helper calls itself once, so none is inlined and each call really passes its words. The board's USB port
 * re-enumerates when the program starts, so it waits two seconds before the first line.
 */
@Board(ArduinoNanoEsp32Board.class)
@SuppressWarnings("java:S2189")
public final class SerialManyArguments {
    /** An operation over eight words: the receiver, six {@code int}s and one more on the stack. */
    interface Weigher {
        int weigh(int a, int b, int c, int d, int e, int f, int g);
    }

    static final class Linear implements Weigher {
        private final int bias;

        Linear(int bias) {
            this.bias = bias;
        }

        @Override
        public int weigh(int a, int b, int c, int d, int e, int f, int g) {
            return bias + a + 2 * b + 3 * c + 4 * d + 5 * e + 6 * f + 7 * g;
        }
    }

    static final class Alternating implements Weigher {
        @Override
        public int weigh(int a, int b, int c, int d, int e, int f, int g) {
            return a - b + c - d + e - f + g;
        }
    }

    private final int scale;

    SerialManyArguments(int scale) {
        this.scale = scale;
    }

    static int sevenInts(int depth, int a, int b, int c, int d, int e, int f) {
        return depth > 0 ? sevenInts(depth - 1, a, b, c, d, e, f) : a + 10 * b + 100 * c + 1000 * d + 10000 * e
                + 100000 * f;
    }

    static long longAfterFiveInts(int depth, int a, int b, int c, int d, long wide, int g, int h) {
        return depth > 0 ? longAfterFiveInts(depth - 1, a, b, c, d, wide, g, h) : wide * 3 + a + b * 7 + c * 11
                + d * 13 + g * 17 + h * 19;
    }

    static double fourDoubles(int depth, double a, double b, double c, double d) {
        return depth > 0 ? fourDoubles(depth - 1, a, b, c, d) : a * 2.5 + b / 4 - c * c + d;
    }

    static long longInLastPair(int depth, int a, int b, int c, long wide, int g) {
        return depth > 0 ? longInLastPair(depth - 1, a, b, c, wide, g) : wide - a * 100L + b * 10L + c - g;
    }

    long scaled(int depth, int a, int b, int c, int d, int e, int f) {
        return depth > 0 ? scaled(depth - 1, a, b, c, d, e, f) : (long) scale * (a + b + c + d + e + f);
    }

    /**
     * Opens the serial port and prints the same block of results indefinitely, with a round counter.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        SerialManyArguments example = new SerialManyArguments(1_000_003);
        Weigher[] weighers = {new Linear(5), new Alternating()};

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);
            Serial.print("ints ");
            Serial.println(sevenInts(2, round, 1, 2, 3, 4, 5));
            Serial.print("long5 ");
            Serial.println(longAfterFiveInts(2, round, 2, 3, 4, 7_000_000_000L + round, 6, 8));
            Serial.print("doubles ");
            Serial.println((long) (fourDoubles(2, round + 0.5, 3.25, 1.5, -2.0) * 1000));
            Serial.print("long3 ");
            Serial.println(longInLastPair(2, round, 4, 5, 9_000_000_000L - round, 77));
            Serial.print("instance ");
            Serial.println(example.scaled(2, round, 1, 1, 2, 3, 5));
            Serial.print("interface ");
            Serial.println(weighers[round % 2].weigh(round, 1, 2, 3, 4, 5, 6));

            Delay.millis(2000);
            round = round + 1;
        }
    }
}
