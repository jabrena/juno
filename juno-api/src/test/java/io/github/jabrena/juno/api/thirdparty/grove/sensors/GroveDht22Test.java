package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroveDht22Test {
    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
    }

    @Test
    void readsAChecksumValidFrameFromTheWire() {
        Wire.dhtReply(2, 120, 0, 242, 108).install();
        int[] reading = new int[2];
        int[] frame = new int[5];

        assertTrue(GroveDht22.read(Gpio.D2, reading, reading.length, frame, frame.length));
        assertArrayEquals(new int[] {242, 632}, reading);
    }

    @Test
    void keepsThePreviousReadingWhenTheSensorStaysSilent() {
        new Wire().hold(true, 20_040).hold(true, 400).install();
        int[] reading = {42, 44};
        int[] frame = new int[5];

        assertFalse(GroveDht22.read(Gpio.D2, reading, reading.length, frame, frame.length));
        assertArrayEquals(new int[] {42, 44}, reading);
    }

    @Test
    void rejectsHumidityAboveOneHundredPercent() {
        int[] frame = {3, 233, 0, 0, 236};
        int[] reading = {42, 44};

        assertFalse(GroveDht22.decode(frame, frame.length, reading, reading.length));
        assertArrayEquals(new int[] {42, 44}, reading);
    }

    @Test
    void rejectsTemperaturesOutsideTheSensorRange() {
        int[] tooHot = {0, 0, 3, 33, 36};
        int[] tooCold = {0, 0, 0x81, 0x91, 18};
        int[] reading = new int[2];

        assertFalse(GroveDht22.decode(tooHot, tooHot.length, reading, reading.length));
        assertFalse(GroveDht22.decode(tooCold, tooCold.length, reading, reading.length));
    }

    @Test
    void rejectsAFrameTooShortForItsFields() {
        int[] reading = new int[2];

        assertThrows(IllegalArgumentException.class,
                () -> GroveDht22.decode(new int[5], 4, reading, reading.length));
    }

    @Test
    void exposesTheDht22ResultLayout() {
        assertEquals(5, GroveDht22.FRAME_LENGTH);
        assertEquals(2, GroveDht22.READING_LENGTH);
    }

    @Test
    void rejectsBuffersThatAreTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> GroveDht22.read(2, new int[1], 1, new int[5], 5));
        assertThrows(IllegalArgumentException.class,
                () -> GroveDht22.read(2, new int[2], 2, new int[4], 4));
    }

    @Test
    void decodesTheFrameObservedOnTheConnectedBoard() {
        int[] frame = {2, 120, 0, 242, 108};
        int[] reading = new int[2];

        assertTrue(GroveDht22.decode(frame, frame.length, reading, reading.length));
        assertArrayEquals(new int[] {242, 632}, reading);
    }

    @Test
    void decodesNegativeTemperature() {
        int[] frame = {1, 194, 128, 55, 122};
        int[] reading = new int[2];

        assertTrue(GroveDht22.decode(frame, frame.length, reading, reading.length));
        assertArrayEquals(new int[] {-55, 450}, reading);
    }

    @Test
    void rejectsCorruptAndOutOfRangeFramesWithoutChangingTheResult() {
        int[] reading = {42, 44};
        int[] corrupt = {2, 120, 0, 242, 109};
        int[] outOfRange = {4, 0, 0, 242, 246};

        assertFalse(GroveDht22.decode(corrupt, corrupt.length, reading, reading.length));
        assertFalse(GroveDht22.decode(outOfRange, outOfRange.length, reading, reading.length));
        assertArrayEquals(new int[] {42, 44}, reading);
    }
}
