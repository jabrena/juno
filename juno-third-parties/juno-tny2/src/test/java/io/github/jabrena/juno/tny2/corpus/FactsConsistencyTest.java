package io.github.jabrena.juno.tny2.corpus;

import io.github.jabrena.juno.tny2.Tny2Vocabulary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bundled facts must not contradict themselves: the same question, as the model reads it after normalization, may
 * not have two different answers. Questions that only differ by one word are not checked, since many are different
 * questions with different answers.
 */
@DisplayName("Bundled facts")
class FactsConsistencyTest {
    private static final Path FACTS = Path.of("src/main/resources/tny2/facts");
    private static final Path HELD_OUT = Path.of("src/main/resources/tny2/heldout");

    @Test
    @DisplayName("The same question never has two different answers")
    void shouldNotContradictItself() throws IOException {
        // Given
        List<Fact> facts = new FactsReader().read(FACTS);
        Map<String, Set<String>> answersByQuestion = new TreeMap<>();
        for (Fact fact : facts) {
            for (String question : fact.questions()) {
                answersByQuestion.computeIfAbsent(normalize(question), key -> new TreeSet<>())
                        .add(fact.primaryAnswer());
            }
        }

        // When
        List<String> contradictions = new ArrayList<>();
        answersByQuestion.forEach((question, answers) -> {
            if (answers.size() > 1) {
                contradictions.add(question + " -> " + answers);
            }
        });

        // Then
        assertThat(contradictions).isEmpty();
    }

    @Test
    @DisplayName("No held-out question is also a training question, so the held-out score measures new wordings")
    void shouldKeepHeldOutQuestionsOutOfTraining() throws IOException {
        // Given
        Set<String> training = new TreeSet<>();
        for (Fact fact : new FactsReader().read(FACTS)) {
            fact.questions().forEach(question -> training.add(normalize(question)));
        }

        // When
        List<String> leaked = new ArrayList<>();
        for (Fact fact : new FactsReader().read(HELD_OUT)) {
            fact.questions().stream().filter(question -> training.contains(normalize(question))).forEach(leaked::add);
        }

        // Then
        assertThat(leaked).isEmpty();
    }

    /** The question as the model reads it, so two wordings that reach the model the same way count as one. */
    static String normalize(String question) {
        return Tny2Vocabulary.ASCII.question(question);
    }
}
