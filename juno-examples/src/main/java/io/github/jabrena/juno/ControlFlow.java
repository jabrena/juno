package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Demonstrates sequence, bifurcation, and iteration control-flow structures. */
public class ControlFlow {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Control flow demo");

        Serial.println("Sequence");
        int initialValue = 6;
        int doubledValue = initialValue * 2;
        int finalValue = doubledValue + 3;
        Serial.println("initial value: " + initialValue);
        Serial.println("doubled value: " + doubledValue);
        Serial.println("final value: " + finalValue);

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
            Serial.println("for index: " + index);
        }

        Serial.println("Iteration with while");
        int countdown = 3;
        while (countdown > 0) {
            Serial.println("countdown: " + countdown);
            countdown--;
        }

        Serial.println("Iteration with do-while");
        int attempt = 1;
        do {
            Serial.println("attempt: " + attempt);
            attempt++;
        } while (attempt <= 2);

        Serial.println("Control flow complete");
    }
}
