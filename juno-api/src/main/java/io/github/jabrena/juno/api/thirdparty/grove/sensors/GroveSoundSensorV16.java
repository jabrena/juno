package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.gpio.Gpio;
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;

/**
 * Reads Seeed's analog <a href="https://wiki.seeedstudio.com/Grove-Sound_Sensor/">Grove Sound
 * Sensor</a> (v1.6). The value is an ADC sample of the amplified microphone signal, not a dB
 * measurement. This class allocates nothing.
 */
public final class GroveSoundSensorV16 {
    private final Gpio gpio;

    /** A sensor on the board's own pins. Create it once, at startup. */
    public GroveSoundSensorV16() {
        this(MCU.getGpio());
    }

    /** A sensor read through {@code gpio}: a mocked one in a unit test. */
    public GroveSoundSensorV16(Gpio gpio) {
        this.gpio = gpio;
    }

    /** Reads the raw ADC value from an analog socket such as {@link GroveBaseShield#getA1()}. */
    public int rawValue(int analogPin) {
        return gpio.analogRead(analogPin);
    }
}
