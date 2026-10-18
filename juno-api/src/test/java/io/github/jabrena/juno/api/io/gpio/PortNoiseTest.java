package io.github.jabrena.juno.api.io.gpio;

import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortNoiseTest {

    @BeforeEach
    void startWithAQuietPort() {
        Wire.resetBoard();
    }

    @Test
    void aVaryingSourcePassesTheHealthChecksAndGivesASeed() {
        Gpio.analogSequence = new int[] {0, 1};

        int seed = PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64);

        assertThat(seed).isNotZero();
    }

    @Test
    void theSameSourceAndTimingGiveTheSameSeed() {
        Gpio.analogSequence = new int[] {0, 1, 1, 0};
        int first = PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64);

        Wire.resetBoard();
        Gpio.analogSequence = new int[] {0, 1, 1, 0};

        assertThat(PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64)).isEqualTo(first);
    }

    @Test
    void aConstantLowSourceFailsTheProportionCheck() {
        assertThat(PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64)).isZero();
    }

    @Test
    void aConstantHighSourceFailsTheProportionCheck() {
        Gpio.analogSequence = new int[] {1};

        assertThat(PortNoise.collectSeed(Gpio.A0, Gpio.D2, 64)).isZero();
    }

    @Test
    void tooFewSamplesAreRejected() {
        assertThatThrownBy(() -> PortNoise.collectSeed(Gpio.A0, Gpio.D2, 31))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
