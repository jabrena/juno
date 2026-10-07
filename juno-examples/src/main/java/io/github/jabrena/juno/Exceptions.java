package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Demonstrates throwing and catching exceptions: {@code try}/{@code catch}/{@code finally}, multi-catch,
 * catching by a supertype, dividing by zero, nested handlers, a custom exception class with its own field,
 * exceptions that propagate across method calls, and try-with-resources.
 *
 * <p>A {@code throw} is caught by the nearest matching handler, in the same method or in any caller, and
 * {@code finally} blocks run while it unwinds. An integer division by zero inside a {@code try} raises
 * {@code ArithmeticException("/ by zero")}. An exception that no handler catches prints
 * {@code Exception in thread "main" <class>: <message>} and halts the board.
 */
public class Exceptions {

    static final class SensorException extends RuntimeException {
        private final int sensor;

        SensorException(String message, int sensor) {
            super(message);
            this.sensor = sensor;
        }

        int sensor() {
            return sensor;
        }
    }

    /** A resource whose {@code close()} is observable on the serial monitor. */
    static final class Channel implements AutoCloseable {
        private final int id;

        Channel(int id) {
            this.id = id;
        }

        @Override
        public void close() {
            Serial.println("close " + id);
        }
    }

    static int parse(int reading) {
        if (reading < 0) {
            throw new IllegalArgumentException("negative reading");
        }
        return reading;
    }

    static int sample(int reading) {
        return parse(reading) + 1;
    }

    static void withFinally(int reading) {
        try {
            sample(reading);
        } finally {
            Serial.println("cleanup");
        }
    }

    static void withResource(int id, int reading) {
        try (Channel channel = new Channel(id)) {
            sample(reading);
        }
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Exceptions demo");

        Serial.println("Validating readings");
        int[] readings = new int[3];
        readings[0] = 12;
        readings[1] = -3;
        readings[2] = 250;
        for (int index = 0; index < readings.length; index++) {
            int reading = readings[index];
            try {
                if (reading < 0) {
                    throw new IllegalArgumentException("negative reading");
                }
                if (reading > 100) {
                    throw new IllegalStateException("sensor saturated");
                }
                Serial.println("accepted: " + reading);
            } catch (IllegalArgumentException | IllegalStateException e) {
                Serial.println("rejected: " + e.getMessage());
            } finally {
                Serial.println("checked one reading");
            }
        }

        Serial.println("Custom exception");
        try {
            throw new SensorException("sensor offline", 2);
        } catch (SensorException e) {
            Serial.println(e.getMessage() + " on sensor " + e.sensor());
        }

        Serial.println("Catching by supertype");
        try {
            throw new NumberFormatException("not a number");
        } catch (IllegalArgumentException e) {
            Serial.println("caught: " + e.getMessage());
        }

        Serial.println("Dividing by zero");
        int total = 250;
        int samples = 0;
        try {
            // Integer division by zero raises ArithmeticException, just like on the JVM.
            int average = total / samples;
            Serial.println(average);
        } catch (ArithmeticException e) {
            Serial.println("caught: " + e.getMessage());
        }

        Serial.println("Nested handlers");
        try {
            try {
                throw new UnsupportedOperationException("not implemented");
            } finally {
                Serial.println("inner finally runs first");
            }
        } catch (UnsupportedOperationException e) {
            Serial.println("outer catch: " + e.getMessage());
        }

        Serial.println("Across methods");
        try {
            // parse() throws two calls below this handler; the exception unwinds through sample().
            Serial.println("parsed " + sample(41));
            Serial.println("parsed " + sample(-1));
        } catch (IllegalArgumentException e) {
            Serial.println("caught " + e.getMessage());
        }
        try {
            withFinally(-1);
        } catch (IllegalArgumentException e) {
            Serial.println("caught " + e.getMessage());
        }

        Serial.println("Try-with-resources");
        try {
            withResource(1, -1);
        } catch (IllegalArgumentException e) {
            Serial.println("caught " + e.getMessage());
        }
        withResource(2, 5);

        Serial.println("Done");
    }
}
