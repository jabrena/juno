package io.github.jabrena.juno.api.hardware.gpio;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.RaspberryPico2;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

/** Blinks the Raspberry Pi Pico 2 W's built-in LED, which the board wires to its CYW43439 radio chip. */
@Board(RaspberryPico2WifiBoard.class)
public final class RaspberryPico2Blink {
    private static final int BLINK_DELAY_MILLIS = 500;

    /** Blinks the LED once per second: on for 500 milliseconds, off for 500 milliseconds. */
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(RaspberryPico2.getGpio().getBuiltinLed());
        while (true) {
            led.high();
            Delay.millis(BLINK_DELAY_MILLIS);
            led.low();
            Delay.millis(BLINK_DELAY_MILLIS);
        }
    }
}
