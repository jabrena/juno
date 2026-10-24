package io.github.jabrena.juno.hardware.imu;

/**
 * Caller-owned inertial sensor data. Angles and angular rates use milli-degrees to retain useful
 * resolution without floating-point state; create one instance and reuse it for every read.
 */
public final class ImuSample {
    private boolean valid;
    private int headingMillidegrees;
    private int yawRateMillidegreesPerSecond;
    private int pitchMillidegrees;
    private int rollMillidegrees;
    private int calibration;

    /** Returns whether this sample contains a successful sensor reading. */
    public boolean valid() {
        return valid;
    }

    /** Returns the heading in milli-degrees, relative to the sensor's configured reference. */
    public int headingMillidegrees() {
        return headingMillidegrees;
    }

    /** Returns the yaw rate in milli-degrees per second. */
    public int yawRateMillidegreesPerSecond() {
        return yawRateMillidegreesPerSecond;
    }

    /** Returns the pitch in milli-degrees. */
    public int pitchMillidegrees() {
        return pitchMillidegrees;
    }

    /** Returns the roll in milli-degrees. */
    public int rollMillidegrees() {
        return rollMillidegrees;
    }

    /** Returns the device-specific calibration status, or {@code -1} when unavailable. */
    public int calibration() {
        return calibration;
    }

    /** Replaces this sample with one successful reading. Intended for IMU implementations. */
    public void update(
            int headingMillidegrees,
            int yawRateMillidegreesPerSecond,
            int pitchMillidegrees,
            int rollMillidegrees,
            int calibration) {
        this.headingMillidegrees = headingMillidegrees;
        this.yawRateMillidegreesPerSecond = yawRateMillidegreesPerSecond;
        this.pitchMillidegrees = pitchMillidegrees;
        this.rollMillidegrees = rollMillidegrees;
        this.calibration = calibration;
        valid = true;
    }

    /** Marks this sample invalid while retaining its previous primitive values. */
    public void invalidate() {
        valid = false;
    }
}
