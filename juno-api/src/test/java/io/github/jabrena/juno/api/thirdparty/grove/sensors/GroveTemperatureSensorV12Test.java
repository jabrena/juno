package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroveTemperatureSensorV12Test {
    @Test
    void convertsTheObservedAdcCountToRoomTemperature() {
        assertEquals(24.23f, GroveTemperatureSensorV12.celsiusFromRaw(502), 0.02f);
    }

    @Test
    void returnsNanForSaturatedDividerReadings() {
        assertTrue(Float.isNaN(GroveTemperatureSensorV12.celsiusFromRaw(0)));
        assertTrue(Float.isNaN(GroveTemperatureSensorV12.celsiusFromRaw(1023)));
    }
}
