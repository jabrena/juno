package io.github.jabrena.juno.hardware.gpio;

import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Port noise seed collection")
class PortNoiseTest {
    private final Gpio gpio = mock(Gpio.class);
    private final Clock clock = mock(Clock.class);
    private final PortNoise noise = new PortNoise(gpio, clock);
    private int analogPin;
    private int digitalPin;

    @BeforeEach
    void wireAPulledUpComparatorAndATickingTimer() {
        when(gpio.analogPin("A0")).thenReturn(14);
        when(gpio.digitalPin("D2")).thenReturn(2);
        analogPin = gpio.analogPin("A0");
        digitalPin = gpio.digitalPin("D2");
        when(gpio.digitalRead(digitalPin)).thenReturn(true);
        clearInvocations(gpio);
        int[] micros = {0};
        when(clock.micros()).thenAnswer(invocation -> micros[0]++);
    }

    @Test
    @DisplayName("A varying source passes the health checks and gives a seed")
    void shouldGiveASeedWhenTheSourceVaries() {
        // Given
        analogReadsCycle(0, 1);

        // When
        int seed = noise.collectSeed(analogPin, digitalPin, 64);

        // Then
        assertThat(seed).isNotZero();
    }

    @Test
    @DisplayName("Takes one analog, one digital and one timer sample per requested sample")
    void shouldTakeOneOfEachReadingPerSample() {
        // Given
        analogReadsCycle(0, 1);

        // When
        noise.collectSeed(analogPin, digitalPin, 64);

        // Then
        verify(gpio, times(64)).analogRead(analogPin);
        verify(gpio, times(64)).digitalRead(digitalPin);
        verify(clock, times(64)).micros();
    }

    @Test
    @DisplayName("The same source and timing give the same seed")
    void shouldGiveTheSameSeedForTheSameSourceAndTiming() {
        // Given
        analogReadsCycle(0, 1, 1, 0);
        int first = noise.collectSeed(analogPin, digitalPin, 64);
        Gpio sameGpio = mock(Gpio.class);
        Clock sameClock = mock(Clock.class);
        when(sameGpio.digitalPin("D2")).thenReturn(digitalPin);
        int sameDigitalPin = sameGpio.digitalPin("D2");
        when(sameGpio.digitalRead(sameDigitalPin)).thenReturn(true);
        int[] cycle = {0, 1, 1, 0};
        int[] reads = {0};
        when(sameGpio.analogPin("A0")).thenReturn(analogPin);
        int sameAnalogPin = sameGpio.analogPin("A0");
        when(sameGpio.analogRead(sameAnalogPin)).thenAnswer(invocation -> cycle[reads[0]++ % cycle.length]);
        int[] micros = {0};
        when(sameClock.micros()).thenAnswer(invocation -> micros[0]++);

        // When
        int second = new PortNoise(sameGpio, sameClock).collectSeed(sameAnalogPin, sameDigitalPin, 64);

        // Then
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("A constant low source fails the proportion check")
    void shouldFailWhenTheSourceIsConstantlyLow() {
        // Given
        when(gpio.analogRead(analogPin)).thenReturn(0);

        // When
        int seed = noise.collectSeed(analogPin, digitalPin, 64);

        // Then
        assertThat(seed).isZero();
    }

    @Test
    @DisplayName("A constant high source fails the proportion check")
    void shouldFailWhenTheSourceIsConstantlyHigh() {
        // Given
        when(gpio.analogRead(analogPin)).thenReturn(1);

        // When
        int seed = noise.collectSeed(analogPin, digitalPin, 64);

        // Then
        assertThat(seed).isZero();
    }

    @Test
    @DisplayName("A source and timer that never change fail the repetition check")
    void shouldFailWhenTheSamplesRepeat() {
        // Given
        when(gpio.analogRead(analogPin)).thenReturn(1);
        when(clock.micros()).thenReturn(0);

        // When
        int seed = noise.collectSeed(analogPin, digitalPin, 64);

        // Then
        assertThat(seed).isZero();
        verify(gpio, times(33)).analogRead(analogPin);
    }

    @Test
    @DisplayName("Too few samples are rejected before touching the pins")
    void shouldRejectTooFewSamples() {
        // When & Then
        assertThatThrownBy(() -> noise.collectSeed(analogPin, digitalPin, 31))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gpio, clock);
    }

    /** Makes the analog pin return {@code values} in turn, starting over after the last. */
    private void analogReadsCycle(int... values) {
        int[] reads = {0};
        when(gpio.analogRead(analogPin)).thenAnswer(invocation -> values[reads[0]++ % values.length]);
    }
}
