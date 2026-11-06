package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Grove analog sensors")
class GroveAnalogSensorsTest {
    private final Gpio pins = mock(Gpio.class);
    private int analogPin;

    @BeforeEach
    void wireTheSensorsToAMockedA0() {
        when(pins.analogPin("A0")).thenReturn(14);
        analogPin = pins.analogPin("A0");
        when(pins.analogRead(analogPin)).thenReturn(321);
    }

    @Test
    @DisplayName("Gas and sound sensors report their analog pin")
    void shouldReportTheAnalogPinValueFromTheGasAndSoundSensors() {
        // When
        int[] readings = {
                new GroveAnalogGas(pins).rawValue(analogPin),
                new GroveSoundV16(pins).rawValue(analogPin)};

        // Then
        assertThat(readings).containsOnly(321);
        verify(pins, times(2)).analogRead(analogPin);
    }
}
