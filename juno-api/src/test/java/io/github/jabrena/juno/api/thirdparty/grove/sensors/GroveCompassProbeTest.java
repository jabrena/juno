package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroveCompassProbeTest {
    @Test
    void decodesSignedBmm150AxisValues() {
        assertEquals(123, GroveCompassProbe.decodeAxis(123 << 3, 13, 3));
        assertEquals(-123, GroveCompassProbe.decodeAxis((0x2000 - 123) << 3, 13, 3));
        assertEquals(456, GroveCompassProbe.decodeAxis(456 << 1, 15, 1));
        assertEquals(-456, GroveCompassProbe.decodeAxis((0x8000 - 456) << 1, 15, 1));
    }
}
