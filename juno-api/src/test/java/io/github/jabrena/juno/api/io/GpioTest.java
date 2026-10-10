package io.github.jabrena.juno.api.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GpioTest {

    @Test
    void digitalPinsUseUnoHeaderNumbers() {
        assertEquals(0, Gpio.D0);
        assertEquals(7, Gpio.D7);
        assertEquals(13, Gpio.D13);
    }

    @Test
    void analogPinsFollowTheLastDigitalPin() {
        assertEquals(Gpio.D13 + 1, Gpio.A0);
        assertEquals(19, Gpio.A5);
    }
}
