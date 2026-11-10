package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Grove BMI088")
class Bmi088Test {
    private I2c i2c;
    private Clock clock;
    private Bmi088 imu;

    @BeforeEach
    void connectTheImuToAMockedI2cBus() {
        i2c = mock(I2c.class);
        clock = mock(Clock.class);
        when(i2c.readRegister(0x19, 0)).thenReturn(0x1E);
        when(i2c.readRegister(0x69, 0)).thenReturn(0x0F);
        imu = new Bmi088(i2c, clock);
    }

    @Test
    @DisplayName("Construction verifies both chips and configures their measurement ranges")
    void shouldConfigureBothChips() {
        verify(i2c).writeRegister(0x19, 0x7D, 0x04);
        verify(i2c).begin();
        verify(i2c).writeRegister(0x19, 0x40, 0xA8);
        verify(i2c).writeRegister(0x69, 0x0F, 0x00);
    }

    @Test
    @DisplayName("Fetches acceleration in standard metres-per-second-squared units")
    void shouldFetchAcceleration() {
        when(i2c.readRegister16(0x19, 0x12)).thenReturn(16_384);
        when(i2c.readRegister16(0x19, 0x14)).thenReturn(0);
        when(i2c.readRegister16(0x19, 0x16)).thenReturn(0xC000);
        float[] sample = new float[3];

        imu.fetchSample(sample, 0);

        assertThat(sample[0]).isCloseTo(14.70f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(sample[1]).isZero();
        assertThat(sample[2]).isCloseTo(-14.70f, org.assertj.core.data.Offset.offset(0.01f));
    }

    @Test
    @DisplayName("Returns false when either BMI088 chip does not identify itself")
    void shouldRejectAnUnidentifiedChip() {
        when(i2c.readRegister(0x69, 0)).thenReturn(-1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new Bmi088(i2c, clock))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Integrates its clockwise gyro rate after construction")
    void shouldIntegrateTheGyroscopeAfterBegin() {
        when(clock.millis()).thenReturn(0, 100);
        when(i2c.readRegister16(0x69, 0x06)).thenReturn(256);

        assertThat(imu.getAngularVelocity()).isCloseTo(-15.625f, org.assertj.core.data.Offset.offset(0.001f));
    }

    @Test
    @DisplayName("Can recalibrate and reset a started IMU")
    void shouldRecalibrateAndReset() {
        when(i2c.readRegister16(0x69, 0x06)).thenReturn(10);
        imu.recalibrateOffset();
        imu.reset();

        assertThat(imu.getAngle()).isZero();
    }

    @Test
    void shouldStartTheFifoAt100HertzWithTheZAxisOnly() {
        imu.startBuffering();

        // 100 Hz with a 32 Hz filter, then stream mode with the Z axis only, which also empties the FIFO
        verify(i2c).writeRegister(0x69, 0x10, 0x07);
        verify(i2c).writeRegister(0x69, 0x3E, 0x83);
        assertThat(imu.samplePeriodMillis()).isEqualTo(10);
    }

    @Test
    void shouldAddEveryBufferedZRateAsClockwiseRotation() {
        // Given: three frames, two counter-clockwise at 1000 LSB and one at zero
        when(i2c.readRegister(0x69, 0x0E)).thenReturn(3);
        when(i2c.readRegister16(0x69, 0x3F)).thenReturn(1000, 1000, 0);

        // When
        int rotation = imu.readBufferedRotation();

        // Then: 2000 LSB of 0.061 degrees per second for 10 ms, counter-clockwise, is -1220 thousandths clockwise
        assertThat(imu.bufferedSamples()).isEqualTo(3);
        assertThat(rotation).isEqualTo(-1220);
    }

    @Test
    void shouldReadNothingWhenTheFifoStatusFails() {
        when(i2c.readRegister(0x69, 0x0E)).thenReturn(-1);

        assertThat(imu.readBufferedRotation()).isZero();
        assertThat(imu.bufferedSamples()).isZero();
    }
}
