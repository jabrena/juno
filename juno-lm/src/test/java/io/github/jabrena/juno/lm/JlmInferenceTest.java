package io.github.jabrena.juno.lm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JLM1 inference")
class JlmInferenceTest {

    @Test
    @DisplayName("Answers every trained question greedily")
    void shouldAnswerEveryTrainedQuestionGreedily() {
        // Given
        JlmInference inference = new JlmInference(TrainedModels.model());

        // When & Then
        assertThat(inference.answer("Hello")).isEqualTo("Hi!");
        assertThat(inference.answer("What is Juno?")).isEqualTo("A Java compiler.");
        assertThat(inference.answer("Which board?")).isEqualTo("Nano ESP32.");
    }

    @Test
    @DisplayName("Reuses its attention cache across questions")
    void shouldReuseItsAttentionCacheAcrossQuestions() {
        // Given
        JlmInference inference = new JlmInference(TrainedModels.model());
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
        JlmInference inference = new JlmInference(TrainedModels.model());

        // When & Then
        assertThat(inference.answer("Hello   ")).isEqualTo(inference.answer("Hello"));
        assertThat(inference.answer("Which\tboard?")).isEqualTo(inference.answer("Which board?"));
    }

    @Test
    @DisplayName("Samples reproducibly for a seed and stays within the context")
    void shouldSampleReproduciblyForASeed() {
        // Given
        JlmInference inference = new JlmInference(TrainedModels.model());

        // When
        String first = inference.answer("What is Juno?", 9, new SplittableRandom(5));
        String second = inference.answer("What is Juno?", 9, new SplittableRandom(5));

        // Then
        assertThat(first).isEqualTo(second).hasSizeLessThanOrEqualTo(JlmConfig.DEFAULT_CONTEXT);
        assertThat(inference.answer("What is Juno?", 1, new SplittableRandom(5))).isEqualTo("A Java compiler.");
    }

    @Test
    @DisplayName("Answers a question that fills the context without running past it")
    void shouldStopAtTheEndOfTheContext() {
        // Given
        JlmInference inference = new JlmInference(TrainedModels.model());

        // When
        String answer = inference.answer("x".repeat(200), 9, new SplittableRandom(1));

        // Then
        assertThat(answer).hasSizeLessThanOrEqualTo(JlmConfig.DEFAULT_CONTEXT - JlmVocabulary.MAX_QUESTION - 1);
    }

    @Test
    @DisplayName("Answers I do not know when an early pick is less certain than the floor")
    void shouldFallBackBelowTheConfidenceFloor() {
        // Given
        JlmInference certain = new JlmInference(TrainedModels.model(), 0f);
        JlmInference doubtful = new JlmInference(TrainedModels.model(), 1.01f);

        // When & Then
        assertThat(certain.answer("What is Juno?")).isEqualTo("A Java compiler.");
        assertThat(doubtful.answer("What is Juno?")).isEqualTo(JlmInference.UNKNOWN);
        assertThat(new JlmInference(TrainedModels.model()).answer("What is Juno?")).isEqualTo("A Java compiler.");
    }

    @Test
    @DisplayName("Rejects an empty question and an out-of-range creativity")
    void shouldRejectAnEmptyQuestionAndBadCreativity() {
        // Given
        JlmInference inference = new JlmInference(TrainedModels.model());

        // When & Then
        assertThatThrownBy(() -> inference.answer("   ")).isInstanceOf(JlmFormatException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> inference.answer("Hello", 10, new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("creativity");
        assertThatThrownBy(() -> inference.answer("Hello", -1, new SplittableRandom(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
