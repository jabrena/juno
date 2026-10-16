package io.github.jabrena.juno.api.thirdparty.grove;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroveBaseShieldTest {
    @Test
    void mapsTheGroveSocketsToUnoPins() {
        assertEquals(14, GroveBaseShield.A0);
        assertEquals(2, GroveBaseShield.D2);
        assertEquals(8, GroveBaseShield.D8);
        assertEquals(0, GroveBaseShield.UART_RX);
        assertEquals(19, GroveBaseShield.I2C_SCL);
    }
}
