package io.github.jabrena.juno.tny2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TNY2 inference")
class Tny2InferenceTest {

    @Test
    @DisplayName("Answers every trained question greedily")
    void shouldAnswerEveryTrainedQuestionGreedily() {
        // Given
        Tny2Inference inference = new Tny2Inference(TrainedModels.model());

        // When & Then
        assertThat(inference.answer("Hello")).isEqualTo("Hi!");
        assertThat(inference.answer("What is Juno?")).isEqualTo("A Java compiler.");
        assertThat(inference.answer("Which board?")).isEqualTo("Nano ESP32.");
    }

    @Test
    @DisplayName("Reuses its attention cache across questions")
    void shouldReuseItsAttentionCacheAcrossQuestions() {
        // Given
        Tny2Inference inference = new Tny2Inference(TrainedModels.model());
        String first = inference.answer("Which board?");

        // When
        inference.answer("What is Juno? What is Juno? What is Juno?");

        // Then
        assertThat(inference.answer("Which board?")).isEqualTo(first);
    }

    @Test
    @DisplayName("Normalizes the question as the board does: trailing spaces and control characters")
    void shouldNormalizeTheQuestionAsTheBoardDoes() {
        // Given
        Tny2Inference inference = new Tny2Inference(TrainedModels.model());

        // When & Then
        assertThat(inference.answer("Hello   ")).isEqualTo(inference.answer("Hello"));
        assertThat(inference.answer("Which\tboard?")).isEqualTo(inference.answer("Which board?"));
    }

    @Test
    @DisplayName("Samples reproducibly for a seed and stays within the context")
    void shouldSampleReproduciblyForASeed() {
        // Given
        Tny2Inference inference = new Tny2Inference(TrainedModels.model());

        // When
        String first = inference.answer("What is Juno?", 9, new SplittableRandom(5));
        String second = inference.answer("What is Juno?", 9, new SplittableRandom(5));

        // Then
        assertThat(first).isEqualTo(second).hasSizeLessThanOrEqualTo(Tny2Config.CONTEXT);
        assertThat(inference.answer("What is Juno?", 1, new SplittableRandom(5))).isEqualTo("A Java compiler.");
    }

    @Test
    @DisplayName("Answers a question that fills the context without running past it")
    void shouldStopAtTheEndOfTheContext() {
        // Given
        Tny2Inference inference = new Tny2Inference(TrainedModels.model());

        // When
        String answer = inference.answer("x".repeat(200), 9, new SplittableRandom(1));

        // Then
        assertThat(answer).hasSizeLessThanOrEqualTo(Tny2Config.CONTEXT - Tny2Vocabulary.MAX_QUESTION - 1);
    }

    @Test
    @DisplayName("Rejects an empty question and an out-of-range creativity")
    void shouldRejectAnEmptyQuestionAndBadCreativity() {
        // Given
        Tny2Inference inference = new Tny2Inference(TrainedModels.model());

        // When & Then
        assertThatThrownBy(() -> inference.answer("   ")).isInstanceOf(Tny2FormatException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> inference.answer("Hello", 10, new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("creativity");
        assertThatThrownBy(() -> inference.answer("Hello", -1, new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
