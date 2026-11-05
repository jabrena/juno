package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.testing.ImmediateDelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("LSM6DS3TR inertial sensor over SPI")
class Lsm6ds3Test {
    private static final int CHIP_SELECT = 30;
    private static final int WHO_AM_I = 0x0F;
    private static final int OUTX_L_G = 0x22;

    private final int[] registers = new int[256];
    private final Clock clock = mock(Clock.class);
    private Lsm6ds3 imu;

    /** A chip with a register file: a transaction starts when chip select goes low, then the first byte is the address. */
    private final Gpio chipSelect = new Gpio() {
        @Override
        public void pinMode(int pin, int mode) {
        }

        @Override
        public void digitalWrite(int pin, boolean high) {
            if (pin == CHIP_SELECT && !high) {
                address = -1;
            }
        }

        @Override
        public boolean digitalRead(int pin) {
            return false;
        }

        @Override
        public int analogRead(int pin) {
            return 0;
        }

        @Override
        public void analogWrite(int pin, int value) {
        }

        @Override
        public void toggle(int pin) {
        }

        @Override
        public int getBuiltinLed() {
            return 0;
        }
    };
    private int address;
    private boolean reading;

    private final Spi spi = new Spi() {
        @Override
        public void begin() {
        }

        @Override
        public int transfer(int value) {
            if (address < 0) {
                reading = (value & 0x80) != 0;
                address = value & 0x7F;
                return 0;
            }
            int reply = reading ? registers[address] : 0;
            if (!reading) {
                registers[address] = value;
            }
            address = (address + 1) & 0xFF;
            return reply;
        }
    };

    @BeforeEach
    void startWithAChipThatAnswersItsId() {
        registers[WHO_AM_I] = 0x69;
        imu = new Lsm6ds3(chipSelect, spi, clock);
    }

    @Test
    @DisplayName("Begin fails when the chip does not answer with its ID")
    void shouldFailWhenTheIdIsWrong() {
        // Given
        registers[WHO_AM_I] = 0x00;

        // When
        boolean started = ImmediateDelay.get(imu::begin);

        // Then
        assertThat(started).isFalse();
        assertThat(imu.getAngle()).isZero();
    }

    @Test
    @DisplayName("Begin measures the gyroscope bias, so a resting sensor reads zero yaw")
    void shouldCalibrateTheBiasSoARestingSensorReadsZeroYaw() {
        // Given
        registers[OUTX_L_G + 4] = 0x40;
        registers[OUTX_L_G + 5] = 0x00;
        when(clock.millis()).thenReturn(0, 0);

        // When
        boolean started = ImmediateDelay.get(imu::begin);

        // Then
        assertThat(started).isTrue();
        assertThat(imu.getAngularVelocity()).isZero();
        assertThat(imu.getAngle()).isZero();
    }

    @Test
    @DisplayName("A yaw rate integrates into the heading, clockwise positive")
    void shouldIntegrateYawIntoHeading() {
        // Given: the gyroscope's Z axis points up, so a positive raw value is anticlockwise
        ImmediateDelay.get(imu::begin);
        registers[OUTX_L_G + 4] = 100;
        registers[OUTX_L_G + 5] = 0;
        when(clock.millis()).thenReturn(1_000);

        // When
        int angle = imu.getAngle();

        // Then: 100 LSB of 35 mdps is -3500 mdps (clockwise), held for 1 s, so the heading is 356 degrees
        assertThat(angle).isEqualTo(356);
        assertThat(imu.getAngularVelocity()).isEqualTo(-3.5f);
    }

    @Test
    @DisplayName("The accelerometer reports whole metres per second squared on each axis")
    void shouldReportAccelerationInWholeMetresPerSecondSquared() {
        // Given: 16384 LSB on Z is about 1 g at +/-2 g
        ImmediateDelay.run(imu::begin);
        registers[OUTX_L_G + 10] = 0x00;
        registers[OUTX_L_G + 11] = 0x40;

        // When & Then
        assertThat(imu.getXAccel()).isZero();
        assertThat(imu.getYAccel()).isZero();
        assertThat(imu.getZAccel()).isEqualTo(9);
    }

    @Test
    @DisplayName("The accelerometer and gyroscope read zero before begin has succeeded")
    void shouldReadZeroBeforeBegin() {
        // When & Then
        assertThat(imu.getZAccel()).isZero();
        assertThat(imu.getAngle()).isZero();
        assertThat(imu.getAngularVelocity()).isZero();
    }

    @Test
    @DisplayName("The gyroscope heading integrates the yaw rate and resets to zero")
    void shouldIntegrateTheHeadingAndResetIt() {
        // Given: 100 LSB of 35 mdps clockwise-negative is -3500 mdps, held for 1 s
        ImmediateDelay.run(imu::begin);
        registers[OUTX_L_G + 4] = 100;
        registers[OUTX_L_G + 5] = 0;
        when(clock.millis()).thenReturn(1_000);

        // When
        int angle = imu.getAngle();
        imu.reset();
        int afterReset = imu.getAngle();

        // Then
        assertThat(angle).isEqualTo(356);
        assertThat(afterReset).isZero();
    }

    @Test
    @DisplayName("The gyroscope reports the yaw rate in degrees per second, clockwise positive")
    void shouldReportTheYawRateInDegreesPerSecond() {
        // Given
        ImmediateDelay.run(imu::begin);
        registers[OUTX_L_G + 4] = 100;
        registers[OUTX_L_G + 5] = 0;
        when(clock.millis()).thenReturn(1_000);

        // When & Then
        assertThat(imu.getAngularVelocity()).isEqualTo(-3.5f);
    }

    @Test
    @DisplayName("Recalibrating the gyroscope measures its bias again once begin has succeeded")
    void shouldMeasureTheBiasAgainAfterBegin() {
        // Given
        ImmediateDelay.run(imu::begin);

        // When
        ImmediateDelay.run(imu::recalibrateOffset);

        // Then: the sensor still reads a stationary heading
        assertThat(imu.getAngle()).isZero();
    }

    @Test
    @DisplayName("Recalibrating before begin does nothing")
    void shouldNotRecalibrateBeforeBegin() {
        // When
        ImmediateDelay.run(imu::recalibrateOffset);

        // Then
        assertThat(imu.getAngle()).isZero();
    }

    @Test
    @DisplayName("Fetches the acceleration in metres per second squared as a three-element sample")
    void shouldFetchTheAccelerationAsASample() {
        // Given: 16384 LSB on Z is about 9.8 m/s² at +/-2 g
        ImmediateDelay.run(imu::begin);
        registers[OUTX_L_G + 10] = 0x00;
        registers[OUTX_L_G + 11] = 0x40;
        float[] sample = new float[4];

        // When
        imu.fetchSample(sample, 1);

        // Then
        assertThat(imu.sampleSize()).isEqualTo(3);
        assertThat(sample[1]).isZero();
        assertThat(sample[2]).isZero();
        assertThat(sample[3]).isCloseTo(9.797f, within(0.01f));
    }

    @Test
    @DisplayName("Fetches zeros before begin has succeeded")
    void shouldFetchZerosBeforeBegin() {
        // Given
        float[] sample = {1f, 1f, 1f};

        // When
        imu.fetchSample(sample, 0);

        // Then
        assertThat(sample).containsExactly(0f, 0f, 0f);
    }
}
