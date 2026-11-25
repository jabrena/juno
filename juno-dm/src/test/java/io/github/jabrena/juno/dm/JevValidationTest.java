package io.github.jabrena.juno.dm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JevValidationTest {
    @Test
    void rejectsInvalidModelShapesAndScores() {
        assertThatThrownBy(() -> new JevModel(new float[0][], new float[0]))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new JevModel(new float[][] {{}, {}}, new float[] {0f, 0f}))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new JevModel(new float[][] {{1f}}, new float[0]))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new JevModel(new float[][] {{1f}, {1f, 2f}}, new float[] {0f, 0f}))
                .isInstanceOf(JevModelException.class);

        var model = new JevModel(new float[][] {{1f}}, new float[] {0f});
        assertThatThrownBy(() -> model.score(0, new float[0], 1)).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> model.score(0, new float[] {1f}, 2)).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> model.score(1, new float[] {1f}, 1)).isInstanceOf(JevModelException.class);
        assertThat(model).isEqualTo(new JevModel(new float[][] {{1f}}, new float[] {0f}));
        assertThat(model).hasSameHashCodeAs(new JevModel(new float[][] {{1f}}, new float[] {0f}));
        assertThat(model).isNotEqualTo("not a model");
    }

    @Test
    void rejectsInvalidInferenceBuffersAndPreservesTies() {
        var model = new JevModel(new float[][] {{1f}, {1f}}, new float[] {0f, 0f});
        var inference = new JevInference(model);
        var quantized = new QuantizedJevInference(QuantizedJevModel.from(model));

        assertThat(inference.evaluate(new float[] {1f}, new int[] {0, 1}, 2)).isEqualTo(0);
        assertThatThrownBy(() -> inference.evaluate(new float[] {1f}, new int[] {0}, 0))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> inference.evaluate(new float[] {1f}, new int[] {0}, 2))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> quantized.evaluate(new float[] {1f}, new int[] {0}, 0))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> quantized.evaluate(new float[] {1f}, new int[] {2}, 1))
                .isInstanceOf(JevModelException.class);
    }

    @Test
    void validatesTrainerConfigurationAndExamples() {
        assertThatThrownBy(() -> new JevTrainer(0, 1, 1f, 1)).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new JevTrainer(1, 0, 1f, 1)).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new JevTrainer(1, 1, 0f, 1)).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> new JevTrainer(1, 1, 1f, 0)).isInstanceOf(JevModelException.class);

        var trainer = new JevTrainer(2, 1, 1f, 1);
        assertThatThrownBy(() -> trainer.train(List.of())).isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[0], new int[] {0}, 0))))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[] {1f}, new int[0], 0))))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[] {1f}, new int[] {2}, 0))))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> trainer.train(List.of(new JevTrainingExample(new float[] {1f}, new int[] {0}, 1))))
                .isInstanceOf(JevModelException.class);
    }

    @Test
    void quantizationHandlesAnAllZeroActionRowAndTrainingExampleCopiesBuffers() {
        var model = QuantizedJevModel.from(new JevModel(
                new float[][] {{0f, 0f}, {1f, 0f}}, new float[] {0f, 0f}));
        assertThat(model.score(0, new float[] {1f, 1f}, 2)).isZero();

        float[] features = {1f};
        int[] actions = {0, 1};
        var example = new JevTrainingExample(features, actions, 1);
        features[0] = 2f;
        actions[0] = 1;
        assertThat(example.features()).containsExactly(1f);
        assertThat(example.legalActions()).containsExactly(0, 1);
        assertThat(example.expectedAction()).isEqualTo(1);
    }

    @Test
    void rejectsJev1HeadersWithWrongMagicVersionDimensionsOrLength() {
        var codec = new JevModelCodec();
        assertThatThrownBy(() -> codec.read(new byte[] {'X', 'E', 'V', '1', 1, 0, 1, 0, 1}))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> codec.read(new byte[] {'J', 'E', 'V', '1', 2, 0, 1, 0, 1}))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> codec.read(new byte[] {'J', 'E', 'V', '1', 1, 0, 0, 0, 1}))
                .isInstanceOf(JevModelException.class);
        assertThatThrownBy(() -> codec.read(new byte[] {'J', 'E', 'V', '1', 1, 0, 1, 0, 1, 0}))
                .isInstanceOf(JevModelException.class);
    }
}
