package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Grove DHT22 temperature and humidity sensor")
class GroveDht22Test {
    @BeforeEach
    void startAtTimeZero() {
        Wire.resetBoard();
    }

    static Stream<Arguments> invalidFrames() {
        return Stream.of(
                Arguments.of("humidity above 100 percent", new int[] {3, 233, 0, 0, 236}),
                Arguments.of("temperature above the sensor range", new int[] {0, 0, 3, 33, 36}),
                Arguments.of("temperature below the sensor range", new int[] {0, 0, 0x81, 0x91, 18}),
                Arguments.of("corrupt checksum", new int[] {2, 120, 0, 242, 109}),
                Arguments.of("out-of-range fields", new int[] {4, 0, 0, 242, 246}));
    }

    @Test
    @DisplayName("Reads a checksum-valid frame from the wire")
    void shouldReadAChecksumValidFrameFromTheWire() {
        // Given
        Wire.dhtReply(2, 120, 0, 242, 108).install();
        int[] reading = new int[2];
        int[] frame = new int[5];

        // When
        boolean read = GroveDht22.read(Gpio.D2, reading, reading.length, frame, frame.length);

        // Then
        assertThat(read).isTrue();
        assertThat(reading).containsExactly(242, 632);
    }

    @Test
    @DisplayName("Keeps the previous reading when the sensor stays silent")
    void shouldKeepThePreviousReadingWhenTheSensorStaysSilent() {
        // Given
        new Wire().hold(true, 20_040).hold(true, 400).install();
        int[] reading = {42, 44};
        int[] frame = new int[5];

        // When
        boolean read = GroveDht22.read(Gpio.D2, reading, reading.length, frame, frame.length);

        // Then
        assertThat(read).isFalse();
        assertThat(reading).containsExactly(42, 44);
    }

    @ParameterizedTest(name = "rejects {0} and keeps the previous reading")
    @MethodSource("invalidFrames")
    @DisplayName("Rejects invalid frames without changing the reading")
    void shouldKeepThePreviousReadingWhenTheFrameIsInvalid(String description, int[] frame) {
        // Given
        int[] reading = {42, 44};

        // When
        boolean decoded = GroveDht22.decode(frame, frame.length, reading, reading.length);

        // Then
        assertThat(decoded).isFalse();
        assertThat(reading).containsExactly(42, 44);
    }

    @Test
    @DisplayName("Rejects a frame that is too short for its fields")
    void shouldRejectAFrameTooShortForItsFields() {
        // Given
        int[] reading = new int[2];

        // When & Then
        assertThatThrownBy(() -> GroveDht22.decode(new int[5], 4, reading, reading.length))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Uses a five-byte frame")
    void shouldUseAFiveByteFrame() {
        // When & Then
        assertThat(GroveDht22.FRAME_LENGTH).isEqualTo(5);
    }

    @Test
    @DisplayName("Returns two reading values")
    void shouldReturnTwoReadingValues() {
        // When & Then
        assertThat(GroveDht22.READING_LENGTH).isEqualTo(2);
    }

    @Test
    @DisplayName("Rejects a reading buffer that is too short")
    void shouldRejectAReadingBufferThatIsTooShort() {
        // When & Then
        assertThatThrownBy(() -> GroveDht22.read(2, new int[1], 1, new int[5], 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects a frame buffer that is too short")
    void shouldRejectAFrameBufferThatIsTooShort() {
        // When & Then
        assertThatThrownBy(() -> GroveDht22.read(2, new int[2], 2, new int[4], 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Decodes the frame observed on the connected board")
    void shouldDecodeTheFrameObservedOnTheConnectedBoard() {
        // Given
        int[] frame = {2, 120, 0, 242, 108};
        int[] reading = new int[2];

        // When
        boolean decoded = GroveDht22.decode(frame, frame.length, reading, reading.length);

        // Then
        assertThat(decoded).isTrue();
        assertThat(reading).containsExactly(242, 632);
    }

    @Test
    @DisplayName("Decodes a negative temperature")
    void shouldDecodeANegativeTemperature() {
        // Given
        int[] frame = {1, 194, 128, 55, 122};
        int[] reading = new int[2];

        // When
        boolean decoded = GroveDht22.decode(frame, frame.length, reading, reading.length);

        // Then
        assertThat(decoded).isTrue();
        assertThat(reading).containsExactly(-55, 450);
    }
}
