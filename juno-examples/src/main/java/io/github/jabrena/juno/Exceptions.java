package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Demonstrates throwing and catching exceptions: {@code try}/{@code catch}/{@code finally}, multi-catch,
 * catching by a supertype, dividing by zero, nested handlers, and a custom exception class with its own field.
 *
 * <p>Juno handles exceptions locally: a {@code throw} is caught by a {@code catch} in the same method, and an
 * integer division by zero inside such a {@code try} raises {@code ArithmeticException("/ by zero")}. An
 * exception that leaves its method, or that no handler catches, prints
 * {@code Exception in thread "main" <class>: <message>} and halts the board.
 */
public final class Exceptions {
    private Exceptions() {
    }

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
                Serial.print("accepted: ");
                Serial.println(reading);
            } catch (IllegalArgumentException | IllegalStateException e) {
                Serial.print("rejected: ");
                Serial.println(e.getMessage());
            } finally {
                Serial.println("checked one reading");
            }
        }

        Serial.println("Custom exception");
        try {
            throw new SensorException("sensor offline", 2);
        } catch (SensorException e) {
            Serial.print(e.getMessage());
            Serial.print(" on sensor ");
            Serial.println(e.sensor());
        }

        Serial.println("Catching by supertype");
        try {
            throw new NumberFormatException("not a number");
        } catch (IllegalArgumentException e) {
            Serial.print("caught: ");
            Serial.println(e.getMessage());
        }

        Serial.println("Dividing by zero");
        int total = 250;
        int samples = 0;
        try {
            // Integer division by zero raises ArithmeticException, just like on the JVM.
            int average = total / samples;
            Serial.println(average);
        } catch (ArithmeticException e) {
            Serial.print("caught: ");
            Serial.println(e.getMessage());
        }

        Serial.println("Nested handlers");
        try {
            try {
                throw new UnsupportedOperationException("not implemented yet");
            } finally {
                Serial.println("inner finally runs first");
            }
        } catch (UnsupportedOperationException e) {
            Serial.print("outer catch: ");
            Serial.println(e.getMessage());
        }

        Serial.println("Done");
    }
}
