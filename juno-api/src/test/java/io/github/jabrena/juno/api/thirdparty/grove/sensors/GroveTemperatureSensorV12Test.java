package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Grove temperature sensor v1.2")
class GroveTemperatureSensorV12Test {
    private Gpio pins;
    private GroveTemperatureSensorV12 sensor;

    @BeforeEach
    void wireTheThermistorToAMockedPin() {
        pins = mock(Gpio.class);
        sensor = new GroveTemperatureSensorV12(pins);
    }

    @Test
    @DisplayName("Reads the thermistor's analog pin and converts it to degrees Celsius")
    void shouldReadTheAnalogPinAndConvertItToCelsius() {
        // Given
        when(pins.analogRead(Gpio.getA0())).thenReturn(502);

        // When
        float celsius = sensor.celsius(Gpio.getA0());

        // Then
        assertThat(celsius).isCloseTo(24.23f, within(0.02f));
        verify(pins).analogRead(Gpio.getA0());
    }

    @Test
    @DisplayName("Converts the observed ADC count to room temperature")
    void shouldConvertTheObservedAdcCountToRoomTemperature() {
        // When
        float celsius = sensor.celsiusFromRaw(502);

        // Then
        assertThat(celsius).isCloseTo(24.23f, within(0.02f));
    }

    @Test
    @DisplayName("Returns NaN when the divider is shorted to ground")
    void shouldReturnNanWhenTheDividerIsShortedToGround() {
        // When
        float celsius = sensor.celsiusFromRaw(0);

        // Then
        assertThat(celsius).isNaN();
    }

    @Test
    @DisplayName("Returns NaN when the divider is shorted to the supply")
    void shouldReturnNanWhenTheDividerIsShortedToTheSupply() {
        // When
        float celsius = sensor.celsiusFromRaw(1023);

        // Then
        assertThat(celsius).isNaN();
    }
}
