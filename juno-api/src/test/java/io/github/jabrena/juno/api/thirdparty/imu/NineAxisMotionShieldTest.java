package io.github.jabrena.juno.api.thirdparty.imu;

import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NineAxisMotionShieldTest {
    private static final int DEVICE = NineAxisMotionShield.ADDRESS_DEFAULT;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        I2c.reset();
    }

    @Test
    void beginResetsTheSensorAndSwitchesItToAndroidImuMode() {
        I2c.device(DEVICE, 0x00, 0xA0);

        assertThat(NineAxisMotionShield.begin()).isTrue();

        assertThat(flat(I2c.writes)).containsSubsequence(
                DEVICE, 0x3D, 0x00,
                DEVICE, 0x3B, 0x80,
                DEVICE, 0x3E, 0x00,
                DEVICE, 0x3D, 0x08);
    }

    @Test
    void beginFailsWhenTheChipIdIsWrong() {
        I2c.device(DEVICE, 0x00, 0x32);

        assertThat(NineAxisMotionShield.begin()).isFalse();
        assertThat(I2c.writes).as("no mode is written after a failed identity check").isEmpty();
    }

    @Test
    void headingIsWrappedAndConvertedToDegrees() {
        I2c.device(DEVICE, 0x1A, 360 * 16 + 5);
        assertThat(NineAxisMotionShield.headingRaw()).isEqualTo(5);
        assertThat(NineAxisMotionShield.headingDegrees()).isZero();

        I2c.device(DEVICE, 0x1A, 90 * 16);
        assertThat(NineAxisMotionShield.headingDegrees()).isEqualTo(90);
    }

    @Test
    void aMissingHeadingReadsAsMinusOne() {
        assertThat(NineAxisMotionShield.headingRaw()).isEqualTo(-1);
        assertThat(NineAxisMotionShield.headingDegrees()).isEqualTo(-1);
        assertThat(NineAxisMotionShield.turnedSince(90)).isZero();
    }

    @Test
    void pitchAndGyroAreSignedSixteenBitValues() {
        I2c.device(DEVICE, 0x1E, 0xFFFF);
        assertThat(NineAxisMotionShield.pitchRaw()).isEqualTo(-1);

        I2c.device(DEVICE, 0x14, 0x0100);
        assertThat(NineAxisMotionShield.gyroXRaw()).isEqualTo(256);

        I2c.device(DEVICE, 0x14, 0x8000);
        assertThat(NineAxisMotionShield.gyroXRaw()).isEqualTo(-32768);
    }

    @Test
    void aMissingPitchReadsAsTheMinimumValue() {
        assertThat(NineAxisMotionShield.pitchRaw()).isEqualTo(Short.MIN_VALUE);
    }

    @Test
    void turnedSinceTakesTheShortestWayAroundTheCompass() {
        I2c.device(DEVICE, 0x1A, 10 * 16);

        assertThat(NineAxisMotionShield.turnedSince(350)).isEqualTo(20);
        assertThat(NineAxisMotionShield.turnedSince(-1)).as("no start heading").isZero();
    }

    @Test
    void calibrationReadsTheStatusRegister() {
        I2c.device(DEVICE, 0x35, 0xFF);

        assertThat(NineAxisMotionShield.calibration()).isEqualTo(255);
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
