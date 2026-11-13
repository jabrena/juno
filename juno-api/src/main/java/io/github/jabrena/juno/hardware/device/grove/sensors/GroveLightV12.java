package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.robotics.LightDetector;
import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * Reads Seeed's analog <a href="https://www.seeedstudio.com/Grove-Light-Sensor-p-746.html">Grove
 * Light Sensor v1.2</a> as a {@link LightDetector}. The sensor is bound to its analog socket when it is created. Its
 * normalized value is the ADC sample divided by the ADC full scale, and its light value is that fraction as a
 * percentage. The sensor responds non-linearly, so the percentage is relative brightness, not illuminance. This class
 * allocates nothing.
 */
public final class GroveLightV12 implements LightDetector, SampleProvider {
    /** Full scale of a 10-bit ADC, the default on the UNO boards. */
    public static final int ADC_10_BIT = 1023;
    /** Full scale of a 12-bit ADC, the default on the Nano ESP32. */
    public static final int ADC_12_BIT = 4095;

    private static final float LOW = 0.0f;
    private static final float HIGH = 1.0f;
    private static final float PERCENT = 100.0f;

    private final Gpio gpio;
    private final int analogPin;
    private final float adcMaximum;

    /** A sensor plugged into {@code analogPin}, read through the board's own GPIO pins ({@link MCU#getGpio()}), with an ADC
     * whose full scale is {@code adcMaximum}. */
    public GroveLightV12(int analogPin, int adcMaximum) {
        this(MCU.getGpio(), analogPin, adcMaximum);
    }

    /**
     * A sensor plugged into {@code analogPin} (such as a socket of a {@code GroveBaseShield}) and read through
     * {@code gpio}, with an ADC whose full scale is {@code adcMaximum}: {@link #ADC_10_BIT} or {@link #ADC_12_BIT}.
     * Pass the {@code Gpio} of the board the sensor is wired to, and create the sensor once, at startup.
     */
    public GroveLightV12(Gpio gpio, int analogPin, int adcMaximum) {
        if (adcMaximum <= 0) {
            throw new IllegalArgumentException("ADC full scale must be positive");
        }
        this.gpio = gpio;
        this.analogPin = analogPin;
        this.adcMaximum = adcMaximum;
    }

    /** Returns the brightness as a percentage of the ADC full scale, from 0 in darkness to 100 at saturation. */
    @Override
    public float getLightValue() {
        return getNormalizedLightValue() * PERCENT;
    }

    /**
     * Returns the ADC sample divided by the ADC full scale, from {@link #getLow()} in darkness to {@link #getHigh()}
     * at saturation.
     */
    @Override
    public float getNormalizedLightValue() {
        float normalized = gpio.analogRead(analogPin) / adcMaximum;
        return Math.max(LOW, Math.min(HIGH, normalized));
    }

    /** Returns 1, the normalized value at ADC full scale. */
    @Override
    public float getHigh() {
        return HIGH;
    }

    /** Returns 0, the normalized value in complete darkness. */
    @Override
    public float getLow() {
        return LOW;
    }

    /** Returns 1: the sample is one normalized light value. */
    @Override
    public int sampleSize() {
        return 1;
    }

    /** Writes the normalized light value, from 0 to 1, at {@code offset}. */
    @Override
    public void fetchSample(float[] sample, int offset) {
        sample[offset] = getNormalizedLightValue();
    }
}
