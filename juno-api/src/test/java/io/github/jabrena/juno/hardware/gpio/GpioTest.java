package io.github.jabrena.juno.hardware.gpio;

import io.github.jabrena.juno.hardware.MCU;
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
        assertThat(MCU.getGpio()).isSameAs(MCU.getGpio()).isSameAs(NativeGpio.INSTANCE);
    }

    @Test
    @DisplayName("The Cardputer exposes the board's GPIO context")
    void shouldExposeGpioForCardputer() {
        // When & Then
        assertThat(MCU.getGpio()).isSameAs(NativeGpio.INSTANCE);
    }

}
