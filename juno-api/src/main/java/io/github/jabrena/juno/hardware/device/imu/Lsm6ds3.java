package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;
import io.github.jabrena.juno.util.Delay;

/**
 * The STMicroelectronics LSM6DS3TR six-axis inertial sensor on the Arduino UNO WiFi Rev2, read over SPI as plain Java on
 * top of {@link Spi} (so it needs no Arduino library). The sensor has an accelerometer and a gyroscope but no
 * magnetometer, so it cannot find north: the heading is the gyroscope's yaw rate integrated over time from the
 * moment {@link #begin} ran, clockwise positive, and it drifts a little with every minute of use.
 *
 * <pre>{@code
 * Gyroscope gyroscope = ArduinoUno.getGyroscope();       // once, at startup: starts the sensor, keep the board still
 * Accelerometer accelerometer = ArduinoUno.getAccelerometer();
 * Serial.println(gyroscope.getAngle());                  // heading in whole degrees, clockwise
 * Serial.println(accelerometer.getXAccel());             // whole m/s^2 along the board's X axis
 * }</pre>
 *
 * <p>The IMU's chip select is the board's pin {@link #CHIP_SELECT} (the megaavr core's {@code SPIIMU_SS}). The sensor is
 * set to 104 Hz, {@code ±2 g} and {@code ±1000 degrees per second}.
 */
public final class Lsm6ds3 implements Accelerometer, Gyroscope, SampleProvider {
    /** The IMU's chip-select line on the UNO WiFi Rev2: PB3 in the megaavr core, outside the header pins. */
    private static final int CHIP_SELECT = 30;

    private static final int WHO_AM_I = 0x0F;
    /** The UNO WiFi Rev2's LSM6DS3TR answers 0x69, the original LSM6DS3's ID; the LSM6DS3TR-C answers 0x6A. */
    private static final int WHO_AM_I_LSM6DS3 = 0x69;
    private static final int WHO_AM_I_LSM6DS3TR_C = 0x6A;
    private static final int CTRL1_XL = 0x10;
    private static final int CTRL2_G = 0x11;
    private static final int CTRL3_C = 0x12;
    private static final int OUTX_L_G = 0x22;
    /** Gyro x, y, z then accelerometer x, y, z: twelve contiguous registers, read in one SPI burst. */
    private static final int SAMPLE_BYTES = 12;
    private static final int CTRL3_C_SW_RESET = 0x01;
    /** Block data update (bit 6) and register address auto-increment (bit 2), so a burst reads one consistent sample. */
    private static final int CTRL3_C_BDU_AUTO_INCREMENT = 0x44;
    /** 104 Hz output, {@code ±2 g}. */
    private static final int CTRL1_XL_104HZ_2G = 0x40;
    /** 104 Hz output, {@code ±1000 degrees per second}. */
    private static final int CTRL2_G_104HZ_1000DPS = 0x48;
    /** One LSB of gyro output at {@code ±1000 degrees per second} is 35 milli-degrees per second. */
    private static final int MILLIDEGREES_PER_LSB = 35;
    /** One LSB of accelerometer output at {@code ±2 g} is 0.061 mg, about 0.000598 metres per second squared. */
    private static final float METRES_PER_SECOND_SQUARED_PER_LSB = 0.000598f;
    private static final int FULL_TURN = 360_000;
    private static final int READ_FLAG = 0x80;
    private static final int RESET_MILLIS = 20;
    private static final int STARTUP_MILLIS = 20;
    /** The gyroscope's output is a start-up transient for its first few hundred milliseconds; skip it. */
    private static final int SETTLE_SAMPLES = 32;
    private static final int CALIBRATION_SAMPLES = 128;
    private static final int SAMPLE_STEP_MILLIS = 10;
    /** A gap longer than this is clamped, so a stalled program does not integrate a huge turn at once. */
    private static final int MAX_STEP_MILLIS = 1000;

    private final Gpio gpio;
    private final Spi spi;
    private final Clock clock;
    private final int[] raw = new int[SAMPLE_BYTES];
    private int gyroBias;
    private int heading;
    private int yawRate;
    private int lastMillis;
    private boolean started;

    /** The IMU on the board's own pins, bus and clock. Create it once, at startup. */
    public Lsm6ds3(Gpio gpio, Spi spi, Clock clock) {
        this.gpio = gpio;
        this.spi = spi;
        this.clock = clock;
    }

    /**
     * Starts the bus and the sensor, returning {@code false} if the sensor did not answer with its chip ID. Then waits
     * for the gyroscope to settle and measures its bias over about 2 s, so keep the board still while it runs.
     */
    public boolean begin() {
        gpio.pinMode(CHIP_SELECT, gpio.getOutput());
        gpio.digitalWrite(CHIP_SELECT, true);
        spi.begin();
        writeRegister(CTRL3_C, CTRL3_C_SW_RESET);
        Delay.millis(RESET_MILLIS);
        int chipId = readRegister(WHO_AM_I);
        if (chipId != WHO_AM_I_LSM6DS3 && chipId != WHO_AM_I_LSM6DS3TR_C) {
            return false;
        }
        writeRegister(CTRL3_C, CTRL3_C_BDU_AUTO_INCREMENT);
        writeRegister(CTRL1_XL, CTRL1_XL_104HZ_2G);
        writeRegister(CTRL2_G, CTRL2_G_104HZ_1000DPS);
        Delay.millis(STARTUP_MILLIS);
        sampleGyroBias();
        heading = 0;
        lastMillis = clock.millis();
        started = true;
        return true;
    }

