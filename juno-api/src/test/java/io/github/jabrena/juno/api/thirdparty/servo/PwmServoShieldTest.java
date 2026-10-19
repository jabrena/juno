package io.github.jabrena.juno.api.thirdparty.servo;

import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.testing.Wire;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("PWM servo shield")
class PwmServoShieldTest {

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        I2c.reset();
        PwmServoShield.begin();
    }

    @Test
    @DisplayName("Begin opens the I2C bus once")
    void shouldOpenTheI2cBusOnce() {
        // When & Then
        assertThat(I2c.begins).isEqualTo(1);
    }

    @Test
    @DisplayName("Begin sleeps the oscillator while it sets the 50 Hz prescaler")
    void shouldSleepTheOscillatorWhileSettingTheFiftyHertzPrescaler() {
        // When & Then
        assertThat(flat(I2c.writes.subList(0, 3))).containsExactly(
                0x40, 0x00, 0x10,
                0x40, 0xFE, 121,
                0x40, 0x00, 0x00);
    }

    @Test
    @DisplayName("Write maps an angle to its pulse width ticks")
    void shouldMapAnAngleToItsPulseWidthTicks() {
        // Given
        I2c.writes.clear();

        // When
        PwmServoShield.write(0, 90);

        // Then
        assertThat(flat(I2c.writes)).containsExactly(
                0x40, 0x06, 0x00,
                0x40, 0x07, 0x00,
                0x40, 0x08, 301 & 0xFF,
                0x40, 0x09, 301 >> 8);
    }

    @Test
    @DisplayName("Angles above the servo range are clamped")
    void shouldClampAnglesAboveTheServoRange() {
        // Given
        I2c.writes.clear();
        PwmServoShield.write(0, 200);
        int clampedHigh = I2c.writes.get(2)[2];
        I2c.writes.clear();

        // When
        PwmServoShield.write(0, 180);

        // Then
        assertThat(I2c.writes.get(2)[2]).isEqualTo(clampedHigh);
    }

    @Test
    @DisplayName("Write micros uses the channel registers and clamps to the frame")
    void shouldUseTheChannelRegistersAndClampToTheFrameForWriteMicros() {
        // Given
        I2c.writes.clear();

        // When
        PwmServoShield.writeMicros(1, 30_000);

        // Then
        assertThat(flat(I2c.writes)).containsExactly(
                0x40, 0x0A, 0x00,
                0x40, 0x0B, 0x00,
                0x40, 0x0C, 0xFF,
                0x40, 0x0D, 0x0F);
    }

    @Test
    @DisplayName("Release turns the channel fully off")
    void shouldTurnTheChannelFullyOffWhenReleased() {
        // Given
        I2c.writes.clear();

        // When
        PwmServoShield.release(2);

        // Then
        assertThat(flat(I2c.writes)).containsExactly(
                0x40, 0x0E, 0x00,
                0x40, 0x0F, 0x00,
                0x40, 0x10, 0x00,
                0x40, 0x11, 0x10);
    }

    @Test
    @DisplayName("Begin can use an alternate address")
    void shouldUseAnAlternateAddressWhenBegunWithOne() {
        // Given
        I2c.writes.clear();

        // When
        PwmServoShield.begin(0x41);

        // Then
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
