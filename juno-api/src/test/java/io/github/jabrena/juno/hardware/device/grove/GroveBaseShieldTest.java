package io.github.jabrena.juno.hardware.device.grove;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Grove base shield socket mapping")
class GroveBaseShieldTest {

    @Test
    @DisplayName("Maps the Grove sockets to UNO pins")
    void shouldMapTheGroveSocketsToUnoPins() {
        Gpio pins = pins();
        GroveBaseShield shield = new GroveBaseShield(pins);
        // When & Then
        assertThat(new int[] {
                shield.getA0(),
                shield.getD2(),
                shield.getD8(),
                shield.getUartRx(),
                shield.getI2cScl()})
            .containsExactly(
                    pins.analogPin("A0"), pins.digitalPin("D2"), pins.digitalPin("D8"),
                    pins.digitalPin("D0"), pins.analogPin("A5"));
    }

    private static Gpio pins() {
        Gpio pins = mock(Gpio.class);
        when(pins.digitalPin(anyString())).thenAnswer(invocation -> Integer.parseInt(
                invocation.<String>getArgument(0).substring(1)));
        when(pins.analogPin(anyString())).thenAnswer(invocation -> 14 + Integer.parseInt(
                invocation.<String>getArgument(0).substring(1)));
        return pins;
    }
}
