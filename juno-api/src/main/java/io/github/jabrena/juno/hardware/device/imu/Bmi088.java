package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.BufferedGyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;

/**
 * Grove 6-Axis Accelerometer &amp; Gyroscope (BMI088), accessed through I2C.
 *
 * <p>The module contains independent accelerometer and gyroscope I2C devices. It is configured for ±3 g and ±2000
 * degrees per second at 100 Hz. As the BMI088 has no magnetometer, heading is the gyro's Z rate integrated from
 * construction or {@link #reset()} and therefore drifts over time. Create it once at startup and keep it still
 * while {@link #recalibrateOffset()} runs.
 */
public final class Bmi088 implements Accelerometer, BufferedGyroscope, SampleProvider {
    private static final int ACCEL_ADDRESS_DEFAULT = 0x19;
    private static final int GYRO_ADDRESS_DEFAULT = 0x69;
    private static final int ACCEL_CHIP_ID = 0x00;
    private static final int GYRO_CHIP_ID = 0x00;
    private static final int ACCEL_CHIP_ID_VALUE = 0x1E;
    private static final int GYRO_CHIP_ID_VALUE = 0x0F;
    private static final int ACCEL_X_LSB = 0x12;
    private static final int GYRO_X_LSB = 0x02;
    private static final int ACCEL_POWER_CONTROL = 0x7D;
    private static final int ACCEL_POWER_CONFIGURATION = 0x7C;
    private static final int ACCEL_CONFIGURATION = 0x40;
    private static final int ACCEL_RANGE = 0x41;
    private static final int GYRO_RANGE = 0x0F;
    private static final int GYRO_POWER_MODE = 0x11;
    /** Output data rate and filter bandwidth; after reset 2000 Hz, which would fill the FIFO in 50 ms. */
    private static final int GYRO_BANDWIDTH = 0x10;
    /** 100 Hz output data rate, 32 Hz filter bandwidth. */
    private static final int GYRO_100_HZ = 0x07;
    /** Overrun in bit 7, frames in the FIFO in bits 0-6. */
    private static final int GYRO_FIFO_STATUS = 0x0E;
    private static final int FIFO_FRAME_COUNT = 0x7F;
    /** FIFO mode in bits 6-7, axes in bits 0-1. */
    private static final int GYRO_FIFO_CONFIG_1 = 0x3E;
    /** Stream mode (the oldest frame is dropped when full) with the Z axis only: a frame is one 16-bit rate. */
    private static final int FIFO_STREAM_Z_ONLY = 0x83;
    /** Each read of two bytes here takes the oldest frame out of the FIFO. */
    private static final int GYRO_FIFO_DATA = 0x3F;
    private static final int FIFO_PERIOD_MILLIS = 10;
    private static final int ACCEL_ENABLED = 0x04;
    private static final int ACCEL_ACTIVE = 0x00;
    /** Normal bandwidth and 100 Hz output-data rate. */
    private static final int ACCEL_100_HZ = 0xA8;
    /** ±3 g. */
    private static final int ACCEL_3_G = 0x00;
    /** ±2000 degrees per second. */
    private static final int GYRO_2000_DPS = 0x00;
    private static final int GYRO_NORMAL_MODE = 0x00;
    private static final int CALIBRATION_SAMPLES = 64;
    private static final int FULL_TURN_MILLIDEGREES = 360_000;
    private static final float ACCEL_METRES_PER_SECOND_SQUARED_PER_LSB = 0.0008977f;
    private static final float GYRO_DEGREES_PER_SECOND_PER_LSB = 0.06103515625f;

    private final I2c i2c;
    private final Clock clock;
    private int gyroBiasZ;
    private int headingMillidegrees;
    private int gyroRateZ;
    private int lastMillis;
    private boolean started;
    private int bufferedSamples;

    /** The Grove module on {@code i2c}; collaborators may be mocks in a unit test. */
    public Bmi088(I2c i2c, Clock clock) {
        this.i2c = i2c;
        this.clock = clock;
        if (!begin()) {
            throw new IllegalStateException("BMI088 did not acknowledge initialization");
        }
    }

    /** Starts both BMI088 devices and returns {@code false} if either chip ID cannot be read. */
    private boolean begin() {
        i2c.begin();
        if (i2c.readRegister(ACCEL_ADDRESS_DEFAULT, ACCEL_CHIP_ID) != ACCEL_CHIP_ID_VALUE
                || i2c.readRegister(GYRO_ADDRESS_DEFAULT, GYRO_CHIP_ID) != GYRO_CHIP_ID_VALUE) {
            started = false;
            return false;
        }
        i2c.writeRegister(ACCEL_ADDRESS_DEFAULT, ACCEL_POWER_CONTROL, ACCEL_ENABLED);
        i2c.writeRegister(ACCEL_ADDRESS_DEFAULT, ACCEL_POWER_CONFIGURATION, ACCEL_ACTIVE);
        i2c.writeRegister(ACCEL_ADDRESS_DEFAULT, ACCEL_CONFIGURATION, ACCEL_100_HZ);
        i2c.writeRegister(ACCEL_ADDRESS_DEFAULT, ACCEL_RANGE, ACCEL_3_G);
        i2c.writeRegister(GYRO_ADDRESS_DEFAULT, GYRO_RANGE, GYRO_2000_DPS);
        i2c.writeRegister(GYRO_ADDRESS_DEFAULT, GYRO_POWER_MODE, GYRO_NORMAL_MODE);
        headingMillidegrees = 0;
        gyroBiasZ = 0;
        lastMillis = clock.millis();
        started = true;
        return true;
    }

