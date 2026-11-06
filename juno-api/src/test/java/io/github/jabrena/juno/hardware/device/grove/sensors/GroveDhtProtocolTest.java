package io.github.jabrena.juno.hardware.device.grove.sensors;

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
import static org.mockito.Mockito.when;

@DisplayName("Grove DHT single-wire protocol")
class GroveDhtProtocolTest {
    private Gpio pins;
    private GroveDhtProtocol protocol;
    private int digitalPin;

    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
        pins = mock(Gpio.class);
        when(pins.digitalPin("D2")).thenReturn(2);
        digitalPin = pins.digitalPin("D2");
        protocol = new GroveDhtProtocol(pins, SimulatedTime.clock());
    }

    @Test
    @DisplayName("Reads every byte of a checksum-valid frame")
    void shouldReadEveryByteOfAChecksumValidFrame() {
        // Given
        Wire.dhtReply(0x00, 0xFF, 0x00, 0xFF, 0xFE).install(pins);
        int[] frame = new int[5];

        // When
        boolean read = protocol.read(digitalPin, frame, frame.length);

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
        protocol.read(digitalPin, new int[5], 5);

        // Then
        InOrder order = inOrder(pins);
        order.verify(pins).pinMode(digitalPin, pins.getOutput());
        order.verify(pins).digitalWrite(digitalPin, false);
        order.verify(pins).pinMode(digitalPin, pins.getInputPullup());
        order.verify(pins, atLeastOnce()).digitalRead(digitalPin);
    }

    @Test
    @DisplayName("Rejects a frame shorter than the DHT frame")
    void shouldRejectAFrameShorterThanTheDhtFrame() {
        // When & Then
        assertThatThrownBy(() -> protocol.read(digitalPin, new int[5], 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Gives up when the sensor does not answer the start pulse")
    void shouldGiveUpWhenTheSensorDoesNotAnswerTheStartPulse() {
        // Given
        new Wire().hold(true, 400).install(pins);

        // When
        boolean read = protocol.read(digitalPin, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("Gives up when a bit never goes high")
    void shouldGiveUpWhenABitNeverGoesHigh() {
        // Given
        new Wire().hold(false, 80).hold(true, 80).hold(false, 5_000).install(pins);

        // When
        boolean read = protocol.read(digitalPin, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("Gives up when a bit never goes low")
    void shouldGiveUpWhenABitNeverGoesLow() {
        // Given
        new Wire().hold(false, 80).hold(true, 80).hold(false, 50).hold(true, 5_000).install(pins);

        // When
        boolean read = protocol.read(digitalPin, new int[5], 5);

        // Then
        assertThat(read).isFalse();
    }
}
