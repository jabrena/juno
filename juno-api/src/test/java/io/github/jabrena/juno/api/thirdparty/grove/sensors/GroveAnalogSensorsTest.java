package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Grove analog sensors")
class GroveAnalogSensorsTest {

    @BeforeEach
    void startWithAQuietBoard() {
        Wire.resetBoard();
        Gpio.analogValues[Gpio.A0] = 321;
    }

    @Test
    @DisplayName("Gas, sound and light sensors report their analog pin")
    void shouldReportTheAnalogPinValueFromTheGasSoundAndLightSensors() {
        // Given
        int[] readings;

        // When
        readings = new int[] {
                GroveAnalogGas.rawValue(Gpio.A0),
                GroveSoundSensorV16.rawValue(Gpio.A0),
                GroveLightSensorV12.rawValue(Gpio.A0)};

        // Then
        assertThat(readings).containsOnly(321);
    }
}