    /** Returns X acceleration in whole metres per second squared, or zero if initialization failed. */
    @Override
    public int getXAccel() {
        return (int) acceleration(ACCEL_X_LSB);
    }

    /** Returns Y acceleration in whole metres per second squared, or zero if initialization failed. */
    @Override
    public int getYAccel() {
        return (int) acceleration(ACCEL_X_LSB + 2);
    }

    /** Returns Z acceleration in whole metres per second squared, or zero if initialization failed. */
    @Override
    public int getZAccel() {
        return (int) acceleration(ACCEL_X_LSB + 4);
    }

    /** Returns heading in clockwise whole degrees, modulo 360. */
    @Override
    public int getAngle() {
        if (!started) {
            return 0;
        }
        integrate();
        return headingMillidegrees / 1000;
    }

    /** Returns the clockwise Z angular velocity in degrees per second, or zero if initialization failed. */
    @Override
    public float getAngularVelocity() {
        if (!started) {
            return 0f;
        }
        integrate();
        return -gyroRateZ * GYRO_DEGREES_PER_SECOND_PER_LSB;
    }

    /** Measures the stationary Z-axis gyro bias over 64 samples. */
    @Override
    public void recalibrateOffset() {
        if (!started) {
            return;
        }
        int sum = 0;
        for (int sample = 0; sample < CALIBRATION_SAMPLES; sample++) {
            sum += signed(i2c.readRegister16(GYRO_ADDRESS_DEFAULT, GYRO_X_LSB + 4));
        }
        gyroBiasZ = sum / CALIBRATION_SAMPLES;
        lastMillis = clock.millis();
    }

    /** Resets the integrated heading to zero. */
    @Override
    public void reset() {
        headingMillidegrees = 0;
        lastMillis = clock.millis();
    }

    /** Returns three acceleration components (X, Y, Z), in metres per second squared. */
    @Override
    public int sampleSize() {
        return 3;
    }

    /** Writes X, Y and Z acceleration in metres per second squared into the caller-owned buffer. */
    @Override
    public void fetchSample(float[] sample, int offset) {
        sample[offset] = acceleration(ACCEL_X_LSB);
        sample[offset + 1] = acceleration(ACCEL_X_LSB + 2);
        sample[offset + 2] = acceleration(ACCEL_X_LSB + 4);
    }

    /** Sets the gyroscope to 100 Hz and restarts its FIFO in stream mode, the Z axis only, empty. */
    @Override
    public void startBuffering() {
        if (!started) {
            return;
        }
        i2c.writeRegister(GYRO_ADDRESS_DEFAULT, GYRO_BANDWIDTH, GYRO_100_HZ);
        // Writing the FIFO configuration also empties the FIFO.
        i2c.writeRegister(GYRO_ADDRESS_DEFAULT, GYRO_FIFO_CONFIG_1, FIFO_STREAM_Z_ONLY);
        bufferedSamples = 0;
    }

    /** Ten milliseconds: the 100 Hz rate {@link #startBuffering()} sets. */
    @Override
    public int samplePeriodMillis() {
        return FIFO_PERIOD_MILLIS;
    }

    /**
     * Reads every Z rate in the FIFO, oldest first, and returns their clockwise rotation in thousandths of a degree, the
     * stored offset removed. The FIFO holds 100 frames, a second at 100 Hz; when the reads come further apart, the oldest
     * are lost and the rotation of that time with them.
     */
    @Override
    public int readBufferedRotation() {
        bufferedSamples = 0;
        if (!started) {
            return 0;
        }
        int status = i2c.readRegister(GYRO_ADDRESS_DEFAULT, GYRO_FIFO_STATUS);
        if (status < 0) {
            return 0;
        }
        int frames = status & FIFO_FRAME_COUNT;
        int sum = 0;
        for (int frame = 0; frame < frames; frame++) {
            sum += signed(i2c.readRegister16(GYRO_ADDRESS_DEFAULT, GYRO_FIFO_DATA)) - gyroBiasZ;
        }
        bufferedSamples = frames;
        // Each frame's rate in degrees per second times its 10 ms is thousandths of a degree; the sensor's Z axis counts
        // counter-clockwise.
        return (int) (-sum * GYRO_DEGREES_PER_SECOND_PER_LSB * FIFO_PERIOD_MILLIS);
    }

    /** How many frames the last {@link #readBufferedRotation()} read. */
    @Override
    public int bufferedSamples() {
        return bufferedSamples;
    }

    private float acceleration(int register) {
        if (!started) {
            return 0f;
        }
        return signed(i2c.readRegister16(ACCEL_ADDRESS_DEFAULT, register)) * ACCEL_METRES_PER_SECOND_SQUARED_PER_LSB;
    }

    private void integrate() {
        int now = clock.millis();
        int elapsed = now - lastMillis;
        lastMillis = now;
        gyroRateZ = signed(i2c.readRegister16(GYRO_ADDRESS_DEFAULT, GYRO_X_LSB + 4)) - gyroBiasZ;
        int rateMillidegreesPerSecond = (int) (-gyroRateZ * GYRO_DEGREES_PER_SECOND_PER_LSB * 1000f);
        int heading = (headingMillidegrees + rateMillidegreesPerSecond * elapsed / 1000) % FULL_TURN_MILLIDEGREES;
        headingMillidegrees = heading < 0 ? heading + FULL_TURN_MILLIDEGREES : heading;
    }

    private int signed(int word) {
        return word >= 0x8000 ? word - 0x10000 : word;
    }
}
