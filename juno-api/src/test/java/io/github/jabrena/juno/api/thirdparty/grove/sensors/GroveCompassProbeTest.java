package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroveCompassProbeTest {
    private static final int BMM150_ADDRESS = 0x13;
    private static final int BMM150_CHIP_ID = 0x40;
    private static final int BMM150_DATA_X_LSB = 0x42;
    private static final int BMM150_DATA_Y_LSB = 0x44;
    private static final int BMM150_DATA_Z_LSB = 0x46;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        I2c.reset();
    }

    @Test
    void decodesSignedBmm150AxisValues() {
        assertEquals(123, GroveCompassProbe.decodeAxis(123 << 3, 13, 3));
        assertEquals(-123, GroveCompassProbe.decodeAxis((0x2000 - 123) << 3, 13, 3));
        assertEquals(456, GroveCompassProbe.decodeAxis(456 << 1, 15, 1));
        assertEquals(-456, GroveCompassProbe.decodeAxis((0x8000 - 456) << 1, 15, 1));
    }

    @Test
    void detectsTheBmm150AndWakesItUp() {
        I2c.device(BMM150_ADDRESS, BMM150_CHIP_ID, 0x32);

        assertEquals(GroveCompassProbe.BMM150, GroveCompassProbe.detect());
        assertEquals(0x01, I2c.writes.get(0)[2]);
        assertEquals(0x00, I2c.writes.get(1)[2]);
    }

    @Test
    void reportsNoCompassWhenTheChipIdIsWrong() {
        I2c.device(BMM150_ADDRESS, BMM150_CHIP_ID, 0x00);

        assertEquals(GroveCompassProbe.NOT_FOUND, GroveCompassProbe.detect());
    }

    @Test
    void readsTheThreeAxesFromTheDataRegisters() {
        I2c.device(BMM150_ADDRESS, BMM150_DATA_X_LSB, 123 << 3);
        I2c.device(BMM150_ADDRESS, BMM150_DATA_Y_LSB, (0x2000 - 123) << 3);
        I2c.device(BMM150_ADDRESS, BMM150_DATA_Z_LSB, 456 << 1);
        int[] axes = new int[GroveCompassProbe.AXIS_LENGTH];

        assertTrue(GroveCompassProbe.readRaw(axes, axes.length));
        assertEquals(123, axes[GroveCompassProbe.X_AXIS]);
        assertEquals(-123, axes[GroveCompassProbe.Y_AXIS]);
        assertEquals(456, axes[GroveCompassProbe.Z_AXIS]);
    }

    @Test
    void reportsAFailedReadWhenAnAxisDoesNotAnswer() {
        I2c.device(BMM150_ADDRESS, BMM150_DATA_X_LSB, 0);
        I2c.device(BMM150_ADDRESS, BMM150_DATA_Y_LSB, 0);
        int[] axes = new int[GroveCompassProbe.AXIS_LENGTH];

        assertFalse(GroveCompassProbe.readRaw(axes, axes.length));
    }

    @Test
    void rejectsAnAxisBufferThatIsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> GroveCompassProbe.readRaw(new int[2], 2));
    }
}
