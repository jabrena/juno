package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.testing.ImmediateDelay;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BNO055 nine-axis motion shield")
class NineAxisMotionShieldTest {
    private static final int DEVICE = 0x28;
    private static final int CHIP_ID = 0x00;
    private static final int EULER_HEADING = 0x1A;
    private static final int GYRO_Z = 0x18;
    private static final int ACC_X = 0x08;
    private static final int ACC_Y = 0x0A;

    private Gpio pins;
    private I2c bus;
    private NineAxisMotionShield imu;
    private int resetPin;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        pins = mock(Gpio.class);
        bus = mock(I2c.class);
        when(pins.digitalPin("D7")).thenReturn(7);
        resetPin = pins.digitalPin("D7");
        // A register a test does not set answers -1, like a device that does not acknowledge.
        when(bus.readRegister(anyInt(), anyInt())).thenReturn(-1);
        when(bus.readRegister16(anyInt(), anyInt())).thenReturn(-1);
        imu = new NineAxisMotionShield(pins, bus);
    }

    @Test
    @DisplayName("Begin pulses D7 low then high to release the BNO055 from reset")
    void shouldPulseTheResetLineBeforeTalkingToTheSensor() {
        // Given
        when(bus.readRegister(DEVICE, CHIP_ID)).thenReturn(0xA0);

        // When
        ImmediateDelay.run(imu::begin);

        // Then
        InOrder order = inOrder(pins);
        order.verify(pins).pinMode(resetPin, pins.getOutput());
        order.verify(pins).digitalWrite(resetPin, false);
        order.verify(pins).digitalWrite(resetPin, true);
    }

    @Test
    @DisplayName("Begin reports success when the chip ID matches")
    void shouldReportSuccessWhenTheChipIdMatches() {
        // Given
        when(bus.readRegister(DEVICE, CHIP_ID)).thenReturn(0xA0);

        // When
        boolean begun = ImmediateDelay.get(imu::begin);

        // Then
        assertThat(begun).isTrue();
    }

    @Test
    @DisplayName("Begin resets the sensor and switches it to Android IMU mode")
    void shouldResetTheSensorAndSwitchItToAndroidImuMode() {
        // Given
        when(bus.readRegister(DEVICE, CHIP_ID)).thenReturn(0xA0);

        // When
        ImmediateDelay.run(imu::begin);

        // Then
        InOrder order = inOrder(bus);
        order.verify(bus).writeRegister(DEVICE, 0x3D, 0x00);
        order.verify(bus).writeRegister(DEVICE, 0x3B, 0x80);
        order.verify(bus).writeRegister(DEVICE, 0x3E, 0x00);
        order.verify(bus).writeRegister(DEVICE, 0x3D, 0x08);
    }

    @Test
    @DisplayName("Begin reports failure when the chip ID is wrong")
    void shouldReportFailureWhenTheChipIdIsWrong() {
        // Given
        when(bus.readRegister(DEVICE, CHIP_ID)).thenReturn(0x32);

        // When
        boolean begun = ImmediateDelay.get(imu::begin);

        // Then
        assertThat(begun).isFalse();
    }

    @Test
    @DisplayName("No mode is written after a failed identity check")
    void shouldWriteNoModeAfterAFailedIdentityCheck() {
        // Given
        when(bus.readRegister(DEVICE, CHIP_ID)).thenReturn(0x32);

        // When
        ImmediateDelay.run(imu::begin);

        // Then
        verify(bus, never()).writeRegister(anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("The heading wraps a full turn back to zero degrees")
    void shouldWrapAFullTurnOfHeadingToZeroDegrees() {
        // Given
        when(bus.readRegister16(DEVICE, EULER_HEADING)).thenReturn(360 * 16 + 5);

        // When & Then
        assertThat(imu.getAngle()).isZero();
    }

    @Test
    @DisplayName("The heading counts whole degrees clockwise")
    void shouldReportTheHeadingInWholeDegrees() {
        // Given
        when(bus.readRegister16(DEVICE, EULER_HEADING)).thenReturn(90 * 16);

        // When & Then
        assertThat(imu.getAngle()).isEqualTo(90);
    }

    @Test
    @DisplayName("The heading reads -1 when the sensor does not answer")
    void shouldReadAMissingHeadingAsMinusOne() {
        // Then: the default stub answers -1 for every register
        assertThat(imu.getAngle()).isEqualTo(-1);
    }

    @Test
    @DisplayName("The heading counts from the last reset")
    void shouldCountTheHeadingFromTheLastReset() {
        // Given
        when(bus.readRegister16(DEVICE, EULER_HEADING)).thenReturn(90 * 16);
        imu.reset();
        when(bus.readRegister16(DEVICE, EULER_HEADING)).thenReturn(100 * 16);

        // When & Then
        assertThat(imu.getAngle()).isEqualTo(10);
    }

    @Test
    @DisplayName("Reset keeps the previous offset when the heading read fails")
    void shouldKeepTheOffsetWhenTheHeadingReadFails() {
        // Given: the heading read fails, so reset does nothing
        imu.reset();
        assertThat(imu.getAngle()).isEqualTo(-1);

        // When
        when(bus.readRegister16(DEVICE, EULER_HEADING)).thenReturn(90 * 16);

        // Then
        assertThat(imu.getAngle()).isEqualTo(90);
    }

    @Test
    @DisplayName("The accelerometer reports whole metres per second squared, signed")
    void shouldReportAccelerationInWholeMetresPerSecondSquared() {
        // Given: 100 LSB per m/s²; 250 is +2 and 0xFF38 (-200) is -2
        when(bus.readRegister16(DEVICE, ACC_X)).thenReturn(250);
        when(bus.readRegister16(DEVICE, ACC_Y)).thenReturn(0xFF38);

        // When & Then
        assertThat(imu.getXAccel()).isEqualTo(2);
        assertThat(imu.getYAccel()).isEqualTo(-2);
    }

    @Test
    @DisplayName("The accelerometer reads zero when the sensor does not answer")
    void shouldReadZeroAccelerationWhenTheReadFails() {
        // Then
        assertThat(imu.getZAccel()).isZero();
    }

    @Test
    @DisplayName("The yaw rate in degrees per second is clockwise positive")
    void shouldReportTheYawRateClockwisePositive() {
        // Given: a raw +16 is one degree per second anticlockwise
        when(bus.readRegister16(DEVICE, GYRO_Z)).thenReturn(16);

        // When & Then
        assertThat(imu.getAngularVelocity()).isEqualTo(-1f);
    }

    @Test
    @DisplayName("The yaw rate is NaN when the read fails")
    void shouldReportNaNWhenTheYawRateReadFails() {
        // Then
        assertThat(imu.getAngularVelocity()).isNaN();
    }

    @Test
    @DisplayName("Recalibrating the gyroscope needs no action on the BNO055")
    void shouldLeaveTheGyroscopeBiasToTheSensor() {
        // When & Then
        assertThatCode(imu::recalibrateOffset).doesNotThrowAnyException();
    }


    @Test
    @DisplayName("Fetches the acceleration of each axis in metres per second squared")
    void shouldFetchTheAccelerationAsASample() {
        // Given: 250 is +2.5 m/s² and 0xFF38 is -2 m/s²; the Z read fails and is written as 0
        when(bus.readRegister16(DEVICE, ACC_X)).thenReturn(250);
        when(bus.readRegister16(DEVICE, ACC_Y)).thenReturn(0xFF38);
        float[] sample = new float[3];

        // When
        imu.fetchSample(sample, 0);

        // Then
        assertThat(imu.sampleSize()).isEqualTo(3);
        assertThat(sample).containsExactly(2.5f, -2.0f, 0f);
    }
}
