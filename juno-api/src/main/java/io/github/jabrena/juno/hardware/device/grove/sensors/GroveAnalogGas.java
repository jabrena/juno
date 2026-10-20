package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;

/**
 * Reads the analog {@code SIG} output of a Grove gas sensor connected to an analog Grove socket.
 * The ADC value is useful for observing changes, but is not a gas concentration: interpreting it
 * requires the particular sensor's warm-up and calibration procedure. This class allocates nothing.
 */
public final class GroveAnalogGas {
    private final Gpio gpio;

    /** A sensor on the board's own pins. Create it once, at startup. */
    public GroveAnalogGas() {
        this(MCU.getGpio());
    }

    /** A sensor read through {@code gpio}: a mocked one in a unit test. */
    public GroveAnalogGas(Gpio gpio) {
        this.gpio = gpio;
    }

    /** Reads the board's raw ADC value from an analog socket such as {@link GroveBaseShield#getA0()}. */
    public int rawValue(int analogPin) {
        return gpio.analogRead(analogPin);
    }
}
