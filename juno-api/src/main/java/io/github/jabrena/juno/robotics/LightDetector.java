package io.github.jabrena.juno.robotics;

/**
 * A sensor that measures white-light level, independent of the device that
 * provides it.
 */
public interface LightDetector {

    /** Returns the calibrated brightness as a percentage: 0 is darkness and 100 is intense sunlight. */
    float getLightValue();

    /**
     * Returns the uncalibrated brightness, between {@link #getLow()} and {@link #getHigh()}; low values are dark and
     * high values are bright. The range is usually 0 to 1 but depends on the hardware.
     */
    float getNormalizedLightValue();

    /** Returns the highest raw value the sensor reports, in intense bright light. */
    float getHigh();

    /** Returns the lowest raw value the sensor reports, in complete darkness. */
    float getLow();
}
