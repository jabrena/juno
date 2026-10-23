package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;

/**
 * Reads Seeed's <a href="https://wiki.seeedstudio.com/Grove-Temperature_Sensor_V1.2/">Grove
 * Temperature Sensor v1.2</a>. The module is a 100-kilohm thermistor divider; this driver exposes
 * both the raw ADC count and Seeed's v1.2 B-constant conversion. It allocates nothing.
 */
public final class GroveTemperatureSensorV12 {
    private static final float ADC_MAXIMUM = 1023.0f;
    private static final float B_CONSTANT_KELVIN = 4275.0f;
    private static final float REFERENCE_TEMPERATURE_KELVIN = 298.15f;
    private static final float KELVIN_TO_CELSIUS = 273.15f;
    private static final float NATURAL_LOG_TWO = 0.6931472f;

    private final Gpio gpio;

    /** A sensor on the board's own pins. Create it once, at startup. */
    public GroveTemperatureSensorV12() {
        this(ArduinoOne.getGpio());
    }

    /** A sensor read through {@code gpio}: a mocked one in a unit test. */
    public GroveTemperatureSensorV12(Gpio gpio) {
        this.gpio = gpio;
    }

    /** Reads the raw ADC value from an analog socket such as {@link GroveBaseShield#getA0()}. */
    public int rawValue(int analogPin) {
        return gpio.analogRead(analogPin);
    }

    /**
     * Reads and converts the thermistor voltage to degrees Celsius using the v1.2 Beta equation.
     * Returns {@link Float#NaN} when the ADC is at either rail, where the divider resistance is
     * undefined.
     */
    public float celsius(int analogPin) {
        return celsiusFromRaw(rawValue(analogPin));
    }

    /** Converts one previously sampled v1.2 ADC value to degrees Celsius. */
    public float celsiusFromRaw(int adcValue) {
        if (adcValue <= 0 || adcValue >= ADC_MAXIMUM) {
            return Float.NaN;
        }
        float resistanceRatio = ADC_MAXIMUM / adcValue - 1.0f;
        return 1.0f / (naturalLog(resistanceRatio) / B_CONSTANT_KELVIN
                + 1.0f / REFERENCE_TEMPERATURE_KELVIN) - KELVIN_TO_CELSIUS;
    }

    /* Natural log after range reduction to [1, 2); the odd-power series converges quickly there. */
    private float naturalLog(float value) {
        int exponent = 0;
        while (value >= 2.0f) {
            value *= 0.5f;
            exponent++;
        }
        while (value < 1.0f) {
            value *= 2.0f;
            exponent--;
        }
        float y = (value - 1.0f) / (value + 1.0f);
        float square = y * y;
        float term = y;
        float series = term;
        term *= square;
        series += term / 3.0f;
        term *= square;
        series += term / 5.0f;
        term *= square;
        series += term / 7.0f;
        term *= square;
        series += term / 9.0f;
        term *= square;
        series += term / 11.0f;
        return 2.0f * series + exponent * NATURAL_LOG_TWO;
    }
}
