package io.github.jabrena.juno.dm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JevInferenceTest {
    @Test
    void selectsTheHighestScoringLegalAction() {
        var model = new JevModel(
                new float[][] {
                    {1f, 0f},
                    {0f, 2f},
                    {5f, 5f}
                },
                new float[] {0f, 0f, 0f});
        var inference = new JevInference(model);

        int selected = inference.evaluate(new float[] {1f, 1f}, new int[] {0, 1}, 2);

        assertThat(selected).isEqualTo(1);
    }

    @Test
    void ignoresAHigherScoringIllegalAction() {
        var model = new JevModel(
                new float[][] {
                    {1f, 0f},
                    {0f, 2f},
                    {5f, 5f}
                },
                new float[] {0f, 0f, 0f});
        var inference = new JevInference(model);

        int selected = inference.evaluate(new float[] {1f, 1f}, new int[] {0, 1}, 2);

        assertThat(selected).isNotEqualTo(2);
    }

    @Test
    void quantizedInferencePreservesTheSelectedAction() {
        var model = new JevModel(
                new float[][] {
                    {0.25f, 0.5f},
                    {0.5f, 0.75f},
                    {4f, 4f}
                },
                new float[] {0f, 0.1f, 0f});
        var inference = new QuantizedJevInference(QuantizedJevModel.from(model));

        int selected = inference.evaluate(new float[] {1f, 1f}, new int[] {0, 1}, 2);

        assertThat(selected).isEqualTo(1);
    }

    @Test
    void trainerLearnsASeparableTwoActionProblem() {
        var trainer = new JevTrainer(2, 2, 0.5f, 10);
        var model = trainer.train(List.of(
                new JevTrainingExample(new float[] {1f, 0f}, new int[] {0, 1}, 0),
                new JevTrainingExample(new float[] {0f, 1f}, new int[] {0, 1}, 1)));
        var inference = new JevInference(model);

        assertThat(inference.evaluate(new float[] {1f, 0f}, new int[] {0, 1}, 2)).isEqualTo(0);
        assertThat(inference.evaluate(new float[] {0f, 1f}, new int[] {0, 1}, 2)).isEqualTo(1);
    }
}
