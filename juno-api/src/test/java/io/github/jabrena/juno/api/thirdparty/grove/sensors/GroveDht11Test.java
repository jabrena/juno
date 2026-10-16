package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroveDht11Test {
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
}
