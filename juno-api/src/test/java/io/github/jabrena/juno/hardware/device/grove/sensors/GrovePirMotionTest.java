package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Grove PIR motion sensor")
class GrovePirMotionTest {
    private Gpio pins;
    private GrovePirMotion pir;

    @BeforeEach
    void wireTheSensorToAMockedPin() {
        pins = mock(Gpio.class);
        pir = new GrovePirMotion(pins);
    }

    @Test
    @DisplayName("Begin configures the signal pin as an input")
    void shouldConfigureTheSignalPinAsAnInputWhenBegun() {
        // When
        pir.begin(Gpio.getD2());

        // Then
        verify(pins).pinMode(Gpio.getD2(), Gpio.getInput());
    }

    @Test
    @DisplayName("Reports motion when the signal is high")
    void shouldReportMotionWhenTheSignalIsHigh() {
        // Given
        when(pins.digitalRead(Gpio.getD2())).thenReturn(true);

        // When
        boolean motion = pir.motionDetected(Gpio.getD2());

        // Then
        assertThat(motion).isTrue();
    }

    @Test
    @DisplayName("Reports no motion when the signal is low")
    void shouldReportNoMotionWhenTheSignalIsLow() {
        // Given
        when(pins.digitalRead(Gpio.getD2())).thenReturn(false);

        // When
        boolean motion = pir.motionDetected(Gpio.getD2());

        // Then
        assertThat(motion).isFalse();
    }

    @Test
    @DisplayName("Configures its bound pin as an input when created")
    void shouldConfigureItsBoundPinAsAnInput() {
        // When
        new GrovePirMotion(pins, Gpio.getD2());

        // Then
        verify(pins).pinMode(Gpio.getD2(), Gpio.getInput());
    }

    @Test
    @DisplayName("Reads motion from its bound pin as a MotionDetector and writes it as a sample")
    void shouldReadMotionFromItsBoundPin() {
        // Given
        when(pins.digitalRead(Gpio.getD2())).thenReturn(true);
        GrovePirMotion bound = new GrovePirMotion(pins, Gpio.getD2());
        float[] sample = new float[2];

        // When
        bound.fetchSample(sample, 1);

        // Then
        assertThat(bound.isMotionDetected()).isTrue();
        assertThat(bound.sampleSize()).isEqualTo(1);
        assertThat(sample[1]).isEqualTo(1f);
    }

    @Test
    @DisplayName("Writes zero to the sample when the bound pin is low")
    void shouldWriteZeroWhenTheBoundPinIsLow() {
        // Given
        when(pins.digitalRead(Gpio.getD2())).thenReturn(false);
        GrovePirMotion bound = new GrovePirMotion(pins, Gpio.getD2());
        float[] sample = new float[1];

        // When
        bound.fetchSample(sample, 0);

        // Then
        assertThat(sample[0]).isZero();
    }

    @Test
    @DisplayName("Refuses a MotionDetector read when created without its signal pin")
    void shouldRefuseAMotionReadWithoutItsPin() {
        // When & Then
        assertThatThrownBy(() -> pir.isMotionDetected()).isInstanceOf(IllegalStateException.class);
    }
}
