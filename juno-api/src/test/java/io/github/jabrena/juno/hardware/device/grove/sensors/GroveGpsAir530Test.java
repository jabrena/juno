package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("Grove GPS Air530")
class GroveGpsAir530Test {

    @Test
    @DisplayName("Decodes a checksum-valid GGA fix into integer units")
    void shouldDecodeGgaPosition() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        byte[] sentence = "$GPGGA,123519,4807.038,S,01131.000,W,1,08,0.9,545.4,M,46.9,M,,*48".getBytes();
        int[] position = new int[GroveGpsAir530.POSITION_LENGTH];

        boolean valid = gps.parseGga(sentence, sentence.length, position, position.length);

        assertThat(valid).isTrue();
        assertThat(position).containsExactly(-48117300, -11516667, 54540, 8, 1);
    }

    @Test
    @DisplayName("Rejects a bad checksum and preserves the previous position")
    void shouldRejectBadChecksum() {
        GroveGpsAir530 gps = new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), 2);
        byte[] sentence = "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*00".getBytes();
        int[] position = {1, 2, 3, 4, 5};

        assertThat(gps.parseGga(sentence, sentence.length, position, position.length)).isFalse();
        assertThat(position).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Requires a Grove receive pin")
    void shouldRejectNegativeReceivePin() {
        assertThatThrownBy(() -> new GroveGpsAir530(mock(Gpio.class), mock(Clock.class), -1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
