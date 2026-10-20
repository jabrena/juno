package io.github.jabrena.juno.thirdparty.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

@DisplayName("Grove DHT single-wire protocol")
class GroveDhtProtocolTest {
    private Gpio pins;
    private GroveDhtProtocol protocol;

    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
        pins = mock(Gpio.class);
        protocol = new GroveDhtProtocol(pins, SimulatedTime.clock());
    }

    @Test
    @DisplayName("Reads every byte of a checksum-valid frame")
    void shouldReadEveryByteOfAChecksumValidFrame() {
        // Given
        Wire.dhtReply(0x00, 0xFF, 0x00, 0xFF, 0xFE).install(pins);
        int[] frame = new int[5];

        // When
        boolean read = protocol.read(2, frame, frame.length);

        // Then
        assertThat(read).isTrue();
        assertThat(frame).containsExactly(0x00, 0xFF, 0x00, 0xFF, 0xFE);
    }

    @Test
    @DisplayName("Pulls the line low to wake the sensor, then releases it to listen")
    void shouldSendTheStartPulseBeforeListening() {
        // Given
        Wire.dhtReply(0x00, 0xFF, 0x00, 0xFF, 0xFE).install(pins);

        // When
        protocol.read(2, new int[5], 5);

        // Then
        InOrder order = inOrder(pins);
        order.verify(pins).pinMode(2, Gpio.getOutput());
        order.verify(pins).digitalWrite(2, false);
        order.verify(pins).pinMode(2, Gpio.getInputPullup());
        order.verify(pins, atLeastOnce()).digitalRead(2);
    }

    @Test
    @DisplayName("Rejects a frame shorter than the DHT frame")
    void shouldRejectAFrameShorterThanTheDhtFrame() {
        // When & Then
        assertThatThrownBy(() -> protocol.read(2, new int[5], 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Gives up when the sensor does not answer the start pulse")
    void shouldGiveUpWhenTheSensorDoesNotAnswerTheStartPulse() {
        // Given
        new Wire().hold(true, 20_040).hold(true, 400).install(pins);

        // When
        boolean read = protocol.read(2, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("Gives up when a bit never goes high")
    void shouldGiveUpWhenABitNeverGoesHigh() {
        // Given
        new Wire().hold(true, 20_040).hold(false, 80).hold(true, 80).hold(false, 5_000).install(pins);

        // When
        boolean read = protocol.read(2, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("Gives up when a bit never goes low")
    void shouldGiveUpWhenABitNeverGoesLow() {
        // Given
        new Wire().hold(true, 20_040).hold(false, 80).hold(true, 80).hold(false, 50).hold(true, 5_000).install(pins);

        // When
        boolean read = protocol.read(2, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }
}
