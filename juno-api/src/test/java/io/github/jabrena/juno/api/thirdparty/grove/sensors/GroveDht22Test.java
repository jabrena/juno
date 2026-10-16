package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroveDht22Test {
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
