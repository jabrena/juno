package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.imu.Imu;
import io.github.jabrena.juno.hardware.imu.ImuSample;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * The STMicroelectronics LSM6DS3TR six-axis inertial sensor on the Arduino UNO WiFi Rev2, read over SPI as plain Java on
 * top of {@link Spi} (so it needs no Arduino library). The sensor has an accelerometer and a gyroscope but no
 * magnetometer, so it cannot find north: the heading is the gyroscope's yaw rate integrated over time from the
 * moment {@link #begin} ran, clockwise positive, and it drifts a little with every minute of use. Roll and pitch come
 * from the accelerometer's gravity vector, so they are right only while the board is not accelerating.
 *
 * <pre>{@code
 * Imu imu = ArduinoUno.getImu();   // once, at startup
 * imu.begin();                     // keep the board still for about 1.5 s: it measures the gyroscope's bias
 * ImuSample sample = new ImuSample();
 * if (imu.read(sample)) {
 *     Serial.println(sample.headingMillidegrees());
 * }
 * }</pre>
 *
 * <p>The IMU's chip select is the board's pin {@link #CHIP_SELECT} (the megaavr core's {@code SPIIMU_SS}). The sensor is
 * set to 104 Hz, {@code ±2 g} and {@code ±1000 degrees per second}. Yaw and roll/pitch are reported in
 * milli-degrees (yaw rate in milli-degrees per second), the {@link ImuSample} units.
 */
public final class Lsm6ds3 implements Imu {
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
    @Override
    public boolean begin() {
        gpio.pinMode(CHIP_SELECT, Gpio.getOutput());
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
        heading = 0;
        lastMillis = clock.millis();
        started = true;
        return true;
    }

    /**
     * Reads one burst of the gyroscope and accelerometer, integrates the yaw rate into the heading and derives roll and
     * pitch from gravity. Returns {@code false} before {@link #begin} has succeeded.
     */
    @Override
    public boolean read(ImuSample sample) {
        if (!started) {
            sample.invalidate();
            return false;
        }
        readSample();
        int now = clock.millis();
        int elapsed = Math.min(now - lastMillis, MAX_STEP_MILLIS);
        lastMillis = now;
        // The gyroscope's Z axis points up, so a positive raw value is anticlockwise; the heading grows clockwise.
        int yawRate = -(word(4) - gyroBias) * MILLIDEGREES_PER_LSB;
        int turned = (heading + yawRate * elapsed / 1000) % FULL_TURN;
        heading = turned < 0 ? turned + FULL_TURN : turned;
        int ax = word(6);
        int ay = word(8);
        int az = word(10);
        int roll = ImuAngles.atan2Millidegrees(ay, az);
        int pitch = ImuAngles.atan2Millidegrees(-ax, ImuAngles.magnitude(ay, az));
        sample.update(heading, yawRate, pitch, roll, -1);
        return true;
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
