package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;
import io.github.jabrena.juno.util.Delay;

/**
 * The Bosch BNO055 orientation sensor of the Arduino 9 Axis Motion Shield, read over I2C as plain Java on top of
 * {@link I2c} (so it needs no Arduino library). The sensor fuses its accelerometer and gyroscope itself, in the
 * {@code IMU} fusion mode: the heading is relative to the direction the sensor pointed at construction, needs no
 * compass calibration and is not disturbed by magnetic fields.
 *
 * <pre>{@code
 * NineAxisMotionShield imu = new NineAxisMotionShield();   // once, at startup
 * imu.reset();                                             // count the turn from here
 * // ... turn left until imu.getAngle() >= 270 (heading grows clockwise)
 * }</pre>
 *
 * <p>The shield's BNO055 answers at address {@code 0x28}. The board's reset line for the sensor is D7, so a program
 * using the shield must not use D7 for anything else. {@code new NineAxisMotionShield(gpio, i2c)} drives that line and
 * talks to the sensor through a given {@link Gpio} and {@link I2c} instead of the board's, which is how a unit test
 * mocks them.
 *
 * @see <a href="https://docs.arduino.cc/hardware/9-axis-motion-shield/">Arduino 9 Axis Motion Shield</a>
 */
public final class NineAxisMotionShield implements Accelerometer, Gyroscope, SampleProvider {
    private static final int ADDRESS_DEFAULT = 0x28;

    private static final int CHIP_ID = 0x00;
    private static final int ACC_X = 0x08;
    private static final int ACC_Y = 0x0A;
    private static final int ACC_Z = 0x0C;
    private static final int GYRO_X = 0x14;
    private static final int GYRO_Z = 0x18;
    private static final int EULER_HEADING = 0x1A;
    private static final int UNIT_SELECTION = 0x3B;
    private static final int OPERATION_MODE = 0x3D;
    private static final int POWER_MODE = 0x3E;
    private static final int CHIP_ID_VALUE = 0xA0;
    private static final int MODE_CONFIG = 0x00;
    private static final int MODE_IMU = 0x08;
    /** Android orientation (bit 7); the other unit bits stay 0: m/s², dps, degrees, Celsius. */
    private static final int UNIT_ANDROID = 0x80;
    private static final int POWER_NORMAL = 0x00;
    /** The sensor boots in about 650 ms and needs up to 19 ms to switch between modes. */
    private static final int BOOT_MILLIS = 700;
    private static final int MODE_SWITCH_MILLIS = 30;
    /**
     * The shield wires the BNO055's RESET line to D7 (fitted {@code R_RESET} on the V3 schematic, as
     * in Arduino's NineAxesMotion library); the chip only runs while it is high.
     */
    private static final int RESET_LOW_MILLIS = 300;
    private static final int RESET_HIGH_MILLIS = 600;
    /** The heading and the gyroscope are reported in sixteenths of a degree (per second for the rate). */
    private static final int UNITS_PER_DEGREE = 16;
    /** Acceleration is reported in hundredths of a metre per second squared, since the units are m/s². */
    private static final int UNITS_PER_METRE_PER_SECOND_SQUARED = 100;
    private static final int SIGN_BIT = 0x8000;
    private static final int WORD = 0x10000;

    private final Gpio gpio;
    private final I2c i2c;
    private final int resetPin;
    private int address = ADDRESS_DEFAULT;
    /** The raw heading that {@link #getAngle()} counts from; set by {@link #reset()}. */
    private int headingOffsetRaw;

    /** A shield on the board's own pins and I2C bus. Create it once, at startup. */
    public NineAxisMotionShield() {
        this(ArduinoUno.getGpio(), ArduinoUno.getI2c());
    }

    /**
     * A shield whose reset line is driven through {@code gpio} and whose sensor is read over {@code i2c}: mocked ones
     * in a unit test.
     */
    public NineAxisMotionShield(Gpio gpio, I2c i2c) {
        this.gpio = gpio;
        this.i2c = i2c;
        resetPin = gpio.digitalPin("D7");
        if (!begin()) {
            throw new IllegalStateException("BNO055 did not acknowledge initialization");
        }
    }

    /**
     * Starts the sensor at {@link #ADDRESS_DEFAULT} in IMU fusion mode. Waits for the sensor to boot, so construct it
     * once at startup; the heading counts from this moment.
     */
    private boolean begin() {
        return startAt(ADDRESS_DEFAULT);
    }

