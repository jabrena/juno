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

    @BeforeEach
    void wireTheSensorsToAMockedA0() {
        when(pins.analogRead(Gpio.getA0())).thenReturn(321);
    }

    @Test
    @DisplayName("Gas, sound and light sensors report their analog pin")
    void shouldReportTheAnalogPinValueFromTheGasSoundAndLightSensors() {
        // When
        int[] readings = {
                new GroveAnalogGas(pins).rawValue(Gpio.getA0()),
                new GroveSoundSensorV16(pins).rawValue(Gpio.getA0()),
                new GroveLightSensorV12(pins).rawValue(Gpio.getA0())};

        // Then
        assertThat(readings).containsOnly(321);
        verify(pins, times(3)).analogRead(Gpio.getA0());
    }
}
