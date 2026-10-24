package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.testing.SimulatedTime;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("Grove DHT11 temperature and humidity sensor")
class GroveDht11Test {
    private Gpio pins;
    private GroveDht11 dht11;

    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
        pins = mock(Gpio.class);
        dht11 = new GroveDht11(pins, SimulatedTime.clock());
    }

    @Test
    @DisplayName("Decodes a whole-number temperature and humidity frame")
    void shouldDecodeWholeNumberTemperatureAndHumidity() {
        // Given
        int[] frame = {55, 0, 24, 0, 79};
        int[] reading = new int[2];

        // When
        boolean decoded = dht11.decode(frame, frame.length, reading, reading.length);

        // Then
        assertThat(decoded).isTrue();
        assertThat(reading).containsExactly(24, 55);
    }

    @Test
    @DisplayName("Keeps the previous reading when the frame is a DHT22 frame")
    void shouldKeepThePreviousReadingWhenTheFrameIsNotDht11() {
        // Given
        int[] reading = {42, 44};
        int[] dht22Frame = {2, 120, 0, 242, 108};

        // When
        boolean decoded = dht11.decode(dht22Frame, dht22Frame.length, reading, reading.length);

        // Then
        assertThat(decoded).isFalse();
        assertThat(reading).containsExactly(42, 44);
    }

    @Test
    @DisplayName("Reads a checksum-valid frame from the wire")
    void shouldReadAChecksumValidFrameFromTheWire() {
        // Given
        Wire.dhtReply(55, 0, 24, 0, 79).install(pins);
        int[] reading = new int[2];
        int[] frame = new int[5];

        // When
        boolean read = dht11.read(Gpio.getD2(), reading, reading.length, frame, frame.length);

        // Then
        assertThat(read).isTrue();
        assertThat(reading).containsExactly(24, 55);
        assertThat(frame).containsExactly(55, 0, 24, 0, 79);
    }

    @Test
    @DisplayName("Keeps the previous reading when the checksum fails")
    void shouldKeepThePreviousReadingWhenTheChecksumFails() {
        // Given
        Wire.dhtReply(55, 0, 24, 0, 80).install(pins);
        int[] reading = {42, 44};
        int[] frame = new int[5];

        // When
        boolean read = dht11.read(Gpio.getD2(), reading, reading.length, frame, frame.length);

        // Then
        assertThat(read).isFalse();
        assertThat(reading).containsExactly(42, 44);
    }

    @Test
    @DisplayName("Gives up when the sensor never answers")
    void shouldGiveUpWhenTheSensorNeverAnswers() {
        // Given
        new Wire().hold(true, 400).install(pins);
        int[] reading = {42, 44};
        int[] frame = new int[5];

        // When
        boolean read = dht11.read(Gpio.getD2(), reading, reading.length, frame, frame.length);

        // Then
        assertThat(read).isFalse();
        assertThat(reading).containsExactly(42, 44);
    }

    @Test
    @DisplayName("Rejects a reading buffer that is too short")
    void shouldRejectAReadingBufferThatIsTooShort() {
        // Given
        int[] frame = new int[5];

        // When & Then
        assertThatThrownBy(() -> dht11.read(Gpio.getD2(), new int[1], 1, frame, frame.length))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects a frame buffer that is too short")
    void shouldRejectAFrameBufferThatIsTooShort() {
        // Given
        int[] reading = new int[2];

        // When & Then
        assertThatThrownBy(() -> dht11.read(Gpio.getD2(), reading, 2, new int[4], 4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
