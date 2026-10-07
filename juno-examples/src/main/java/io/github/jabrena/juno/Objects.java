package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Demonstrates final classes with constructors and instance fields, records, enums, and arena allocation. */
public class Objects {

    enum Direction {
        NORTH, EAST, SOUTH, WEST
    }

    record Point(int x, int y) {
        Point moved(Direction direction, int steps) {
            return switch (direction) {
                case NORTH -> new Point(x, y + steps);
                case EAST -> new Point(x + steps, y);
                case SOUTH -> new Point(x, y - steps);
                case WEST -> new Point(x - steps, y);
            };
        }

        int distanceFromOrigin() {
            return Math.abs(x) + Math.abs(y);
        }
    }

    static final class Counter {
        private final int step;
        private int value;

        Counter(int start, int step) {
            this.value = start;
            this.step = step;
        }

        void increment() {
            value += step;
        }

        int value() {
            return value;
        }
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Objects demo");

        Counter counter = new Counter(10, 5);
        counter.increment();
        counter.increment();
        Serial.println("counter: " + counter.value());

        Point position = new Point(0, 0);
        position = position.moved(Direction.NORTH, 4);
        position = position.moved(Direction.EAST, 3);
        position = position.moved(Direction.SOUTH, 6);
        Serial.println("x: " + position.x());
        Serial.println("y: " + position.y());
        Serial.println("distance: " + position.distanceFromOrigin());

        // Every Point is a fresh arena object; the conservative collector reclaims the unreachable ones,
        // so the total allocated here is far beyond the 8 KiB arena.
        Point walker = new Point(0, 0);
        for (int index = 0; index < 2000; index++) {
            walker = walker.moved(Direction.EAST, 1);
        }
        Serial.println("walker x: " + walker.x());
    }
}