    /**
     * Measures the gyroscope's bias while the board is still: discards the start-up transient, then averages
     * {@link #CALIBRATION_SAMPLES} readings. Takes about 2 s.
     */
    private void sampleGyroBias() {
        for (int i = 0; i < SETTLE_SAMPLES; i++) {
            readSample();
            Delay.millis(SAMPLE_STEP_MILLIS);
        }
        int sum = 0;
        for (int i = 0; i < CALIBRATION_SAMPLES; i++) {
            readSample();
            sum += word(4);
            Delay.millis(SAMPLE_STEP_MILLIS);
        }
        gyroBias = sum / CALIBRATION_SAMPLES;
    }

    /**
     * Reads the sensor and integrates the yaw rate into the heading, so the heading follows the board's turns. The
     * readers below call it, so each one advances the heading by the time since the previous read.
     */
    private void integrate() {
        readSample();
        int now = clock.millis();
        int elapsed = Math.min(now - lastMillis, MAX_STEP_MILLIS);
        lastMillis = now;
        // The gyroscope's Z axis points up, so a positive raw value is anticlockwise; the heading grows clockwise.
        yawRate = -(word(4) - gyroBias) * MILLIDEGREES_PER_LSB;
        int turned = (heading + yawRate * elapsed / 1000) % FULL_TURN;
        heading = turned < 0 ? turned + FULL_TURN : turned;
    }

    /** The acceleration along the board's X axis in whole metres per second squared; {@code 0} before {@link #begin}. */
    @Override
    public int getXAccel() {
        return (int) accelerationAxis(6);
    }

    /** The acceleration along the board's Y axis in whole metres per second squared; {@code 0} before {@link #begin}. */
    @Override
    public int getYAccel() {
        return (int) accelerationAxis(8);
    }

    /** The acceleration along the board's Z axis in whole metres per second squared; {@code 0} before {@link #begin}. */
    @Override
    public int getZAccel() {
        return (int) accelerationAxis(10);
    }

    /**
     * The heading in whole degrees, {@code 0..359}, growing clockwise since {@link #begin} or the last {@link #reset};
     * {@code 0} before {@link #begin}.
     */
    @Override
    public int getAngle() {
        if (!started) {
            return 0;
        }
        integrate();
        return heading / 1000;
    }

    /** The yaw rate in degrees per second, clockwise positive; {@code 0} before {@link #begin}. */
    @Override
    public float getAngularVelocity() {
        if (!started) {
            return 0f;
        }
        integrate();
        return yawRate / 1000f;
    }

    /** Measures the gyroscope's bias again; keep the board still for about 2 s. Does nothing before {@link #begin}. */
    @Override
    public void recalibrateOffset() {
        if (started) {
            sampleGyroBias();
            lastMillis = clock.millis();
        }
    }

    /** Sets the heading back to zero, so {@link #getAngle()} counts the turns made from now on. */
    @Override
    public void reset() {
        heading = 0;
        lastMillis = clock.millis();
    }

    /** Returns the sample of the axis at {@code low}, in metres per second squared, or 0 before {@link #begin}. */
    private float accelerationAxis(int low) {
        if (!started) {
            return 0f;
        }
        readSample();
        return word(low) * METRES_PER_SECOND_SQUARED_PER_LSB;
    }

    /** Returns 3: the X, Y and Z acceleration in metres per second squared. */
    @Override
    public int sampleSize() {
        return 3;
    }

    /**
     * Writes the X, Y and Z acceleration in metres per second squared, starting at {@code offset}; all zero before
     * {@link #begin}.
     */
    @Override
    public void fetchSample(float[] sample, int offset) {
        if (!started) {
            sample[offset] = 0f;
            sample[offset + 1] = 0f;
            sample[offset + 2] = 0f;
            return;
        }
        readSample();
        sample[offset] = word(6) * METRES_PER_SECOND_SQUARED_PER_LSB;
        sample[offset + 1] = word(8) * METRES_PER_SECOND_SQUARED_PER_LSB;
        sample[offset + 2] = word(10) * METRES_PER_SECOND_SQUARED_PER_LSB;
    }

    /** Reads the twelve bytes of gyro and accelerometer output into {@link #raw}. */
    private void readSample() {
        gpio.digitalWrite(CHIP_SELECT, false);
        spi.transfer(READ_FLAG | OUTX_L_G);
        for (int i = 0; i < SAMPLE_BYTES; i++) {
            raw[i] = spi.transfer(0);
        }
        gpio.digitalWrite(CHIP_SELECT, true);
    }

    /** The signed 16-bit value whose low byte is at {@code raw[low]} and whose high byte follows it. */
    private int word(int low) {
        int value = raw[low] | (raw[low + 1] << 8);
        return value >= 0x8000 ? value - 0x10000 : value;
    }

    private int readRegister(int register) {
        gpio.digitalWrite(CHIP_SELECT, false);
        spi.transfer(READ_FLAG | register);
        int value = spi.transfer(0);
        gpio.digitalWrite(CHIP_SELECT, true);
        return value;
    }

    private void writeRegister(int register, int value) {
        gpio.digitalWrite(CHIP_SELECT, false);
        spi.transfer(register);
        spi.transfer(value);
        gpio.digitalWrite(CHIP_SELECT, true);
    }
}
