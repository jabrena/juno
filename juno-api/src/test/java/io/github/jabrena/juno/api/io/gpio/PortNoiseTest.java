package io.github.jabrena.juno.api.io.gpio;

import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Port noise seed collection")
class PortNoiseTest {

    @BeforeEach
    void startWithAQuietPort() {
        Wire.resetBoard();
    }

    @Test
    @DisplayName("A varying source passes the health checks and gives a seed")
    void shouldGiveASeedWhenTheSourceVaries() {
        // Given
        Gpio.analogSequence = new int[] {0, 1};

        // When
        int seed = PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64);

        // Then
        assertThat(seed).isNotZero();
    }

    @Test
    @DisplayName("The same source and timing give the same seed")
    void shouldGiveTheSameSeedForTheSameSourceAndTiming() {
        // Given
        Gpio.analogSequence = new int[] {0, 1, 1, 0};
        int first = PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64);
        Wire.resetBoard();
        Gpio.analogSequence = new int[] {0, 1, 1, 0};

        // When
        int second = PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64);

        // Then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("A constant low source fails the proportion check")
    void shouldFailWhenTheSourceIsConstantlyLow() {
        // When
        int seed = PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64);

        // Then
        assertThat(seed).isZero();
    }

    @Test
    @DisplayName("A constant high source fails the proportion check")
    void shouldFailWhenTheSourceIsConstantlyHigh() {
        // Given
        Gpio.analogSequence = new int[] {1};

        // When
        int seed = PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64);

        // Then
        assertThat(seed).isZero();
    }

    @Test
    @DisplayName("Too few samples are rejected")
    void shouldRejectTooFewSamples() {
        // When & Then
        assertThatThrownBy(() -> PortNoise.collectSeed(Gpio.A0, Gpio.D2, 31))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
