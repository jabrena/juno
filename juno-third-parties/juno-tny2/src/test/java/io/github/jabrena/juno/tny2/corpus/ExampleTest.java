package io.github.jabrena.juno.tny2.corpus;

import io.github.jabrena.juno.tny2.Tny2FormatException;
import io.github.jabrena.juno.tny2.Tny2Vocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Training example and fact")
class ExampleTest {

    @Test
    @DisplayName("Lays out question, END, answer and END, and targets the answer")
    void shouldLayOutTheSequenceAndTargetTheAnswer() {
        // When
        Example example = new Example("Hi?", "Yo");

        // Then
        assertThat(example.normalizedQuestion()).isEqualTo("hi");
        assertThat(example.tokens()).containsExactly('h' - 30, 'i' - 30, Tny2Vocabulary.END, 'Y' - 30, 'o' - 30,
                Tny2Vocabulary.END);
        assertThat(example.firstTarget()).isEqualTo(3);
    }

    @Test
    @DisplayName("Accepts a question and answer that exactly fill the context")
    void shouldAcceptAPairThatFillsTheContext() {
        // When & Then
        assertThatCode(() -> new Example("q".repeat(72), "a".repeat(54))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new Example("q".repeat(72), "a".repeat(55)))
                .isInstanceOf(Tny2FormatException.class).hasMessageContaining("126");
    }

    @Test
    @DisplayName("Rejects questions and answers the board cannot see")
    void shouldRejectTextTheBoardCannotSee() {
        // When & Then
        assertThatThrownBy(() -> new Example("", "a")).isInstanceOf(Tny2FormatException.class)
                .hasMessageContaining("question must be 1 to 72");
        assertThatThrownBy(() -> new Example("??", "a")).isInstanceOf(Tny2FormatException.class);
        assertThatThrownBy(() -> new Example("q".repeat(73), "a")).isInstanceOf(Tny2FormatException.class);
        assertThatThrownBy(() -> new Example("Mañana?", "a")).isInstanceOf(Tny2FormatException.class);
        assertThatThrownBy(() -> new Example("Hi", "")).isInstanceOf(Tny2FormatException.class)
                .hasMessageContaining("answer");
        assertThatThrownBy(() -> new Example("Hi", "Sí")).isInstanceOf(Tny2FormatException.class);
    }

    @Test
    @DisplayName("A fact knows its primary phrasing and every accepted answer")
    void shouldKnowPrimaryPhrasingAndAcceptedAnswers() {
        // When
        Fact fact = new Fact(List.of("Hello", "Hi"), List.of("Hello!", "Hi!"));

        // Then
        assertThat(fact.primaryQuestion()).isEqualTo("Hello");
        assertThat(fact.primaryAnswer()).isEqualTo("Hello!");
        assertThat(fact.accepts("Hi!")).isTrue();
        assertThat(fact.accepts("Hey")).isFalse();
    }

    @Test
    @DisplayName("A fact needs a question and an answer that can be trained together")
    void shouldRejectAnIncompleteOrUntrainableFact() {
        // When & Then
        assertThatThrownBy(() -> new Fact(List.of(), List.of("a"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fact(List.of("q"), List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Fact(List.of("q"), List.of("a".repeat(130))))
                .isInstanceOf(Tny2FormatException.class);
    }
}
