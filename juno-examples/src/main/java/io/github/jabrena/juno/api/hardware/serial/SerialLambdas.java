package io.github.jabrena.juno.api.hardware.serial;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Prints the results of every lambda and method-reference shape over the Arduino Nano ESP32's native USB serial port
 * at 115200 baud, once every two seconds, so the board's output can be compared line by line with a desktop JVM run of
 * the same source: non-capturing and capturing lambdas, bound, unbound, static and constructor references, a
 * two-argument lambda with two captures, and an interface call whose receiver may be a class or a lambda.
 *
 * <p>Every lambda is created once, before the loop, so the loop allocates nothing. The board's USB port
 * re-enumerates when the program starts, so it waits two seconds before the first line, giving a serial monitor time
 * to reattach.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class SerialLambdas {
    @FunctionalInterface
    interface IntOperation {
        int apply(int value);
    }

    @FunctionalInterface
    interface IntPairOperation {
        int apply(int left, int right);
    }

    @FunctionalInterface
    interface CounterFactory {
        Counter create(int start);
    }

    @FunctionalInterface
    interface CounterReader {
        int read(Counter counter);
    }

    static final class Counter {
        private final int start;

        Counter(int start) {
            this.start = start;
        }

        int value() {
            return start;
        }

        int plus(int amount) {
            return start + amount;
        }
    }

    static final class Doubler implements IntOperation {
        @Override
        public int apply(int value) {
            return value * 2;
        }
    }

    static int cube(int value) {
        return value * value * value;
    }

    /**
     * Opens the serial port, builds every lambda once, and prints their results indefinitely with a round counter.
     *
     * @param args ignored; Juno programs do not receive command-line arguments
     */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int offset = 7;
        int factor = 3;
        Counter counter = new Counter(40);

        IntOperation square = value -> value * value;
        IntOperation captured = value -> value * factor + offset;
        IntOperation bound = counter::plus;
        IntOperation staticReference = SerialLambdas::cube;
        CounterReader unbound = Counter::value;
        CounterFactory constructor = Counter::new;
        IntPairOperation pair = (left, right) -> left * factor - right + offset;
        IntOperation[] mixed = {new Doubler(), square, captured};

        int round = 0;
        while (true) {
            Serial.print("round ");
            Serial.println(round);
            Serial.print("square ");
            Serial.println(square.apply(round + 3));
            Serial.print("captured ");
            Serial.println(captured.apply(round));
            Serial.print("bound ");
            Serial.println(bound.apply(round));
            Serial.print("static ");
            Serial.println(staticReference.apply(round + 2));
            Serial.print("unbound ");
            Serial.println(unbound.read(counter));
            Serial.print("constructor ");
            Serial.println(constructor.create(round * 5).plus(1));
            Serial.print("pair ");
            Serial.println(pair.apply(round + 1, 100));
            Serial.print("mixed ");
            Serial.println(mixed[round % 3].apply(round + 4));

            Delay.millis(2000);
            round = round + 1;
        }
    }
}
