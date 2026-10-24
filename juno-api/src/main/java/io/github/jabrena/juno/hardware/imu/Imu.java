package io.github.jabrena.juno.hardware.imu;

/**
 * Device-independent inertial measurement unit. Implementations populate a caller-owned sample so
 * repeated reads do not allocate in a control loop.
 */
public interface Imu {

    /** Starts the sensor and returns whether it responded. Call once during setup. */
    boolean begin();

    /** Reads one consistent sensor sample into {@code sample}. Returns whether the sample is valid. */
    boolean read(ImuSample sample);
}
