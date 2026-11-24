package io.github.jabrena.juno.thirdparty.tny2;

import io.github.jabrena.juno.tny2.Tny2Model;
import io.github.jabrena.juno.tny2.Tny2Reader;
import io.github.jabrena.juno.tny2.corpus.FactsReader;
import io.github.jabrena.juno.tny2.training.Tny2Evaluation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality gate for the tracked JUNO.BIN, run through the Java engine that the board's engine matches: a model that
 * forgets its facts or stops generalizing to unseen wordings fails the build before anyone flashes it.
 */
@DisplayName("Tracked TNY2 model quality")
class Tny2ModelQualityTest {
    private static final Path MODEL = Path.of("src/main/resources/tny2/JUNO.BIN");
    private static final Path FACTS = Path.of("src/main/resources/tny2/facts");
    private static final Path HELD_OUT = Path.of("src/main/resources/tny2/heldout");
    /** The profile's own gate for the trained phrasings. */
    private static final double MIN_TRAINED = 0.95;
    /** Below the 40.5% the tracked model scored when it was trained, with room for a retrain's noise. */
    private static final double MIN_HELD_OUT = 0.35;

    private static Tny2Model model;

    @BeforeAll
    static void loadModel() throws IOException {
        model = new Tny2Reader().read(MODEL);
    }

    @Test
    @DisplayName("Answers the trained phrasings exactly")
    void shouldAnswerTheTrainedPhrasings() throws IOException {
        // When
        Tny2Evaluation.Result result = new Tny2Evaluation().evaluate(model, new FactsReader().read(FACTS));

        // Then
        assertThat(result.accuracy()).as("trained accuracy, misses %s", result.misses())
                .isGreaterThanOrEqualTo(MIN_TRAINED);
    }

    @Test
    @DisplayName("Answers unseen wordings of the facts")
    void shouldGeneralizeToUnseenWordings() throws IOException {
        // When
        Tny2Evaluation.Result result = new Tny2Evaluation().evaluate(model, new FactsReader().read(HELD_OUT));

        // Then
        assertThat(result.accuracy()).as("held-out accuracy, misses %s", result.misses())
                .isGreaterThanOrEqualTo(MIN_HELD_OUT);
    }
}
