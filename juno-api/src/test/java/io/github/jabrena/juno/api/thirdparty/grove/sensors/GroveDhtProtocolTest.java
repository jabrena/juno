package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.testing.Wire;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@DisplayName("Grove DHT single-wire protocol")
class GroveDhtProtocolTest {

    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
    }

    @Test
    @DisplayName("Reads every byte of a checksum-valid frame")
    void shouldReadEveryByteOfAChecksumValidFrame() {
        // Given
        Wire.dhtReply(0x00, 0xFF, 0x00, 0xFF, 0xFE).install();
        int[] frame = new int[5];

        // When
        boolean read = GroveDhtProtocol.read(2, frame, frame.length);

        // Then
        assertThat(read).isTrue();
        assertThat(frame).containsExactly(0x00, 0xFF, 0x00, 0xFF, 0xFE);
    }

    @Test
    @DisplayName("Rejects a frame shorter than the DHT frame")
    void shouldRejectAFrameShorterThanTheDhtFrame() {
        // When & Then
        assertThatThrownBy(() -> GroveDhtProtocol.read(2, new int[5], 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Gives up when the sensor does not answer the start pulse")
    void shouldGiveUpWhenTheSensorDoesNotAnswerTheStartPulse() {
        // Given
        new Wire().hold(true, 20_040).hold(true, 400).install();

        // When
        boolean read = GroveDhtProtocol.read(2, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("Gives up when a bit never goes high")
    void shouldGiveUpWhenABitNeverGoesHigh() {
        // Given
        new Wire().hold(true, 20_040).hold(false, 80).hold(true, 80).hold(false, 5_000).install();

        // When
        boolean read = GroveDhtProtocol.read(2, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("Gives up when a bit never goes low")
    void shouldGiveUpWhenABitNeverGoesLow() {
        // Given
        new Wire().hold(true, 20_040).hold(false, 80).hold(true, 80).hold(false, 50).hold(true, 5_000).install();

        // When
        boolean read = GroveDhtProtocol.read(2, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }
}
