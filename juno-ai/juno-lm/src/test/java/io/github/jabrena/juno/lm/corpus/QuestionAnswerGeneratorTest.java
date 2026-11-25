package io.github.jabrena.juno.lm.corpus;

import io.github.jabrena.juno.lm.JlmConfig;
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
    private static final Fact CHIP = new Fact(List.of("Which chip is in the board?"), List.of("An ESP32-S3."));

    @Test
    @DisplayName("Without augmentation, teaches each phrasing as the model reads it, with the primary answer")
    void shouldTeachNormalizedPhrasingsWithoutAugmentation() {
        // Given
        ExampleSource source = new QuestionAnswerGenerator(List.of(JUNO, HELLO), Augmentation.none(),
                new SplittableRandom(1));

        // When
        List<Example> examples = Stream.generate(source::next).limit(200).toList();

        // Then
        assertThat(examples).extracting(Example::question)
                .containsOnly("what is juno", "tell me about juno", "hello")
                .contains("what is juno", "tell me about juno", "hello");
        assertThat(examples).extracting(Example::answer).containsOnly("A compiler.", "Hi!");
    }

    @Test
    @DisplayName("Gives the same stream for the same seed")
    void shouldGiveTheSameStreamForTheSameSeed() {
        // Given
        ExampleSource first = new QuestionAnswerGenerator(List.of(JUNO, CHIP), Augmentation.standard(),
                new SplittableRandom(9));
        ExampleSource second = new QuestionAnswerGenerator(List.of(JUNO, CHIP), Augmentation.standard(),
                new SplittableRandom(9));

        // When & Then
        assertThat(Stream.generate(first::next).limit(50).toList())
                .isEqualTo(Stream.generate(second::next).limit(50).toList());
    }

    @Test
    @DisplayName("Swaps a word for a synonym and drops filler words")
    void shouldSwapSynonymsAndDropFillers() {
        // Given
        ExampleSource synonyms = new QuestionAnswerGenerator(List.of(CHIP), new Augmentation(1, 0, 0, 1),
                new SplittableRandom(3));
        ExampleSource fillers = new QuestionAnswerGenerator(List.of(CHIP), new Augmentation(0, 1, 0, 1),
                new SplittableRandom(3));

        // When
        Set<String> swapped = new HashSet<>();
        Stream.generate(synonyms::next).limit(200).forEach(example -> swapped.add(example.question()));
        Set<String> dropped = new HashSet<>();
        Stream.generate(fillers::next).limit(200).forEach(example -> dropped.add(example.question()));

        // Then
        assertThat(swapped).contains("which processor is in the board", "which cpu is in the board",
                "which microcontroller is in the board", "what chip is in the board", "which chip is in the device");
        assertThat(dropped).contains("which chip is in board", "which chip in the board")
                .doesNotContain("which chip is in the board");
    }

    @Test
    @DisplayName("Keeps the original wording when a synonym would make the example too long for the context")
    void shouldKeepTheOriginalWordingWhenASynonymDoesNotFit() {
        // Given
        String answer = "a".repeat(JlmConfig.DEFAULT_CONTEXT - 2 - "what is juno".length());
        ExampleSource source = new QuestionAnswerGenerator(List.of(new Fact(List.of("What is Juno?"),
                List.of(answer))), new Augmentation(1, 0, 0, 1), new SplittableRandom(6));

        // When
        Set<String> questions = new HashSet<>();
        Stream.generate(source::next).limit(50).forEach(example -> questions.add(example.question()));

        // Then
        assertThat(questions).contains("what is juno").allSatisfy(question ->
                assertThat(question.length() + answer.length() + 2).isLessThanOrEqualTo(JlmConfig.DEFAULT_CONTEXT));
    }

    @Test
    @DisplayName("Misspells long questions, never short ones, and varies answers when asked to")
    void shouldMisspellLongQuestionsAndVaryAnswers() {
        // Given
        ExampleSource long_ = new QuestionAnswerGenerator(List.of(JUNO), new Augmentation(0, 0, 1, 0),
                new SplittableRandom(4));
        ExampleSource short_ = new QuestionAnswerGenerator(List.of(HELLO), new Augmentation(0, 0, 1, 1),
                new SplittableRandom(4));

        // When
        Set<String> questions = new HashSet<>();
        Set<String> answers = new HashSet<>();
        Stream.generate(long_::next).limit(300).forEach(example -> {
            questions.add(example.question());
            answers.add(example.answer());
        });
        Set<String> shortQuestions = new HashSet<>();
        Stream.generate(short_::next).limit(20).forEach(example -> shortQuestions.add(example.question()));

        // Then
        assertThat(questions).contains("what is juno")
                .anySatisfy(question -> assertThat(question).hasSize("what is juno".length() - 1))
                .anySatisfy(question -> assertThat(question).hasSize("what is juno".length())
                        .isNotEqualTo("what is juno"));
        assertThat(answers).containsExactlyInAnyOrder("A compiler.", "A Java compiler.");
        assertThat(shortQuestions).containsExactly("hello");
    }

    @Test
    @DisplayName("Leaves a one-word question alone and needs at least one fact and valid probabilities")
    void shouldLeaveOneWordQuestionsAloneAndRejectBadSettings() {
        // Given
        ExampleSource source = new QuestionAnswerGenerator(List.of(HELLO), new Augmentation(1, 1, 0, 1),
                new SplittableRandom(5));

        // When & Then
        assertThat(Stream.generate(source::next).limit(20).map(Example::question).distinct().toList())
                .containsExactly("hello");
        assertThatThrownBy(() -> new QuestionAnswerGenerator(List.of(), Augmentation.none(),
                new SplittableRandom(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Augmentation(-0.1, 0, 0, 1)).hasMessageContaining("synonym");
        assertThatThrownBy(() -> new Augmentation(0, 1.1, 0, 1)).hasMessageContaining("dropWord");
        assertThatThrownBy(() -> new Augmentation(0, 0, 2, 1)).hasMessageContaining("typo");
        assertThatThrownBy(() -> new Augmentation(0, 0, 0, -1)).hasMessageContaining("primaryAnswer");
    }
}
