package io.github.jabrena.juno.api.thirdparty.servo;

import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PwmServoShieldTest {

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        I2c.reset();
        PwmServoShield.begin();
    }

    @Test
    void beginSleepsTheOscillatorWhileItSetsTheFiftyHertzPrescaler() {
        assertThat(I2c.begins).isEqualTo(1);
        assertThat(flat(I2c.writes.subList(0, 3))).containsExactly(
                0x40, 0x00, 0x10,
                0x40, 0xFE, 121,
                0x40, 0x00, 0x00);
    }

    @Test
    void writeMapsAnAngleToItsPulseWidthTicks() {
        I2c.writes.clear();

        PwmServoShield.write(0, 90);

        assertThat(flat(I2c.writes)).containsExactly(
                0x40, 0x06, 0x00,
                0x40, 0x07, 0x00,
                0x40, 0x08, 301 & 0xFF,
                0x40, 0x09, 301 >> 8);
    }

    @Test
    void anglesOutsideTheServoRangeAreClamped() {
        I2c.writes.clear();
        PwmServoShield.write(0, 200);
        int clampedHigh = I2c.writes.get(2)[2];
        I2c.writes.clear();
        PwmServoShield.write(0, 180);

        assertThat(clampedHigh).isEqualTo(I2c.writes.get(2)[2]);
    }

    @Test
    void writeMicrosUsesTheChannelRegistersAndClampsToTheFrame() {
        I2c.writes.clear();

        PwmServoShield.writeMicros(1, 30_000);

        assertThat(flat(I2c.writes)).containsExactly(
                0x40, 0x0A, 0x00,
                0x40, 0x0B, 0x00,
                0x40, 0x0C, 0xFF,
                0x40, 0x0D, 0x0F);
    }

    @Test
    void releaseTurnsTheChannelFullyOff() {
        I2c.writes.clear();

        PwmServoShield.release(2);

        assertThat(flat(I2c.writes)).containsExactly(
                0x40, 0x0E, 0x00,
                0x40, 0x0F, 0x00,
                0x40, 0x10, 0x00,
                0x40, 0x11, 0x10);
    }

    @Test
    void beginCanUseAnAlternateAddress() {
        I2c.writes.clear();

        PwmServoShield.begin(0x41);

        assertThat(I2c.writes.get(0)[0]).isEqualTo(0x41);
    }

    private static List<Integer> flat(List<int[]> writes) {
        List<Integer> values = new ArrayList<>();
        for (int[] write : writes) {
            for (int value : write) {
                values.add(value);
            }
        }
        return values;
    }
}
