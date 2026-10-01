package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.Serial;

/** long and double parameters, results and fields: across calls (registers and stack), static and instance. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class WideCalls {
    static long factorial(int n) {
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    static long mix(long a, long b) {
        return (a * 31 + b) ^ (a >>> 7);
    }

    static double average(double a, double b) {
        return (a + b) / 2.0;
    }

    static long mixed(int a, long b, int c, long d, int e) {
        return a + b * c - d + e;
    }

    static double blend(int a, double b, float c, double d) {
        return a + b * c - d;
    }

    static long chain(long value, int depth) {
        return depth == 0 ? value : chain(value * 3 + depth, depth - 1);
    }

    static long grandTotal = 5_000_000_000L;
    static double scale = 2.5;

    static final class Counter {
        int tag = 7;
        long total;
        double ratio = 0.5;
        int after = 9;

        long add(long amount, int times) {
            for (int i = 0; i < times; i++) {
                total += amount;
            }
            return total;
        }
    }

    public static void main(String[] args) {
        Serial.println(factorial(20));
        Serial.println(mix(987654321987L, 12345L));
        Serial.println((int) (average(3.0, 8.0) * 100));
        Serial.println(mixed(7, 5_000_000_000L, 3, 123_456_789_012L, -9));
        Serial.println((long) (blend(2, 1.5e9, 2.0f, 0.25) / 1000));
        Serial.println(chain(1L << 40, 10));
        Counter counter = new Counter();
        counter.add(4_000_000_000L, 3);
        Serial.println(counter.add(-1L, 2));
        Serial.println(counter.tag + counter.after);
        counter.ratio = counter.ratio * 6e9;
        Serial.println((long) counter.ratio);
        grandTotal = grandTotal * 3 + counter.total;
        scale = scale * 4e9;
        Serial.println(grandTotal);
        Serial.println((long) scale);
    }
}
