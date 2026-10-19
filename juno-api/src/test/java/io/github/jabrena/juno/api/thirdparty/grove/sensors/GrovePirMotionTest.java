package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("Grove PIR motion sensor")
class GrovePirMotionTest {

    @BeforeEach
    void startWithAQuietBoard() {
        Wire.resetBoard();
    }

    @Test
    @DisplayName("Begin configures the signal pin as an input")
    void shouldConfigureTheSignalPinAsAnInputWhenBegun() {
        // When
        GrovePirMotion.begin(Gpio.D2);

        // Then
        assertThat(Gpio.lastPinMode[Gpio.D2]).isEqualTo(Gpio.INPUT);
    }

    @Test
    @DisplayName("Reports motion when the signal is high")
    void shouldReportMotionWhenTheSignalIsHigh() {
        // Given
        Gpio.highAt = time -> true;

        // When
        boolean motion = GrovePirMotion.motionDetected(Gpio.D2);

        // Then
        assertThat(motion).isTrue();
    }

    @Test
    @DisplayName("Reports no motion when the signal is low")
    void shouldReportNoMotionWhenTheSignalIsLow() {
        // Given
        Gpio.highAt = time -> false;

        // When
        boolean motion = GrovePirMotion.motionDetected(Gpio.D2);

        // Then
        assertThat(motion).isFalse();
    }
}
