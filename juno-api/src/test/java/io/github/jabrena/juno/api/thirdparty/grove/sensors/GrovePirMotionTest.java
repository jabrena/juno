package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrovePirMotionTest {

    @BeforeEach
    void startWithAQuietBoard() {
        Wire.resetBoard();
    }

    @Test
    void beginConfiguresTheSignalPinAsAnInput() {
        GrovePirMotion.begin(Gpio.D2);

        assertThat(Gpio.lastPinMode[Gpio.D2]).isEqualTo(Gpio.INPUT);
    }

    @Test
    void motionIsTheSignalLevel() {
        Gpio.highAt = time -> true;
        assertThat(GrovePirMotion.motionDetected(Gpio.D2)).isTrue();

        Gpio.highAt = time -> false;
        assertThat(GrovePirMotion.motionDetected(Gpio.D2)).isFalse();
    }
}
