package io.github.jabrena.juno.api;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.io.Gpio;

/**
 * Blinks the linked board's built-in LED indefinitely: on for 500 milliseconds, off for 500
 * milliseconds, one complete blink cycle per second.
 *
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public class Blink {

    public static void main(String[] args) {

        DigitalOutput led = DigitalOutput.of(Gpio.builtinLed());

        while (true) {
            led.high();
            Delay.millis(500);

            led.low();
            Delay.millis(500);
        }
    }
}
