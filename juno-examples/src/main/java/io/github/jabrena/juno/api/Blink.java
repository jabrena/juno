package io.github.jabrena.juno.api;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.DigitalOutput;

/**
 * Blinks the UNO R4 WiFi's built-in LED on digital pin 13 indefinitely. The LED remains on for
 * 500 milliseconds and off for 500 milliseconds, producing one complete blink cycle per second.
 *
 */
@Board(ArduinoUnoR4WiFi.class)
public final class Blink {
    private static final int LED = 13;

    public static void main(String[] args) {

        DigitalOutput led = DigitalOutput.of(LED);

        while (true) {
            led.high();
            Delay.millis(500);

            led.low();
            Delay.millis(500);
        }
    }
}
