package io.github.jabrena.juno.api.thirdparty.imu;

import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BNO055 nine-axis motion shield")
class NineAxisMotionShieldTest {
    private static final int DEVICE = NineAxisMotionShield.ADDRESS_DEFAULT;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        I2c.reset();
    }

    @Test
    @DisplayName("Begin reports success when the chip ID matches")
    void shouldReportSuccessWhenTheChipIdMatches() {
        // Given
        I2c.device(DEVICE, 0x00, 0xA0);

        // When
        boolean begun = NineAxisMotionShield.begin();

        // Then
        assertThat(begun).isTrue();
    }

    @Test
    @DisplayName("Begin resets the sensor and switches it to Android IMU mode")
    void shouldResetTheSensorAndSwitchItToAndroidImuMode() {
        // Given
        I2c.device(DEVICE, 0x00, 0xA0);

        // When
        NineAxisMotionShield.begin();

        // Then
        assertThat(flat(I2c.writes)).containsSubsequence(
                DEVICE, 0x3D, 0x00,
                DEVICE, 0x3B, 0x80,
                DEVICE, 0x3E, 0x00,
                DEVICE, 0x3D, 0x08);
    }

    @Test
    @DisplayName("Begin reports failure when the chip ID is wrong")
    void shouldReportFailureWhenTheChipIdIsWrong() {
        // Given
        I2c.device(DEVICE, 0x00, 0x32);

        // When
        boolean begun = NineAxisMotionShield.begin();

        // Then
        assertThat(begun).isFalse();
    }

    @Test
    @DisplayName("No mode is written after a failed identity check")
    void shouldWriteNoModeAfterAFailedIdentityCheck() {
        // Given
        I2c.device(DEVICE, 0x00, 0x32);

        // When
        NineAxisMotionShield.begin();

        // Then
        assertThat(I2c.writes).as("no mode is written after a failed identity check").isEmpty();
    }

    @Test
    @DisplayName("A full turn of heading wraps to zero degrees")
    void shouldWrapAFullTurnOfHeadingToZeroDegrees() {
        // Given
        I2c.device(DEVICE, 0x1A, 360 * 16 + 5);

        // When
        int[] heading = {NineAxisMotionShield.headingRaw(), NineAxisMotionShield.headingDegrees()};

        // Then
        assertThat(heading).containsExactly(5, 0);
    }

    @Test
    @DisplayName("Heading converts to degrees")
    void shouldConvertHeadingToDegrees() {
        // Given
        I2c.device(DEVICE, 0x1A, 90 * 16);

        // When
        int degrees = NineAxisMotionShield.headingDegrees();

        // Then
        assertThat(degrees).isEqualTo(90);
    }

    @Test
    @DisplayName("A missing heading reads as minus one with no turn")
    void shouldReadAMissingHeadingAsMinusOneWithNoTurn() {
        // When
        int[] values = {
                NineAxisMotionShield.headingRaw(),
                NineAxisMotionShield.headingDegrees(),
                NineAxisMotionShield.turnedSince(90)};

        // Then
        assertThat(values).containsExactly(-1, -1, 0);
    }

    @Test
    @DisplayName("Pitch is a signed sixteen-bit value")
    void shouldReadPitchAsASignedSixteenBitValue() {
        // Given
        I2c.device(DEVICE, 0x1E, 0xFFFF);

        // When
        int pitch = NineAxisMotionShield.pitchRaw();

        // Then
        assertThat(pitch).isEqualTo(-1);
    }

    @Test
    @DisplayName("Gyro X reads a positive sixteen-bit value")
    void shouldReadGyroXAsAPositiveSixteenBitValue() {
        // Given
        I2c.device(DEVICE, 0x14, 0x0100);

        // When
        int gyro = NineAxisMotionShield.gyroXRaw();

        // Then
        assertThat(gyro).isEqualTo(256);
    }

    @Test
    @DisplayName("Gyro X reads a negative value from the sign bit")
    void shouldReadGyroXAsANegativeValueFromTheSignBit() {
        // Given
        I2c.device(DEVICE, 0x14, 0x8000);

        // When
        int gyro = NineAxisMotionShield.gyroXRaw();

        // Then
        assertThat(gyro).isEqualTo(-32768);
    }

    @Test
    @DisplayName("A missing pitch reads as the minimum value")
    void shouldReadAMissingPitchAsTheMinimumValue() {
        // When
        int pitch = NineAxisMotionShield.pitchRaw();

        // Then
        assertThat(pitch).isEqualTo(Short.MIN_VALUE);
    }

    @Test
    @DisplayName("Turn takes the shortest way around the compass")
    void shouldTakeTheShortestWayAroundTheCompass() {
        // Given
        I2c.device(DEVICE, 0x1A, 10 * 16);

        // When
        int turned = NineAxisMotionShield.turnedSince(350);

        // Then
        assertThat(turned).isEqualTo(20);
    }

    @Test
    @DisplayName("No turn is reported without a start heading")
    void shouldReportNoTurnWithoutAStartHeading() {
        // Given
        I2c.device(DEVICE, 0x1A, 10 * 16);

        // When
        int turned = NineAxisMotionShield.turnedSince(-1);

        // Then
        assertThat(turned).as("no start heading").isZero();
    }

    @Test
    @DisplayName("Calibration reads the status register")
    void shouldReadTheCalibrationStatusRegister() {
        // Given
        I2c.device(DEVICE, 0x35, 0xFF);

        // When
        int calibration = NineAxisMotionShield.calibration();

        // Then
        assertThat(calibration).isEqualTo(255);
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
