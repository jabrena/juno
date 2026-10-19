package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Grove temperature sensor v1.2")
class GroveTemperatureSensorV12Test {

    @Test
    @DisplayName("Converts the observed ADC count to room temperature")
    void shouldConvertTheObservedAdcCountToRoomTemperature() {
        // When
        float celsius = GroveTemperatureSensorV12.celsiusFromRaw(502);

        // Then
        assertThat(celsius).isCloseTo(24.23f, within(0.02f));
    }

    @Test
    @DisplayName("Returns NaN when the divider is shorted to ground")
    void shouldReturnNanWhenTheDividerIsShortedToGround() {
        // When
        float celsius = GroveTemperatureSensorV12.celsiusFromRaw(0);

        // Then
        assertThat(celsius).isNaN();
    }

    @Test
    @DisplayName("Returns NaN when the divider is shorted to the supply")
    void shouldReturnNanWhenTheDividerIsShortedToTheSupply() {
        // When
        float celsius = GroveTemperatureSensorV12.celsiusFromRaw(1023);

        // Then
        assertThat(celsius).isNaN();
    }
}
