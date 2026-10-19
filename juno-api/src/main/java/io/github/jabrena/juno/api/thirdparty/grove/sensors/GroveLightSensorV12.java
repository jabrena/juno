package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;

/**
 * Reads Seeed's analog <a href="https://www.seeedstudio.com/Grove-Light-Sensor-p-746.html">Grove
 * Light Sensor v1.2</a>. The result is a raw ADC sample; convert it using the installation's
 * calibration if an illuminance value is required. This class allocates nothing.
 */
public final class GroveLightSensorV12 {
    private final Gpio gpio;

    /** A sensor on the board's own pins. Create it once, at startup. */
    public GroveLightSensorV12() {
        this(MCU.getGpio());
    }

    /** A sensor read through {@code gpio}: a mocked one in a unit test. */
    public GroveLightSensorV12(Gpio gpio) {
        this.gpio = gpio;
    }

    /** Reads the raw ADC value from an analog socket such as {@link GroveBaseShield#getA2()}. */
    public int rawValue(int analogPin) {
        return gpio.analogRead(analogPin);
    }
}
