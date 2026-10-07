package io.github.jabrena.juno;

import io.github.jabrena.juno.Lambdas.IntOperation;
import io.github.jabrena.juno.Lambdas.Offset;
import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Non-capturing/capturing block lambdas and bound method references. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Lambdas {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Lambdas lambdas = new Lambdas();

        IntOperation square = value -> {
            return value * value;
        };

        int capturedAmount = 2;
        IntOperation captured = value -> {
            return value + capturedAmount;
        };

        IntOperation instanceReference = lambdas::twice;
        Offset offset = new Offset(3);
        IntOperation boundReference = offset::add;

        int result = lambdas.apply(square, 2)
                + lambdas.apply(captured, 1)
                + lambdas.apply(instanceReference, 2)
                + lambdas.apply(boundReference, 3);

        Runnable printResult = () -> {
            Serial.println("Lambda result: " + result);
        };
        printResult.run();
    }

    @FunctionalInterface
    interface IntOperation {
        int apply(int value);
    }

    static final class Offset {
        private final int amount;

        Offset(int amount) {
            this.amount = amount;
        }

        int add(int value) {
            return value + amount;
        }
    }

    private int twice(int value) {
        return value * 2;
    }

    private int apply(IntOperation operation, int value) {
        return operation.apply(value);
    }
}
