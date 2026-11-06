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
import static org.mockito.Mockito.when;

@DisplayName("Grove ultrasonic ranger")
class GroveUltrasonicRangerTest {

    @Test
    @DisplayName("Uses the shared signal pin for trigger and echo then returns metres")
    void shouldTriggerAndMeasureTheSharedSignalPin() {
        Gpio gpio = mock(Gpio.class);
        Clock clock = new IncrementingClock(100);
        when(gpio.digitalPin("D2")).thenReturn(2);
        int digitalPin = gpio.digitalPin("D2");
        when(gpio.digitalRead(digitalPin)).thenReturn(true, false);
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, clock, digitalPin);

        float range = ranger.getRange();

        assertThat(range).isCloseTo(0.034f, within(0.000001f));
        var order = inOrder(gpio);
        order.verify(gpio).pinMode(digitalPin, gpio.getOutput());
        order.verify(gpio).digitalWrite(digitalPin, false);
        order.verify(gpio).digitalWrite(digitalPin, true);
        order.verify(gpio).digitalWrite(digitalPin, false);
        order.verify(gpio).pinMode(digitalPin, gpio.getInput());
    }

    @Test
    @DisplayName("Makes its one range available as an allocation-free sample")
    void shouldFetchOneRangeSample() {
        Gpio gpio = mock(Gpio.class);
        Clock clock = new IncrementingClock(10);
        when(gpio.digitalPin("D2")).thenReturn(2);
        int digitalPin = gpio.digitalPin("D2");
        when(gpio.digitalRead(digitalPin)).thenReturn(true, false);
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, clock, digitalPin);
        float[] sample = new float[1];

        ranger.fetchSample(sample, 0);

        assertThat(ranger.sampleSize()).isOne();
        assertThat(sample[0]).isEqualTo(0.0034f);
    }

    @Test
    @DisplayName("Returns NaN if no echo starts before the timeout")
    void shouldReturnNanWhenNoEchoStarts() {
        Gpio gpio = mock(Gpio.class);
        when(gpio.digitalPin("D2")).thenReturn(2);
        int digitalPin = gpio.digitalPin("D2");
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(gpio, new IncrementingClock(30_000), digitalPin);
        when(gpio.digitalRead(digitalPin)).thenReturn(false);

        assertThat(ranger.getRange()).isNaN();
    }

    @Test
    @DisplayName("Rejects a negative signal pin")
    void shouldRejectANegativeSignalPin() {
        assertThatThrownBy(() -> new GroveUltrasonicRanger(mock(Gpio.class), new IncrementingClock(1), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class IncrementingClock implements Clock {
        private final int step;
        private int micros;

        private IncrementingClock(int step) {
            this.step = step;
        }

        @Override
        public int millis() {
            return 0;
        }

        @Override
        public int micros() {
            int result = micros;
            micros += step;
            return result;
        }
    }
}
