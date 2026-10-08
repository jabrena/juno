package io.github.jabrena.juno.api.imu;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.i2c.I2c;

/**
 * The Bosch BNO055 orientation sensor of the Arduino 9 Axis Motion Shield, read over I2C as plain
 * Java on top of {@link I2c} (so it needs no Arduino library). The sensor fuses its accelerometer and
 * gyroscope itself and reports the heading in degrees; this class runs it in the {@code IMU} fusion
 * mode, where the heading is relative to the direction the sensor pointed at power-up or at
 * {@link #begin}, needs no compass calibration and is not disturbed by magnetic fields. That suits
 * measuring how far a vehicle has turned:
 *
 * <pre>{@code
 * int start = Bno055.headingDegrees();
 * // ... turn left until Bno055.turnedSince(start) <= -90 (heading grows clockwise)
 * }</pre>
 *
 * <p>The shield's BNO055 answers at address {@code 0x28}; the {@code 0x29} variant is selected with
 * {@link #begin(int)}. Works on both boards, wherever {@code Wire} reaches the shield's SDA/SCL.
 */
public final class Bno055 {
    private Bno055() {
    }

    public static final int ADDRESS_DEFAULT = 0x28;
    public static final int ADDRESS_ALTERNATE = 0x29;

    private static final int CHIP_ID = 0x00;
    private static final int GYRO_X = 0x14;
    private static final int EULER_HEADING = 0x1A;
    private static final int EULER_PITCH = 0x1E;
    private static final int CALIBRATION_STATUS = 0x35;
    private static final int OPERATION_MODE = 0x3D;
    private static final int POWER_MODE = 0x3E;
    private static final int CHIP_ID_VALUE = 0xA0;
    private static final int MODE_CONFIG = 0x00;
    private static final int MODE_IMU = 0x08;
    private static final int POWER_NORMAL = 0x00;
    /** The sensor boots in about 650 ms and needs up to 19 ms to switch between modes. */
    private static final int BOOT_MILLIS = 700;
    private static final int MODE_SWITCH_MILLIS = 30;
    /** Heading is reported in sixteenths of a degree. */
    private static final int UNITS_PER_DEGREE = 16;
    private static final int SIGN_BIT = 0x8000;
    private static final int WORD = 0x10000;

    private static int address = ADDRESS_DEFAULT;

    /** Starts the sensor at {@link #ADDRESS_DEFAULT}; see {@link #begin(int)}. */
    public static boolean begin() {
        return begin(ADDRESS_DEFAULT);
    }

    /**
     * Starts the I2C bus and the sensor at {@code sensorAddress} in IMU fusion mode, returning
     * {@code false} if no BNO055 answered. Waits for the sensor to boot, so call it once at startup.
     */
    public static boolean begin(int sensorAddress) {
        address = sensorAddress;
        I2c.begin();
        Delay.millis(BOOT_MILLIS);
        if (I2c.readRegister(address, CHIP_ID) != CHIP_ID_VALUE) {
            return false;
        }
        I2c.writeRegister(address, OPERATION_MODE, MODE_CONFIG);
        Delay.millis(MODE_SWITCH_MILLIS);
        I2c.writeRegister(address, POWER_MODE, POWER_NORMAL);
        Delay.millis(MODE_SWITCH_MILLIS);
        I2c.writeRegister(address, OPERATION_MODE, MODE_IMU);
        Delay.millis(MODE_SWITCH_MILLIS);
        return true;
    }

    /** The heading in sixteenths of a degree, {@code 0..5759}, growing clockwise; {@code -1} if the read failed. */
    public static int headingRaw() {
        int raw = I2c.readRegister16(address, EULER_HEADING);
        return raw < 0 ? -1 : raw % (360 * UNITS_PER_DEGREE);
    }

    /** The heading in whole degrees, {@code 0..359}, growing clockwise; {@code -1} if the read failed. */
    public static int headingDegrees() {
        int raw = headingRaw();
        return raw < 0 ? -1 : raw / UNITS_PER_DEGREE;
    }

    /**
     * The pitch in sixteenths of a degree, signed, {@code 0} when the sensor's board is level; the
     * sign follows the BNO055's own convention (see its datasheet) and flips with the board's
     * mounting. {@link Short#MIN_VALUE} if the read failed. This is the tilt a self-balancing robot
     * rocks about when the shield is mounted with its X axis along the wheel axle.
     */
    public static int pitchRaw() {
        return signed(I2c.readRegister16(address, EULER_PITCH));
    }

    /**
     * The angular rate about the X axis in sixteenths of a degree per second, signed, taken from the
     * gyroscope; the derivative of {@link #pitchRaw} without the delay of differencing it.
     * {@link Short#MIN_VALUE} if the read failed.
     */
    public static int gyroXRaw() {
        return signed(I2c.readRegister16(address, GYRO_X));
    }

    /** Turns a register word {@code 0..65535} into a signed value, keeping the {@code -1} failure as {@link Short#MIN_VALUE}. */
    private static int signed(int word) {
        if (word < 0) {
            return Short.MIN_VALUE;
        }
        return word >= SIGN_BIT ? word - WORD : word;
    }

    /**
     * How far the heading has moved since {@code startDegrees} (from {@link #headingDegrees}), as a
     * signed angle {@code -180..180}: positive clockwise, negative anticlockwise (a left turn). Handles
     * the wrap around 360. Returns {@code 0} if the read failed.
     */
    public static int turnedSince(int startDegrees) {
        int now = headingDegrees();
        if (now < 0 || startDegrees < 0) {
            return 0;
        }
        int difference = (now - startDegrees + 540) % 360 - 180;
        return difference;
    }

    /**
     * The calibration state, 2 bits each for the system (bits 6-7), gyroscope (4-5), accelerometer
     * (2-3) and magnetometer (0-1), where 3 is fully calibrated; {@code -1} if the read failed. In IMU
     * mode only the gyroscope and accelerometer matter, and the gyroscope calibrates on its own while
     * the sensor stands still for a moment.
     */
    public static int calibration() {
        return I2c.readRegister(address, CALIBRATION_STATUS);
    }
}
