package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroveDht11Test {

    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
    }

    @Test
    void decodesWholeNumberTemperatureAndHumidity() {
        int[] frame = {55, 0, 24, 0, 79};
        int[] reading = new int[2];

        assertTrue(GroveDht11.decode(frame, frame.length, reading, reading.length));
        assertArrayEquals(new int[] {24, 55}, reading);
    }

    @Test
    void rejectsNonDht11FramesWithoutChangingTheResult() {
        int[] reading = {42, 44};
        int[] dht22Frame = {2, 120, 0, 242, 108};

        assertFalse(GroveDht11.decode(dht22Frame, dht22Frame.length, reading, reading.length));
        assertArrayEquals(new int[] {42, 44}, reading);
    }

    @Test
    void readsAChecksumValidFrameFromTheWire() {
        Wire.dhtReply(55, 0, 24, 0, 79).install();
        int[] reading = new int[2];
        int[] frame = new int[5];

        assertTrue(GroveDht11.read(Gpio.D2, reading, reading.length, frame, frame.length));
        assertArrayEquals(new int[] {24, 55}, reading);
        assertArrayEquals(new int[] {55, 0, 24, 0, 79}, frame);
    }

    @Test
    void keepsThePreviousReadingWhenTheChecksumFails() {
        Wire.dhtReply(55, 0, 24, 0, 80).install();
        int[] reading = {42, 44};
        int[] frame = new int[5];

        assertFalse(GroveDht11.read(Gpio.D2, reading, reading.length, frame, frame.length));
        assertArrayEquals(new int[] {42, 44}, reading);
    }

    @Test
    void givesUpWhenTheSensorNeverAnswers() {
        new Wire().hold(true, 20_040).hold(true, 400).install();
        int[] reading = {42, 44};
        int[] frame = new int[5];

        assertFalse(GroveDht11.read(Gpio.D2, reading, reading.length, frame, frame.length));
        assertArrayEquals(new int[] {42, 44}, reading);
    }

    @Test
    void rejectsReadingAndFrameBuffersThatAreTooShort() {
        int[] frame = new int[5];

        assertThrows(IllegalArgumentException.class,
                () -> GroveDht11.read(Gpio.D2, new int[1], 1, frame, frame.length));
        assertThrows(IllegalArgumentException.class,
                () -> GroveDht11.read(Gpio.D2, new int[2], 2, frame, 4));
    }
}
