package io.github.jabrena.juno.api.thirdparty.grove;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroveBaseShieldTest {
    @Test
    void mapsTheGroveSocketsToUnoPins() {
        assertEquals(14, GroveBaseShield.A0);
        assertEquals(2, GroveBaseShield.D2);
        assertEquals(8, GroveBaseShield.D8);
        assertEquals(0, GroveBaseShield.UART_RX);
        assertEquals(19, GroveBaseShield.I2C_SCL);
    }

    @Test
    void exposesDht11ResultSlots() {
        assertEquals(0, GroveDht11.TEMPERATURE_CELSIUS);
        assertEquals(1, GroveDht11.HUMIDITY_PERCENT);
        assertEquals(2, GroveDht11.READING_LENGTH);
    }

    @Test
    void rejectsAResultBufferThatCannotHoldBothValues() {
        assertThrows(IllegalArgumentException.class,
                () -> GroveDht11.read(GroveBaseShield.D4, new int[1], 1));
    }
}
