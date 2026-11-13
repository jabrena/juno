package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;

/**
 * Reads Seeed's analog <a href="https://wiki.seeedstudio.com/Grove-Sound_Sensor/">Grove Sound
 * Sensor</a> (v1.6). The value is an ADC sample of the amplified microphone signal, not a dB
 * measurement. This class allocates nothing.
 */
public final class GroveSoundV16 {
    private final Gpio gpio;

    /** A sensor read through the board's own GPIO pins ({@link MCU#getGpio()}). */
    public GroveSoundV16() {
        this(MCU.getGpio());
    }

    /** A sensor read through {@code gpio}: a mocked one in a unit test. */
    public GroveSoundV16(Gpio gpio) {
        this.gpio = gpio;
    }

    /** Reads the raw ADC value from an analog socket such as {@link GroveBaseShield#getA1()}. */
    public int rawValue(int analogPin) {
        return gpio.analogRead(analogPin);
    }
}
