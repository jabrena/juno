package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.imu.ImuSample;
import io.github.jabrena.juno.testing.ImmediateDelay;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BNO055 nine-axis motion shield")
class NineAxisMotionShieldTest {
    private Gpio pins;
    private I2c bus;
    private NineAxisMotionShield imu;
    private int device;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        pins = mock(Gpio.class);
        bus = mock(I2c.class);
        // A register a test does not set answers -1, like a device that does not acknowledge.
        when(bus.readRegister(anyInt(), anyInt())).thenReturn(-1);
        when(bus.readRegister16(anyInt(), anyInt())).thenReturn(-1);
        imu = new NineAxisMotionShield(pins, bus);
        device = imu.getAddressDefault();
    }

    @Test
    @DisplayName("Begin pulses D7 low then high to release the BNO055 from reset")
    void shouldPulseTheResetLineBeforeTalkingToTheSensor() {
        // Given
        when(bus.readRegister(device, 0x00)).thenReturn(0xA0);

        // When
        ImmediateDelay.run(imu::begin);

        // Then
        InOrder order = inOrder(pins);
        order.verify(pins).pinMode(Gpio.getD7(), Gpio.getOutput());
        order.verify(pins).digitalWrite(Gpio.getD7(), false);
        order.verify(pins).digitalWrite(Gpio.getD7(), true);
    }

    @Test
    @DisplayName("Begin reports success when the chip ID matches")
    void shouldReportSuccessWhenTheChipIdMatches() {
        // Given
        when(bus.readRegister(device, 0x00)).thenReturn(0xA0);

        // When
        boolean begun = ImmediateDelay.get(imu::begin);

        // Then
        assertThat(begun).isTrue();
    }

    @Test
    @DisplayName("Begin resets the sensor and switches it to Android IMU mode")
    void shouldResetTheSensorAndSwitchItToAndroidImuMode() {
        // Given
        when(bus.readRegister(device, 0x00)).thenReturn(0xA0);

        // When
        ImmediateDelay.run(imu::begin);

        // Then
        InOrder order = inOrder(bus);
        order.verify(bus).writeRegister(device, 0x3D, 0x00);
        order.verify(bus).writeRegister(device, 0x3B, 0x80);
        order.verify(bus).writeRegister(device, 0x3E, 0x00);
        order.verify(bus).writeRegister(device, 0x3D, 0x08);
    }

    @Test
    @DisplayName("Begin reports failure when the chip ID is wrong")
    void shouldReportFailureWhenTheChipIdIsWrong() {
        // Given
        when(bus.readRegister(device, 0x00)).thenReturn(0x32);

        // When
        boolean begun = ImmediateDelay.get(imu::begin);

        // Then
        assertThat(begun).isFalse();
    }

    @Test
    @DisplayName("No mode is written after a failed identity check")
    void shouldWriteNoModeAfterAFailedIdentityCheck() {
        // Given
        when(bus.readRegister(device, 0x00)).thenReturn(0x32);

        // When
        ImmediateDelay.run(imu::begin);

        // Then
        verify(bus, never()).writeRegister(anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("A full turn of heading wraps to zero degrees")
    void shouldWrapAFullTurnOfHeadingToZeroDegrees() {
        // Given
        when(bus.readRegister16(device, 0x1A)).thenReturn(360 * 16 + 5);

        // When
        int[] heading = {imu.headingRaw(), imu.headingDegrees()};

        // Then
        assertThat(heading).containsExactly(5, 0);
    }

    @Test
    @DisplayName("Heading converts to degrees")
    void shouldConvertHeadingToDegrees() {
        // Given
        when(bus.readRegister16(device, 0x1A)).thenReturn(90 * 16);

        // When
        int degrees = imu.headingDegrees();

        // Then
        assertThat(degrees).isEqualTo(90);
    }

    @Test
    @DisplayName("A missing heading reads as minus one with no turn")
    void shouldReadAMissingHeadingAsMinusOneWithNoTurn() {
        // When
        int[] values = {
                imu.headingRaw(),
                imu.headingDegrees(),
                imu.turnedSince(90)};

        // Then
        assertThat(values).containsExactly(-1, -1, 0);
    }

    @Test
    @DisplayName("Pitch is a signed sixteen-bit value")
    void shouldReadPitchAsASignedSixteenBitValue() {
        // Given
        when(bus.readRegister16(device, 0x1E)).thenReturn(0xFFFF);

        // When
        int pitch = imu.pitchRaw();

        // Then
        assertThat(pitch).isEqualTo(-1);
    }

    @Test
    @DisplayName("Gyro X reads a positive sixteen-bit value")
    void shouldReadGyroXAsAPositiveSixteenBitValue() {
        // Given
        when(bus.readRegister16(device, 0x14)).thenReturn(0x0100);

        // When
        int gyro = imu.gyroXRaw();

        // Then
        assertThat(gyro).isEqualTo(256);
    }

    @Test
    @DisplayName("Gyro X reads a negative value from the sign bit")
    void shouldReadGyroXAsANegativeValueFromTheSignBit() {
        // Given
        when(bus.readRegister16(device, 0x14)).thenReturn(0x8000);

        // When
        int gyro = imu.gyroXRaw();

        // Then
        assertThat(gyro).isEqualTo(-32768);
    }

    @Test
    @DisplayName("A missing pitch reads as the minimum value")
    void shouldReadAMissingPitchAsTheMinimumValue() {
        // When
        int pitch = imu.pitchRaw();

        // Then
        assertThat(pitch).isEqualTo(Short.MIN_VALUE);
    }

    @Test
    @DisplayName("Turn takes the shortest way around the compass")
    void shouldTakeTheShortestWayAroundTheCompass() {
        // Given
        when(bus.readRegister16(device, 0x1A)).thenReturn(10 * 16);

        // When
        int turned = imu.turnedSince(350);

        // Then
        assertThat(turned).isEqualTo(20);
    }

    @Test
    @DisplayName("No turn is reported without a start heading")
    void shouldReportNoTurnWithoutAStartHeading() {
        // Given
        when(bus.readRegister16(device, 0x1A)).thenReturn(10 * 16);

        // When
        int turned = imu.turnedSince(-1);

        // Then
        assertThat(turned).as("no start heading").isZero();
    }

    @Test
    @DisplayName("Calibration reads the status register")
    void shouldReadTheCalibrationStatusRegister() {
        // Given
        when(bus.readRegister(device, 0x35)).thenReturn(0xFF);

        // When
        int calibration = imu.calibration();

        // Then
        assertThat(calibration).isEqualTo(255);
    }

    @Test
    @DisplayName("Reads a device-independent navigation sample")
    void shouldReadADeviceIndependentNavigationSample() {
        // Given
        when(bus.readRegister16(device, 0x1A)).thenReturn(90 * 16);
        when(bus.readRegister16(device, 0x18)).thenReturn(16);
        when(bus.readRegister16(device, 0x1E)).thenReturn(-1);
        when(bus.readRegister16(device, 0x1C)).thenReturn(8);
        when(bus.readRegister(device, 0x35)).thenReturn(0xFF);
        ImuSample sample = new ImuSample();

        // When
        boolean read = imu.read(sample);

        // Then
        assertThat(read).isFalse();
        assertThat(sample.valid()).isFalse();
    }

    @Test
    @DisplayName("Converts a valid navigation sample to milli-degrees")
    void shouldConvertAValidNavigationSampleToMillidegrees() {
        // Given
        when(bus.readRegister16(device, 0x1A)).thenReturn(90 * 16);
        when(bus.readRegister16(device, 0x18)).thenReturn(16);
        when(bus.readRegister16(device, 0x1E)).thenReturn(0xFFF0);
        when(bus.readRegister16(device, 0x1C)).thenReturn(8);
        when(bus.readRegister(device, 0x35)).thenReturn(0xFF);
        ImuSample sample = new ImuSample();

        // When
        boolean read = imu.read(sample);

        // Then
        assertThat(read).isTrue();
        assertThat(new int[] {
                sample.headingMillidegrees(),
                sample.yawRateMillidegreesPerSecond(),
                sample.pitchMillidegrees(),
                sample.rollMillidegrees(),
                sample.calibration()})
                .containsExactly(90_000, 1_000, -1_000, 500, 0xFF);
    }
}
