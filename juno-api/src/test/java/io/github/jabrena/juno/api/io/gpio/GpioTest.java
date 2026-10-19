package io.github.jabrena.juno.api.io.gpio;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("Gpio pin constants")
class GpioTest {

    @Test
    @DisplayName("Digital pins use the UNO header numbers")
    void shouldUseUnoHeaderNumbersForDigitalPins() {
        // When & Then
        assertThat(new int[] {Gpio.D0, Gpio.D7, Gpio.D13}).containsExactly(0, 7, 13);
    }

    @Test
    @DisplayName("Analog pins follow the last digital pin")
    void shouldPlaceAnalogPinsAfterTheLastDigitalPin() {
        // When & Then
        assertThat(new int[] {Gpio.A0, Gpio.A5}).containsExactly(Gpio.D13 + 1, 19);
    }
}
