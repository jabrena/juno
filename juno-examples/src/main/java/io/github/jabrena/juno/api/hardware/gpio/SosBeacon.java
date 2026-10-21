package io.github.jabrena.juno.api.hardware.gpio;

import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Delay;

/**
 * Flashes SOS in Morse on the built-in LED, forever. {@link MorseLed} is handed the board's {@link Gpio}, which is
 * what lets {@code MorseLedTest} hand it a mocked one and check the timing off the board.
 */
public final class SosBeacon {
    private static final int UNIT_MILLIS = 200;

    // A dot is 1 unit on, a dash 3; 1 unit off between marks, 3 between letters, 7 before the pattern repeats.
    private static final byte[] SOS = {
        1, 0, 1, 0, 1, 0, 0, 0,
        1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0,
        1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0
    };

    public static void main(String[] args) {
        Gpio gpio = Arduino.getGpio();
        MorseLed led = new MorseLed(gpio, gpio.getBuiltinLed(), SOS);

        while (true) {
            led.tick();
            Delay.millis(UNIT_MILLIS);
        }
    }
}
