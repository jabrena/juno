package io.github.jabrena.juno.tny2.training;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Training options")
class TrainingOptionsTest {

    @Test
    @DisplayName("Warms up linearly, then decays along a cosine to the final fraction")
    void shouldWarmUpThenDecay() {
        // Given
        TrainingOptions options = new TrainingOptions(12, 1, 1f, 0.1f, 2, 1f, 1, 0);

        // When & Then
        assertThat(options.learningRateAt(0)).isEqualTo(0.5f);
        assertThat(options.learningRateAt(1)).isEqualTo(1f);
        assertThat(options.learningRateAt(2)).isEqualTo(1f);
        assertThat(options.learningRateAt(6)).isCloseTo(0.55f + 0.45f * (float) Math.cos(Math.PI * 4 / 9),
                within(1e-6f));
        assertThat(options.learningRateAt(11)).isCloseTo(0.1f, within(1e-6f));
    }

    @Test
    @DisplayName("Holds the final rate past the last step and handles a run with no warm-up")
    void shouldHoldTheFinalRate() {
        // Given
        TrainingOptions options = new TrainingOptions(1, 1, 1f, 0.5f, 0, 1f, 1, 0);

        // When & Then
        assertThat(options.learningRateAt(0)).isEqualTo(1f);
        assertThat(options.learningRateAt(5)).isCloseTo(0.5f, within(1e-6f));
    }

    @Test
    @DisplayName("Defaults to 32 examples per step on every core")
    void shouldDefaultToEveryCore() {
        // When
        TrainingOptions options = TrainingOptions.defaults(2000);

        // Then
        assertThat(options.batchSize()).isEqualTo(32);
        assertThat(options.warmupSteps()).isEqualTo(100);
        assertThat(options.threads()).isEqualTo(Runtime.getRuntime().availableProcessors());
        assertThat(TrainingOptions.defaults(50).warmupSteps()).isEqualTo(5);
        assertThat(options.weightDecay()).isEqualTo(0.01f);
        assertThat(options.dropout()).isEqualTo(0.1f);
        assertThat(options.evalEvery()).isEqualTo(250);
        assertThat(new TrainingOptions(1, 1, 1f, 0f, 0, 1f, 1, 0).dropout()).isZero();
    }

    @Test
    @DisplayName("Rejects values that cannot train")
    void shouldRejectValuesThatCannotTrain() {
        // When & Then
        assertThatThrownBy(() -> new TrainingOptions(0, 1, 1f, 0f, 0, 1f, 1, 0)).hasMessageContaining("steps");
        assertThatThrownBy(() -> new TrainingOptions(1, 0, 1f, 0f, 0, 1f, 1, 0)).hasMessageContaining("batch");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 0f, 0, 1f, 0, 0)).hasMessageContaining("threads");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 0f, 0f, 0, 1f, 1, 0)).hasMessageContaining("learningRate");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, Float.NaN, 0f, 0, 1f, 1, 0))
                .hasMessageContaining("learningRate");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 1.5f, 0, 1f, 1, 0))
                .hasMessageContaining("finalLearningRate");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, -0.5f, 0, 1f, 1, 0))
                .hasMessageContaining("finalLearningRate");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 0f, -1, 1f, 1, 0)).hasMessageContaining("warmup");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 0f, 0, 1f, 1, -1)).hasMessageContaining("logEvery");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 0f, 0, 0f, 1, 0)).hasMessageContaining("clipNorm");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 0f, 0, 1f, 1, 0, -0.1f, 0f, 0))
                .hasMessageContaining("weightDecay");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 0f, 0, 1f, 1, 0, 0f, 1f, 0))
                .hasMessageContaining("dropout");
        assertThatThrownBy(() -> new TrainingOptions(1, 1, 1f, 0f, 0, 1f, 1, 0, 0f, 0f, -1))
                .hasMessageContaining("evalEvery");
    }
}
