package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.i2c.I2c;
import io.github.jabrena.juno.api.util.Delay;

/**
 * Probes Seeed's Grove 3-Axis Digital Compass V2.0, which uses a BMM150, and reads its raw axes
 * without allocating. The module must be plugged into one of the shield's I2C sockets, never a D
 * socket. {@code new GroveCompassProbe(i2c)} probes through a given {@link I2c} instead of the board's, which is
 * how a unit test mocks it.
 */
public final class GroveCompassProbe {
    public static final int NOT_FOUND = 0;
    public static final int BMM150 = 1;
    public static final int AXIS_LENGTH = 3;
    public static final int X_AXIS = 0;
    public static final int Y_AXIS = 1;
    public static final int Z_AXIS = 2;

    private static final int BMM150_ADDRESS = 0x13;
    private static final int BMM150_POWER_CONTROL = 0x4B;
    private static final int BMM150_OPERATION_MODE = 0x4C;
    private static final int BMM150_CHIP_ID = 0x40;
    private static final int BMM150_DATA_X_LSB = 0x42;
    private static final int BMM150_DATA_Y_LSB = 0x44;
    private static final int BMM150_DATA_Z_LSB = 0x46;

    private final I2c i2c;

    /** A probe on the board's own I2C bus. Create it once, at startup. */
    public GroveCompassProbe() {
        this(MCU.getI2c());
    }

    /** A probe that talks to the compass through {@code i2c}: a mocked one in a unit test. */
    public GroveCompassProbe(I2c i2c) {
        this.i2c = i2c;
    }

    /** Starts I2C, probes the BMM150 and returns its chip identifier when present. */
    public int detect() {
        i2c.begin();
        // BMM150 starts suspended; the power-control bit makes its chip ID readable.
        i2c.writeRegister(BMM150_ADDRESS, BMM150_POWER_CONTROL, 0x01);
        Delay.millis(3);
        if (i2c.readRegister(BMM150_ADDRESS, BMM150_CHIP_ID) == 0x32) {
            i2c.writeRegister(BMM150_ADDRESS, BMM150_OPERATION_MODE, 0x00);
            return BMM150;
        }
        return NOT_FOUND;
    }

    /** Reads uncalibrated BMM150 magnetic-field counts into {@code axes}: X, Y and Z. */
    public boolean readRaw(int[] axes, int axesLength) {
        if (axesLength < AXIS_LENGTH || axesLength > axes.length) {
            throw new IllegalArgumentException("axesLength must be at least 3 and fit axes");
        }
        int x = i2c.readRegister16(BMM150_ADDRESS, BMM150_DATA_X_LSB);
        int y = i2c.readRegister16(BMM150_ADDRESS, BMM150_DATA_Y_LSB);
        int z = i2c.readRegister16(BMM150_ADDRESS, BMM150_DATA_Z_LSB);
        if (x < 0 || y < 0 || z < 0) {
            return false;
        }
        axes[X_AXIS] = decodeAxis(x, 13, 3);
        axes[Y_AXIS] = decodeAxis(y, 13, 3);
        axes[Z_AXIS] = decodeAxis(z, 15, 1);
        return true;
    }

    static int decodeAxis(int packed, int width, int shift) {
        int mask = (1 << width) - 1;
        int raw = (packed >> shift) & mask;
        int sign = 1 << (width - 1);
        return (raw & sign) == 0 ? raw : raw - (sign << 1);
    }
}
