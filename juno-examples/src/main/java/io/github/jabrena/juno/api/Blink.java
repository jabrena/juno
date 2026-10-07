package io.github.jabrena.juno.api;

import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.io.Gpio;

/**
 * Blinks the linked board's built-in LED indefinitely: on for 500 milliseconds, off for 500
 * milliseconds, one complete blink cycle per second.
 *
 */
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
