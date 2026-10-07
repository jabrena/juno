package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Demonstrates constants, static variables, local variables, assignment, and block scope. */
public class Variables {
    private static final int STEP = 5;
    private static int counter = 10;

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
        Serial.println("board: " + board);
        Serial.println("active: " + active);
        Serial.println("original counter: " + originalCounter);

        counter = counter + STEP;
        Serial.println("updated counter: " + counter);

        Serial.println("distance: " + distance);
        Serial.println("temperature: " + temperature);
        Serial.println("voltage: " + voltage);

        if (active) {
            int doubledCounter = counter * 2;
            Serial.println("block-scoped value: " + doubledCounter);
        }
    }
}
