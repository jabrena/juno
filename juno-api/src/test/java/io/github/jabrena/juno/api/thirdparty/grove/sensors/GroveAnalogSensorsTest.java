package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroveAnalogSensorsTest {

    @BeforeEach
    void startWithAQuietBoard() {
        Wire.resetBoard();
        Gpio.analogValues[Gpio.A0] = 321;
    }

    @Test
    void theGasSoundAndLightSensorsReportTheirAnalogPin() {
        assertThat(GroveAnalogGas.rawValue(Gpio.A0)).isEqualTo(321);
        assertThat(GroveSoundSensorV16.rawValue(Gpio.A0)).isEqualTo(321);
        assertThat(GroveLightSensorV12.rawValue(Gpio.A0)).isEqualTo(321);
    }
}
