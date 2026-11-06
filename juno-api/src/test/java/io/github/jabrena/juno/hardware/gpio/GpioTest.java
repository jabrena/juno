package io.github.jabrena.juno.hardware.gpio;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gpio")
class GpioTest {
    @Test
    @DisplayName("The board's pins are one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(ArduinoUno.getGpio()).isSameAs(ArduinoUno.getGpio()).isSameAs(NativeGpio.INSTANCE);
    }

    @Test
    @DisplayName("The Cardputer exposes the board's GPIO context")
    void shouldExposeGpioForCardputer() {
        // When & Then
        assertThat(M5StackCardputer.getGpio()).isSameAs(NativeGpio.INSTANCE);
    }

}
