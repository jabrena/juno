package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates sequence, bifurcation, and iteration control-flow structures. */
public final class ControlFlow {
    private ControlFlow() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Control flow demo");

        Serial.println("Sequence");
        int initialValue = 6;
        int doubledValue = initialValue * 2;
        int finalValue = doubledValue + 3;
        Serial.print("initial value: ");
        Serial.println(initialValue);
        Serial.print("doubled value: ");
        Serial.println(doubledValue);
        Serial.print("final value: ");
        Serial.println(finalValue);

        Serial.println("Bifurcation");
        if (finalValue >= 10) {
            Serial.println("final value is at least 10");
        } else {
            Serial.println("final value is below 10");
        }

        int remainder = finalValue % 3;
        switch (remainder) {
            case 0 -> Serial.println("divisible by 3");
            case 1 -> Serial.println("remainder is 1");
            default -> Serial.println("remainder is 2");
        }

        Serial.println("Iteration with for");
        for (int index = 1; index <= 3; index++) {
            Serial.print("for index: ");
            Serial.println(index);
        }

        Serial.println("Iteration with while");
        int countdown = 3;
        while (countdown > 0) {
            Serial.print("countdown: ");
            Serial.println(countdown);
            countdown--;
        }

        Serial.println("Iteration with do-while");
        int attempt = 1;
        do {
            Serial.print("attempt: ");
            Serial.println(attempt);
            attempt++;
        } while (attempt <= 2);

        Serial.println("Control flow complete");
    }
}
