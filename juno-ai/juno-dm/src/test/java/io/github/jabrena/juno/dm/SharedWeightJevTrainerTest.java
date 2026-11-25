package io.github.jabrena.juno.dm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedWeightJevTrainerTest {
    /** Three actions with two features each: the expert always picks the action whose first feature is set. */
    private static final List<JevTrainingExample> EXAMPLES = List.of(
            new JevTrainingExample(new float[] {1, 1, 0, 0, 0, 1}, new int[] {0, 1, 2}, 0),
            new JevTrainingExample(new float[] {0, 1, 1, 0, 0, 0}, new int[] {0, 1, 2}, 1),
            new JevTrainingExample(new float[] {0, 0, 0, 1, 1, 1}, new int[] {0, 1, 2}, 2),
            new JevTrainingExample(new float[] {0, 1, 1, 1, 0, 0}, new int[] {0, 1}, 1));

    @Test
    void learnsOneRuleForEveryAction() {
        JevModel model = new SharedWeightJevTrainer(3, 2, 10).train(EXAMPLES);
        var inference = new JevInference(model);

        for (JevTrainingExample example : EXAMPLES) {
            int[] legal = example.legalActions();
            assertThat(inference.evaluate(example.features(), legal, legal.length)).isEqualTo(example.expectedAction());
        }
    }

    @Test
    void givesEachActionTheSharedWeightsOnItsOwnBlockOnly() {
        JevModel model = new SharedWeightJevTrainer(3, 2, 10).train(EXAMPLES);

        assertThat(model.actionCount()).isEqualTo(3);
        assertThat(model.featureCount()).isEqualTo(6);
        for (int action = 0; action < 3; action++) {
            assertThat(model.bias(action)).isZero();
            for (int feature = 0; feature < 6; feature++) {
                float expected = feature / 2 == action ? model.weight(0, feature % 2) : 0f;
                assertThat(model.weight(action, feature)).isEqualTo(expected);
            }
        }
        assertThat(model.weight(0, 0)).isGreaterThan(model.weight(0, 1));
    }

    @Test
    void trainingIsDeterministic() {
        var trainer = new SharedWeightJevTrainer(3, 2, 10);

        assertThat(trainer.train(EXAMPLES)).isEqualTo(trainer.train(EXAMPLES));
    }

    @Test
    void rejectsInvalidDimensionsAndExamples() {
        assertThatThrownBy(() -> new SharedWeightJevTrainer(0, 2, 1)).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new SharedWeightJevTrainer(3, 0, 1)).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new SharedWeightJevTrainer(3, 2, 0)).isInstanceOf(JevModelException.class);
        var trainer = new SharedWeightJevTrainer(3, 2, 1);
        assertThatThrownBy(() -> trainer.train(List.of())).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[5], new int[] {0}, 0))))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[6], new int[0], 0))))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[6], new int[] {3}, 0))))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[6], new int[] {1}, 0))))
                .isInstanceOf(JevModelException.class);
    }
}
