package io.github.jabrena.juno.api.thirdparty.servo;

import io.github.jabrena.juno.api.hardware.internal.NativeI2c;
import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("PWM servo shield")
class PwmServoShieldTest {
    private I2c bus;
    private PwmServoShield shield;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        bus = mock(I2c.class);
        shield = new PwmServoShield(bus);
        shield.begin();
    }

    @Test
    @DisplayName("Begin opens the I2C bus once")
    void shouldOpenTheI2cBusOnce() {
        // When & Then
        verify(bus, times(1)).begin();
    }

    @Test
    @DisplayName("Begin sleeps the oscillator while it sets the 50 Hz prescaler")
    void shouldSleepTheOscillatorWhileSettingTheFiftyHertzPrescaler() {
        // When & Then
        InOrder order = inOrder(bus);
        order.verify(bus).writeRegister(0x40, 0x00, 0x10);
        order.verify(bus).writeRegister(0x40, 0xFE, 121);
        order.verify(bus).writeRegister(0x40, 0x00, 0x00);
    }

    @Test
    @DisplayName("Write maps an angle to its pulse width ticks")
    void shouldMapAnAngleToItsPulseWidthTicks() {
        // Given
        clearInvocations(bus);

        // When
        shield.write(0, 90);

        // Then
        InOrder order = inOrder(bus);
        order.verify(bus).writeRegister(0x40, 0x06, 0x00);
        order.verify(bus).writeRegister(0x40, 0x07, 0x00);
        order.verify(bus).writeRegister(0x40, 0x08, 301 & 0xFF);
        order.verify(bus).writeRegister(0x40, 0x09, 301 >> 8);
        verifyNoMoreInteractions(bus);
    }

    @Test
    @DisplayName("Angles above the servo range are clamped")
    void shouldClampAnglesAboveTheServoRange() {
        // Given
        clearInvocations(bus);
        shield.write(0, 200);
        int clampedHigh = offTickLow(0x08);
        clearInvocations(bus);

        // When
        shield.write(0, 180);

        // Then
        assertThat(offTickLow(0x08)).isEqualTo(clampedHigh);
    }

    @Test
    @DisplayName("Write micros uses the channel registers and clamps to the frame")
    void shouldUseTheChannelRegistersAndClampToTheFrameForWriteMicros() {
        // Given
        clearInvocations(bus);

        // When
        shield.writeMicros(1, 30_000);

        // Then
        InOrder order = inOrder(bus);
        order.verify(bus).writeRegister(0x40, 0x0A, 0x00);
        order.verify(bus).writeRegister(0x40, 0x0B, 0x00);
        order.verify(bus).writeRegister(0x40, 0x0C, 0xFF);
        order.verify(bus).writeRegister(0x40, 0x0D, 0x0F);
        verifyNoMoreInteractions(bus);
    }

    @Test
    @DisplayName("Release turns the channel fully off")
    void shouldTurnTheChannelFullyOffWhenReleased() {
        // Given
        clearInvocations(bus);

        // When
        shield.release(2);

        // Then
        InOrder order = inOrder(bus);
        order.verify(bus).writeRegister(0x40, 0x0E, 0x00);
        order.verify(bus).writeRegister(0x40, 0x0F, 0x00);
        order.verify(bus).writeRegister(0x40, 0x10, 0x00);
        order.verify(bus).writeRegister(0x40, 0x11, 0x10);
        verifyNoMoreInteractions(bus);
    }

    @Test
    @DisplayName("Begin can use an alternate address")
    void shouldUseAnAlternateAddressWhenBegunWithOne() {
        // Given
        clearInvocations(bus);

        // When
        shield.begin(0x41);

        // Then
        verify(bus).writeRegister(0x41, 0x00, 0x10);
    }

    @Test
    @DisplayName("A shield created without a bus talks over the board's I2C bus")
    void shouldTalkOverTheBoardsBusByDefault() {
        // Given
        I2c board = mock(I2c.class);
        NativeI2c.setI2c(board);

        // When
        try {
            new PwmServoShield().begin();
        } finally {
            NativeI2c.setI2c(NativeI2c.INSTANCE);
        }

        // Then
        verify(board).begin();
    }

    /** The value last written to {@code register}, the low byte of a channel's off tick. */
    private int offTickLow(int register) {
        ArgumentCaptor<Integer> value = ArgumentCaptor.forClass(Integer.class);
        verify(bus).writeRegister(anyInt(), eq(register), value.capture());
        return value.getValue();
    }
}
