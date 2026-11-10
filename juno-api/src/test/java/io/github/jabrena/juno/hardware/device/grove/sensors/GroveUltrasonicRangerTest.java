package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("Grove ultrasonic ranger")
class GroveUltrasonicRangerTest {
    private static final int PIN = 2;
    private static final int ECHO_TIMEOUT_MICROS = 30_000;

    @Test
    @DisplayName("Triggers on the shared signal pin, times the echo with pulseIn, and returns metres")
    void shouldTriggerAndTimeTheEchoWithPulseIn() {
        // Given: a 200 microsecond echo, 3.4 cm there and back
        Gpio gpio = mock(Gpio.class);
        when(gpio.pulseIn(PIN, true, ECHO_TIMEOUT_MICROS)).thenReturn(200);
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, mock(Clock.class), PIN);

        // When
        float range = ranger.getRange();

        // Then
        assertThat(range).isCloseTo(0.034f, within(0.000001f));
        var order = inOrder(gpio);
        order.verify(gpio).pinMode(PIN, gpio.getOutput());
        order.verify(gpio).digitalWrite(PIN, false);
        order.verify(gpio).digitalWrite(PIN, true);
        order.verify(gpio).digitalWrite(PIN, false);
        order.verify(gpio).pinMode(PIN, gpio.getInput());
        order.verify(gpio).pulseIn(PIN, true, ECHO_TIMEOUT_MICROS);
    }

    @Test
    @DisplayName("Makes its one range available as an allocation-free sample, without reading the clock")
    void shouldFetchOneRangeSample() {
        // Given
        Gpio gpio = mock(Gpio.class);
        Clock clock = mock(Clock.class);
        when(gpio.pulseIn(PIN, true, ECHO_TIMEOUT_MICROS)).thenReturn(20);
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, clock, PIN);
        float[] sample = new float[1];

        // When
        ranger.fetchSample(sample, 0);

        // Then
        assertThat(ranger.sampleSize()).isOne();
        assertThat(sample[0]).isEqualTo(0.0034f);
        verifyNoInteractions(clock);
    }

    @Test
    @DisplayName("Returns NaN when no echo arrives before the timeout")
    void shouldReturnNanWhenNoEchoArrives() {
        // Given: pulseIn's timeout
        Gpio gpio = mock(Gpio.class);
        when(gpio.pulseIn(PIN, true, ECHO_TIMEOUT_MICROS)).thenReturn(0);
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, mock(Clock.class), PIN);

        // When & Then
        assertThat(ranger.getRange()).isNaN();
    }

    @Test
    @DisplayName("Rejects a negative signal pin")
    void shouldRejectANegativeSignalPin() {
        assertThatThrownBy(() -> new GroveUltrasonicRanger(mock(Gpio.class), mock(Clock.class), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