    private boolean startAt(int sensorAddress) {
        i2c.begin();
        address = sensorAddress;
        headingOffsetRaw = 0;
        gpio.pinMode(resetPin, gpio.getOutput());
        gpio.digitalWrite(resetPin, false);
        Delay.millis(RESET_LOW_MILLIS);
        gpio.digitalWrite(resetPin, true);
        Delay.millis(RESET_HIGH_MILLIS);
        Delay.millis(BOOT_MILLIS);
        if (i2c.readRegister(address, CHIP_ID) != CHIP_ID_VALUE) {
            return false;
        }
        i2c.writeRegister(address, OPERATION_MODE, MODE_CONFIG);
        Delay.millis(MODE_SWITCH_MILLIS);
        i2c.writeRegister(address, UNIT_SELECTION, UNIT_ANDROID);
        i2c.writeRegister(address, POWER_MODE, POWER_NORMAL);
        Delay.millis(MODE_SWITCH_MILLIS);
        i2c.writeRegister(address, OPERATION_MODE, MODE_IMU);
        Delay.millis(MODE_SWITCH_MILLIS);
        return true;
    }

    /** The heading in sixteenths of a degree, {@code 0..5759}, growing clockwise; {@code -1} if the read failed. */
    private int headingRaw() {
        int raw = i2c.readRegister16(address, EULER_HEADING);
        return raw < 0 ? -1 : raw % (360 * UNITS_PER_DEGREE);
    }

    /**
     * The angular rate about the Z axis in sixteenths of a degree per second, signed, counter-clockwise positive: the
     * BNO055's own convention with Z pointing up. {@link Short#MIN_VALUE} if the read failed.
     */
    private int gyroZRaw() {
        return signed(i2c.readRegister16(address, GYRO_Z));
    }

    /** The acceleration along the sensor's X axis in whole metres per second squared; {@code 0} if the read failed. */
    @Override
    public int getXAccel() {
        return (int) accelerationAxis(ACC_X);
    }

    /** The acceleration along the sensor's Y axis in whole metres per second squared; {@code 0} if the read failed. */
    @Override
    public int getYAccel() {
        return (int) accelerationAxis(ACC_Y);
    }

    /** The acceleration along the sensor's Z axis in whole metres per second squared; {@code 0} if the read failed. */
    @Override
    public int getZAccel() {
        return (int) accelerationAxis(ACC_Z);
    }

    /**
     * The heading in whole degrees, {@code 0..359}, growing clockwise since {@link #begin} or the last {@link #reset};
     * {@code -1} if the read failed.
     */
    @Override
    public int getAngle() {
        int raw = headingRaw();
        if (raw < 0) {
            return -1;
        }
        int fullTurn = 360 * UNITS_PER_DEGREE;
        return (raw - headingOffsetRaw + fullTurn) % fullTurn / UNITS_PER_DEGREE;
    }

    /** The yaw rate in degrees per second, clockwise positive; {@code NaN} if the read failed. */
    @Override
    public float getAngularVelocity() {
        int raw = gyroZRaw();
        if (raw == Short.MIN_VALUE) {
            return Float.NaN;
        }
        return -raw / (float) UNITS_PER_DEGREE;
    }

    /**
     * Does nothing: the BNO055 calibrates its gyroscope itself while the sensor stands still, so there is no bias to set.
     */
    @Override
    public void recalibrateOffset() {
        // Intentionally empty: the sensor has no bias register to write.
    }

    /** Makes the current heading the zero of {@link #getAngle()}; does nothing if the read failed. */
    @Override
    public void reset() {
        int raw = headingRaw();
        if (raw >= 0) {
            headingOffsetRaw = raw;
        }
    }

    /** Returns one acceleration axis in metres per second squared, or 0 if its read failed. */
    private float accelerationAxis(int register) {
        int raw = signed(i2c.readRegister16(address, register));
        return raw == Short.MIN_VALUE ? 0f : raw / (float) UNITS_PER_METRE_PER_SECOND_SQUARED;
    }

    /** Returns 3: the X, Y and Z acceleration in metres per second squared. */
    @Override
    public int sampleSize() {
        return 3;
    }

    /**
     * Writes the X, Y and Z acceleration in metres per second squared, starting at {@code offset}; an axis whose read
     * failed is written as 0.
     */
    @Override
    public void fetchSample(float[] sample, int offset) {
        sample[offset] = accelerationAxis(ACC_X);
        sample[offset + 1] = accelerationAxis(ACC_Y);
        sample[offset + 2] = accelerationAxis(ACC_Z);
    }

    /** Turns a register word {@code 0..65535} into a signed value, keeping the {@code -1} failure as {@link Short#MIN_VALUE}. */
    private int signed(int word) {
        if (word < 0) {
            return Short.MIN_VALUE;
        }
        return word >= SIGN_BIT ? word - WORD : word;
    }

}
