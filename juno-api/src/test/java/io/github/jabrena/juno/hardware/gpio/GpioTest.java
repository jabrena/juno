package io.github.jabrena.juno.hardware.gpio;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Gpio")
class GpioTest {
    @Test
    @DisplayName("Digital pins use the UNO header numbers")
    void shouldUseUnoHeaderNumbersForDigitalPins() {
        // When & Then
        assertThat(new int[] {Gpio.getD0(), Gpio.getD7(), Gpio.getD13()}).containsExactly(0, 7, 13);
    }

    @Test
    @DisplayName("The board's pins are one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(ArduinoOne.getGpio()).isSameAs(ArduinoOne.getGpio()).isSameAs(NativeGpio.INSTANCE);
    }

    @Test
    @DisplayName("Analog pins follow the last digital pin")
    void shouldPlaceAnalogPinsAfterTheLastDigitalPin() {
        // When & Then
        assertThat(new int[] {Gpio.getA0(), Gpio.getA5()}).containsExactly(Gpio.getD13() + 1, 19);
    }
}
