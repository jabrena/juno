package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;

/**
 * Reads the analog {@code SIG} output of a Grove gas sensor connected to an analog Grove socket.
 * The ADC value is useful for observing changes, but is not a gas concentration: interpreting it
 * requires the particular sensor's warm-up and calibration procedure. This class allocates nothing.
 */
public final class GroveAnalogGas {
    private GroveAnalogGas() {
    }

    /** Reads the board's raw ADC value from an analog socket such as {@link GroveBaseShield#A0}. */
    public static int rawValue(int analogPin) {
        return Gpio.analogRead(analogPin);
    }
}
