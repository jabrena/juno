package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.util.Delay;

/**
 * The Bosch BNO055 orientation sensor of the Arduino 9 Axis Motion Shield, read over I2C as plain
 * Java on top of {@link I2c} (so it needs no Arduino library). The sensor fuses its accelerometer and
 * gyroscope itself and reports the heading in degrees; this class runs it in the {@code IMU} fusion
 * mode, where the heading is relative to the direction the sensor pointed at power-up or at
 * {@link #begin}, needs no compass calibration and is not disturbed by magnetic fields. That suits
 * measuring how far a vehicle has turned:
 *
 * <pre>{@code
 * NineAxisMotionShield imu = new NineAxisMotionShield();   // once, at startup
 * imu.begin();
 * int start = imu.headingDegrees();
 * // ... turn left until imu.turnedSince(start) <= -90 (heading grows clockwise)
 * }</pre>
 *
 * <p>The shield's BNO055 answers at address {@code 0x28}; the {@code 0x29} variant is selected with
 * {@link #begin(int)}. Works on both boards, wherever {@code Wire} reaches the shield's SDA/SCL.
 * {@link #begin} drives D7, the shield's sensor reset line, so a program using the shield must not
 * use D7 for anything else. {@code new NineAxisMotionShield(gpio, i2c)} drives that line and talks to the
 * sensor through a given {@link Gpio} and {@link I2c} instead of the board's, which is how a unit test mocks them.
 *
 * @see <a href="https://docs.arduino.cc/hardware/9-axis-motion-shield/">Arduino 9 Axis Motion Shield</a>
 */
public final class NineAxisMotionShield {
    private static final int ADDRESS_DEFAULT = 0x28;
    private static final int ADDRESS_ALTERNATE = 0x29;

    private static final int CHIP_ID = 0x00;
    private static final int GYRO_X = 0x14;
    private static final int EULER_HEADING = 0x1A;
    private static final int EULER_PITCH = 0x1E;
    private static final int CALIBRATION_STATUS = 0x35;
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
    private static final int RESET_PIN = Gpio.getD7();
    private static final int RESET_LOW_MILLIS = 300;
    private static final int RESET_HIGH_MILLIS = 600;
    /** Heading is reported in sixteenths of a degree. */
    private static final int UNITS_PER_DEGREE = 16;
    private static final int SIGN_BIT = 0x8000;
    private static final int WORD = 0x10000;

    private final Gpio gpio;
    private final I2c i2c;
    private int address = ADDRESS_DEFAULT;

    /** A shield on the board's own pins and I2C bus. Create it once, at startup. */
    public NineAxisMotionShield() {
        this(MCU.getGpio(), MCU.getI2c());
    }

    /**
     * A shield whose reset line is driven through {@code gpio} and whose sensor is read over {@code i2c}: mocked ones
     * in a unit test.
     */
    public NineAxisMotionShield(Gpio gpio, I2c i2c) {
        this.gpio = gpio;
        this.i2c = i2c;
    }

    /** Starts the sensor at {@link #ADDRESS_DEFAULT}; see {@link #begin(int)}. */
    public boolean begin() {
        return begin(ADDRESS_DEFAULT);
    }

    /**
     * Starts the I2C bus and the sensor at {@code sensorAddress} in IMU fusion mode, returning
     * {@code false} if no BNO055 answered. Waits for the sensor to boot, so call it once at startup.
     */
    public boolean begin(int sensorAddress) {
        address = sensorAddress;
        gpio.pinMode(RESET_PIN, Gpio.getOutput());
        gpio.digitalWrite(RESET_PIN, false);
        Delay.millis(RESET_LOW_MILLIS);
        gpio.digitalWrite(RESET_PIN, true);
        Delay.millis(RESET_HIGH_MILLIS);
        i2c.begin();
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
    public int headingRaw() {
        int raw = i2c.readRegister16(address, EULER_HEADING);
        return raw < 0 ? -1 : raw % (360 * UNITS_PER_DEGREE);
    }

    /** The heading in whole degrees, {@code 0..359}, growing clockwise; {@code -1} if the read failed. */
    public int headingDegrees() {
        int raw = headingRaw();
        return raw < 0 ? -1 : raw / UNITS_PER_DEGREE;
    }

    /**
     * The pitch in sixteenths of a degree, signed, {@code 0} when the sensor's board is level; the
     * sign follows the BNO055's own convention (see its datasheet) and flips with the board's
     * mounting. {@link Short#MIN_VALUE} if the read failed. This is the tilt a self-balancing robot
     * rocks about when the shield is mounted with its X axis along the wheel axle.
     */
    public int pitchRaw() {
        return signed(i2c.readRegister16(address, EULER_PITCH));
    }

    /**
     * The angular rate about the X axis in sixteenths of a degree per second, signed, taken from the
     * gyroscope; the derivative of {@link #pitchRaw} without the delay of differencing it.
     * {@link Short#MIN_VALUE} if the read failed.
     */
    public int gyroXRaw() {
        return signed(i2c.readRegister16(address, GYRO_X));
    }

    /** Turns a register word {@code 0..65535} into a signed value, keeping the {@code -1} failure as {@link Short#MIN_VALUE}. */
    private int signed(int word) {
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
    public int turnedSince(int startDegrees) {
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
    public int calibration() {
        return i2c.readRegister(address, CALIBRATION_STATUS);
    }

    /** The sensor's default I2C address, {@code 0x28}: the shield's BNO055 as wired from the factory. */
    public int getAddressDefault() {
        return ADDRESS_DEFAULT;
    }

    /** The alternate I2C address, {@code 0x29}, selected by the shield's address jumper. */
    public int getAddressAlternate() {
        return ADDRESS_ALTERNATE;
    }
}
