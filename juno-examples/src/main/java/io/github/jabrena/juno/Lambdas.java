package io.github.jabrena.juno;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Non-capturing/capturing block lambdas and static/bound method references. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class Lambdas {
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

    private Lambdas() {
    }

    static int twice(int value) {
        return value * 2;
    }

    static int apply(IntOperation operation, int value) {
        return operation.apply(value);
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        IntOperation square = value -> {
            return value * value;
        };

        int capturedAmount = 2;
        IntOperation captured = value -> {
            return value + capturedAmount;
        };

        IntOperation staticReference = Lambdas::twice;
        Offset offset = new Offset(3);
        IntOperation boundReference = offset::add;

        int result = apply(square, 2)
                + apply(captured, 1)
                + apply(staticReference, 2)
                + apply(boundReference, 3);

        Runnable printResult = () -> {
            Serial.print("Lambda result: ");
            Serial.println(result);
        };
        printResult.run();
    }
}
