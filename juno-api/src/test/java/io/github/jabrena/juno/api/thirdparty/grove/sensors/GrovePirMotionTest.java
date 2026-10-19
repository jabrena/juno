package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
}
