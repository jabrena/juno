package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * What inlining and scalar replacement must keep intact, checked against OpenJDK: small helpers with branches and
 * loops, constructors and accessors, objects built and dropped in a loop, a field read before it is written (Java
 * zeroes it), objects mutated through inlined methods, float fields, and objects that escape into a field, an array
 * or another object and so must stay on the heap.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class ObjectsAndCalls {
    private static final int[] INPUTS = {0, 1, -1, 7, 250, -4096, 65535};

    record Point(int x, int y) {
        Point {
            if (x < 0) {
                x = -x;
            }
        }

        int manhattan() {
            return x + (y < 0 ? -y : y);
        }
    }

    static final class Counter {
        int count;
        int total;

        void add(int value) {
            count = count + 1;
            total = total + value;
        }
    }

    static final class Scale {
        float factor;

        Scale(float factor) {
            this.factor = factor;
        }
    }

    static final class Box {
        Point point;
    }

    static Point kept;
    static Point[] shelf = new Point[2];

    static int clamp(int value, int low, int high) {
        if (value < low) {
            return low;
        }
        return value > high ? high : value;
    }

    static int sumTo(int limit) {
        int total = 0;
        for (int i = 0; i < limit; i++) {
            total = total + i;
        }
        return total;
    }

    static int unboxed(int seed) {
        int total = 0;
        for (int i = 0; i < 5; i++) {
            Point point = new Point(seed - i, i * 3 - seed);
            total = total * 7 + point.manhattan();
        }
        return total;
    }

    static int defaults(int seed) {
        Counter counter = new Counter();
        int before = counter.total;
        counter.add(seed);
        counter.add(seed * 2);
        return before * 1000 + counter.count * 100 + counter.total;
    }

    static int escapes(int seed) {
        Point point = new Point(seed, seed + 1);
        kept = point;
        shelf[seed & 1] = new Point(seed * 2, 3);
        Box box = new Box();
        box.point = new Point(seed - 5, 9);
        return kept.x() + shelf[seed & 1].manhattan() + box.point.manhattan();
    }

    static int scaled(int seed) {
        Scale scale = new Scale(1.5f);
        return (int) (seed * scale.factor);
    }

    public static void main(String[] args) {
        for (int i = 0; i < INPUTS.length; i++) {
            int x = INPUTS[i];
            Serial.println(clamp(x, -10, 300));
            Serial.println(sumTo(x & 15));
            Serial.println(unboxed(x));
            Serial.println(defaults(x));
            Serial.println(escapes(x));
            Serial.println(scaled(x));
        }
    }
}
