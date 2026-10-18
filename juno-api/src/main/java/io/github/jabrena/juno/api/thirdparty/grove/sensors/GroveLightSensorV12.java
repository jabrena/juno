package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;

/**
 * Reads Seeed's analog <a href="https://www.seeedstudio.com/Grove-Light-Sensor-p-746.html">Grove
 * Light Sensor v1.2</a>. The result is a raw ADC sample; convert it using the installation's
 * calibration if an illuminance value is required. This class allocates nothing.
 */
public final class GroveLightSensorV12 {
    private GroveLightSensorV12() {
    }

    /** Reads the raw ADC value from an analog socket such as {@link GroveBaseShield#A2}. */
    public static int rawValue(int analogPin) {
        return Gpio.analogRead(analogPin);
    }
}
