package io.github.jabrena.juno.hardware.device.imu;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.imu.ImuSample;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.testing.ImmediateDelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(imu.read(new ImuSample())).isFalse();
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
        ImuSample sample = new ImuSample();
        boolean read = imu.read(sample);

        // Then
        assertThat(started).isTrue();
        assertThat(read).isTrue();
        assertThat(sample.valid()).isTrue();
        assertThat(sample.yawRateMillidegreesPerSecond()).isZero();
        assertThat(sample.headingMillidegrees()).isZero();
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
        ImuSample sample = new ImuSample();
        imu.read(sample);

        // Then: 100 LSB of 35 mdps is -3500 mdps (clockwise), held for 1 s
        assertThat(sample.yawRateMillidegreesPerSecond()).isEqualTo(-3_500);
        assertThat(sample.headingMillidegrees()).isEqualTo(360_000 - 3_500);
    }

    @Test
    @DisplayName("Read reports the accelerometer tilt from gravity")
    void shouldReportTiltFromGravity() {
        // Given: gravity on the Z axis only, so the board is level
        ImmediateDelay.get(imu::begin);
        registers[OUTX_L_G + 10] = 0x00;
        registers[OUTX_L_G + 11] = 0x40;
        when(clock.millis()).thenReturn(1);

        // When
        ImuSample sample = new ImuSample();
        imu.read(sample);

        // Then
        assertThat(sample.rollMillidegrees()).isZero();
        assertThat(sample.pitchMillidegrees()).isZero();
    }

    @Test
    @DisplayName("Read invalidates the sample before begin has succeeded")
    void shouldNotReadBeforeBegin() {
        // Given
        ImuSample sample = new ImuSample();

        // When & Then
        assertThat(imu.read(sample)).isFalse();
        assertThat(sample.valid()).isFalse();
    }
}
