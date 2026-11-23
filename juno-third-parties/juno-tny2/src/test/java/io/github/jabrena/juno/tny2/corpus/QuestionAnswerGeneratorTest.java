package io.github.jabrena.juno.tny2.corpus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Question/answer generator")
class QuestionAnswerGeneratorTest {
    private static final Fact JUNO = new Fact(List.of("What is Juno?", "Tell me about Juno"),
            List.of("A compiler.", "A Java compiler."));
    private static final Fact HELLO = new Fact(List.of("Hello"), List.of("Hi!"));

    @Test
    @DisplayName("Without augmentation, teaches verbatim phrasings and the primary answer")
    void shouldTeachVerbatimPhrasingsWithoutAugmentation() {
        // Given
        ExampleSource source = new QuestionAnswerGenerator(List.of(JUNO, HELLO), Augmentation.none(),
                new SplittableRandom(1));

        // When
        List<Example> examples = Stream.generate(source::next).limit(200).toList();

        // Then
        assertThat(examples).extracting(Example::question).containsOnly("What is Juno?", "Tell me about Juno",
                "Hello").contains("What is Juno?", "Tell me about Juno", "Hello");
        assertThat(examples).extracting(Example::answer).containsOnly("A compiler.", "Hi!");
    }

    @Test
    @DisplayName("Gives the same stream for the same seed")
    void shouldGiveTheSameStreamForTheSameSeed() {
        // Given
        ExampleSource first = new QuestionAnswerGenerator(List.of(JUNO, HELLO), Augmentation.standard(),
                new SplittableRandom(9));
        ExampleSource second = new QuestionAnswerGenerator(List.of(JUNO, HELLO), Augmentation.standard(),
                new SplittableRandom(9));

        // When & Then
        assertThat(Stream.generate(first::next).limit(50).toList())
                .isEqualTo(Stream.generate(second::next).limit(50).toList());
    }

    @Test
    @DisplayName("With full augmentation, lower-cases, toggles the question mark, misspells and varies answers")
    void shouldPerturbQuestionsAndVaryAnswers() {
        // Given
        ExampleSource source = new QuestionAnswerGenerator(List.of(JUNO), new Augmentation(1, 1, 1, 0),
                new SplittableRandom(3));

        // When
        Set<String> questions = new HashSet<>();
        Set<String> answers = new HashSet<>();
        Stream.generate(source::next).limit(300).forEach(example -> {
            questions.add(example.question());
            answers.add(example.answer());
        });

        // Then
        assertThat(questions).allSatisfy(question -> assertThat(question).doesNotEndWith(" "));
        assertThat(questions).contains("what is Juno", "tell me about Juno?")
                .anySatisfy(question -> assertThat(question).hasSize("what is Juno".length() - 1))
                .anySatisfy(question -> assertThat(question).hasSize("what is Juno".length())
                        .isNotEqualTo("what is Juno"));
        assertThat(answers).containsExactlyInAnyOrder("A compiler.", "A Java compiler.");
    }

    @Test
    @DisplayName("Never misspells short questions, nor grows a full one or empties a lone question mark")
    void shouldKeepShortAndFullQuestionsValid() {
        // Given
        Fact longQuestion = new Fact(List.of("q".repeat(72)), List.of("a"));
        Fact mark = new Fact(List.of("?"), List.of("a"));
        ExampleSource marks = new QuestionAnswerGenerator(List.of(HELLO, longQuestion, mark),
                new Augmentation(0, 1, 0, 1), new SplittableRandom(4));
        ExampleSource typos = new QuestionAnswerGenerator(List.of(HELLO), new Augmentation(0, 0, 1, 1),
                new SplittableRandom(4));

        // When
        Set<String> marked = new HashSet<>();
        Stream.generate(marks::next).limit(100).forEach(example -> marked.add(example.question()));
        Set<String> misspelled = new HashSet<>();
        Stream.generate(typos::next).limit(20).forEach(example -> misspelled.add(example.question()));

        // Then
        assertThat(marked).containsExactlyInAnyOrder("Hello?", "q".repeat(72), "?");
        assertThat(misspelled).containsExactly("Hello");
    }

    @Test
    @DisplayName("Needs at least one fact and probabilities from 0 to 1")
    void shouldRejectNoFactsAndBadProbabilities() {
        // When & Then
        assertThatThrownBy(() -> new QuestionAnswerGenerator(List.of(), Augmentation.none(),
                new SplittableRandom(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Augmentation(-0.1, 0, 0, 1)).hasMessageContaining("lowercase");
        assertThatThrownBy(() -> new Augmentation(0, 1.1, 0, 1)).hasMessageContaining("questionMark");
        assertThatThrownBy(() -> new Augmentation(0, 0, 2, 1)).hasMessageContaining("typo");
        assertThatThrownBy(() -> new Augmentation(0, 0, 0, -1)).hasMessageContaining("primaryAnswer");
    }
}
