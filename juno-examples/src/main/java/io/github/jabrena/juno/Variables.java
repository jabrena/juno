package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates constants, static variables, local variables, assignment, and block scope. */
public final class Variables {
    private static final int STEP = 5;
    private static int counter = 10;

    private Variables() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        String board = "UNO R4 WiFi";
        boolean active = true;
        int originalCounter = counter;
        long distance = 123456789012L;
        float temperature = 23.5f;
        double voltage = 3.3;

        Serial.println("Variables demo");
        Serial.print("board: ");
        Serial.println(board);
        Serial.print("active: ");
        if (active) {
            Serial.println("true");
        } else {
            Serial.println("false");
        }
        Serial.print("original counter: ");
        Serial.println(originalCounter);

        counter = counter + STEP;
        Serial.print("updated counter: ");
        Serial.println(counter);

        Serial.print("distance: ");
        Serial.println(distance);
        Serial.print("temperature: ");
        Serial.println(temperature);
        Serial.print("voltage: ");
        Serial.println(voltage);

        if (active) {
            int doubledCounter = counter * 2;
            Serial.print("block-scoped value: ");
            Serial.println(doubledCounter);
        }
    }
}
