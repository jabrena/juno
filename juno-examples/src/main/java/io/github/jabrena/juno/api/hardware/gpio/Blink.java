package io.github.jabrena.juno.api.hardware.gpio;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Blinks the linked board's built-in LED indefinitely: on for 500 milliseconds, off for 500
 * milliseconds, one complete blink cycle per second.
 *
 */
public class Blink {

    public static void main(String[] args) {

        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());

        while (true) {
            led.high();
            Delay.millis(500);

            led.low();
            Delay.millis(500);
        }
    }
}
