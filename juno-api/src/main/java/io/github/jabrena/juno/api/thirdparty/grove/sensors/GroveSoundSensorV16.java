package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;

/**
 * Reads Seeed's analog <a href="https://wiki.seeedstudio.com/Grove-Sound_Sensor/">Grove Sound
 * Sensor</a> (v1.6). The value is an ADC sample of the amplified microphone signal, not a dB
 * measurement. This class allocates nothing.
 */
public final class GroveSoundSensorV16 {
    private GroveSoundSensorV16() {
    }

    /** Reads the raw ADC value from an analog socket such as {@link GroveBaseShield#A1}. */
    public static int rawValue(int analogPin) {
        return Gpio.analogRead(analogPin);
    }
}
