package io.github.jabrena.juno.hardware.device.grove;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Grove Shield for Pi Pico socket mapping")
class GrovePicoShieldTest {

    @Test
    @DisplayName("Maps the Grove sockets to Pico GPIO pins")
    void shouldMapTheGroveSocketsToPicoPins() {
        // Given
        GrovePicoShield shield = new GrovePicoShield(pins());

        // When & Then
        assertThat(new int[] {
                shield.getA0(), shield.getA1(), shield.getA2(),
                shield.getD16(), shield.getD18(), shield.getD20(),
                shield.getI2c0Sda(), shield.getI2c0Scl(), shield.getI2c1Sda(), shield.getI2c1Scl(),
                shield.getUart0Rx(), shield.getUart0Tx(), shield.getUart1Rx(), shield.getUart1Tx(),
                shield.getSpi0Miso(), shield.getSpi0Mosi(), shield.getSpi0Sck(), shield.getSpi0Cs()})
            .containsExactly(26, 27, 28, 16, 18, 20, 8, 9, 6, 7, 1, 0, 5, 4, 16, 19, 18, 17);
    }

    /** The Pico 2 W's resolver: {@code GPn} is GPIO n and {@code A0..A2} are the ADC pins GP26..GP28. */
    private static Gpio pins() {
        Gpio pins = mock(Gpio.class);
        when(pins.digitalPin(anyString())).thenAnswer(invocation -> Integer.parseInt(
                invocation.<String>getArgument(0).substring(2)));
        when(pins.analogPin(anyString())).thenAnswer(invocation -> 26 + Integer.parseInt(
                invocation.<String>getArgument(0).substring(1)));
        return pins;
    }
}
