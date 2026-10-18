package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroveDhtProtocolTest {

    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
    }

    @Test
    void readsEveryByteOfAChecksumValidFrame() {
        Wire.dhtReply(0x00, 0xFF, 0x00, 0xFF, 0xFE).install();
        int[] frame = new int[5];

        assertThat(GroveDhtProtocol.read(2, frame, frame.length)).isTrue();
        assertThat(frame).containsExactly(0x00, 0xFF, 0x00, 0xFF, 0xFE);
    }

    @Test
    void rejectsAFrameThatIsShorterThanTheDhtFrame() {
        assertThatThrownBy(() -> GroveDhtProtocol.read(2, new int[5], 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givesUpWhenTheSensorDoesNotAnswerTheStartPulse() {
        new Wire().hold(true, 20_040).hold(true, 400).install();

        assertThat(GroveDhtProtocol.read(2, new int[5], 5)).isFalse();
    }

    @Test
    void givesUpWhenABitNeverGoesHigh() {
        new Wire().hold(true, 20_040).hold(false, 80).hold(true, 80).hold(false, 5_000).install();

        assertThat(GroveDhtProtocol.read(2, new int[5], 5)).isFalse();
    }

    @Test
    void givesUpWhenABitNeverGoesLow() {
        new Wire().hold(true, 20_040).hold(false, 80).hold(true, 80).hold(false, 50).hold(true, 5_000).install();

        assertThat(GroveDhtProtocol.read(2, new int[5], 5)).isFalse();
    }
}
