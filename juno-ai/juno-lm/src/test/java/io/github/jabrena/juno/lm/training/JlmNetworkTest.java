package io.github.jabrena.juno.lm.training;

import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.JlmModel;
import io.github.jabrena.juno.lm.TrainedModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("JLM1 network, layout and optimizer")
class JlmNetworkTest {
    private static final JlmConfig SMALL = new JlmConfig(8, 2, 2, 12);

    @Test
    @DisplayName("Lays out every tensor in file order, back to back")
    void shouldLayOutEveryTensorInFileOrder() {
        // When
        ParameterLayout layout = new ParameterLayout(SMALL);

        // Then
        assertThat(layout.size()).isEqualTo(SMALL.parameterCount());
        assertThat(layout.embedding()).isEqualTo(new Slice(0, 97, 8));
        assertThat(layout.position()).isEqualTo(new Slice(776, 128, 8));
        ParameterLayout.Block first = layout.blocks().getFirst();
        assertThat(first.attentionGain()).isEqualTo(new Slice(1800, 1, 8));
        assertThat(first.up()).isEqualTo(new Slice(first.feedForwardGain().end(), 12, 8));
        assertThat(first.down().end()).isEqualTo(layout.blocks().get(1).attentionGain().offset());
        assertThat(layout.finalGain().end()).isEqualTo(layout.size());
    }

    @Test
    @DisplayName("Starts with gains of one and small, centered weights")
    void shouldStartWithUnitGainsAndSmallWeights() {
        // When
        JlmNetwork network = JlmNetwork.initialize(SMALL, new SplittableRandom(1));

        // Then
        ParameterLayout layout = network.layout();
        float[] weights = network.weights();
        Slice gain = layout.blocks().getFirst().feedForwardGain();
        for (int i = gain.offset(); i < gain.end(); i++) {
            assertThat(weights[i]).isEqualTo(1f);
        }
        assertThat(weights[layout.finalGain().offset()]).isEqualTo(1f);
        assertThat(standardDeviation(weights, layout.embedding())).isCloseTo(0.1, within(0.01));
        assertThat(standardDeviation(weights, layout.blocks().getFirst().query()))
                .isCloseTo(1 / Math.sqrt(8), within(0.06));
        assertThat(standardDeviation(weights, layout.blocks().getFirst().down()))
                .isCloseTo(1 / Math.sqrt(12) / 2, within(0.03));
        assertThat(network.config()).isEqualTo(SMALL);
    }

    @Test
    @DisplayName("Continues from a quantized model and quantizes back to the same model")
    void shouldContinueFromAQuantizedModel() {
        // Given
        JlmModel model = TrainedModels.model();

        // When
        JlmModel again = JlmNetwork.from(model).quantize();

        // Then
        assertThat(again).isEqualTo(model);
    }

    @Test
    @DisplayName("Adam moves each weight by about the learning rate against its gradient's sign")
    void shouldMoveEachWeightAgainstItsGradient() {
        // Given
        AdamOptimizer optimizer = new AdamOptimizer(3);
        float[] weights = {1f, 1f, 1f};

        // When
        optimizer.update(weights, new float[] {0.5f, -2f, 0f}, 0.1f);
        optimizer.update(weights, new float[] {0.5f, -2f, 0f}, 0.1f);

        // Then
        assertThat(weights[0]).isCloseTo(0.8f, within(1e-4f));
        assertThat(weights[1]).isCloseTo(1.2f, within(1e-4f));
        assertThat(weights[2]).isEqualTo(1f);
    }

    @Test
    @DisplayName("AdamW shrinks the masked weights by the decay and leaves the others to Adam")
    void shouldDecayOnlyTheMaskedWeights() {
        // Given
        AdamOptimizer optimizer = new AdamOptimizer(2, 0.5f, new boolean[] {true, false});
        float[] weights = {2f, 2f};

        // When
        optimizer.update(weights, new float[] {0f, 0f}, 0.1f);

        // Then
        assertThat(weights[0]).isCloseTo(2f - 0.1f * 0.5f * 2f, within(1e-6f));
        assertThat(weights[1]).isEqualTo(2f);
    }

    @Test
    @DisplayName("The decay mask covers the matrices and leaves every gain out")
    void shouldMaskTheMatricesButNotTheGains() {
        // When
        ParameterLayout layout = new ParameterLayout(SMALL);
        boolean[] mask = layout.decayMask();

        // Then
        ParameterLayout.Block block = layout.blocks().getFirst();
        assertThat(mask[layout.embedding().offset()]).isTrue();
        assertThat(mask[block.query().offset()]).isTrue();
        assertThat(mask[block.attentionGain().offset()]).isFalse();
        assertThat(mask[block.feedForwardGain().end() - 1]).isFalse();
        assertThat(mask[layout.finalGain().offset()]).isFalse();
        assertThat(mask[block.down().end() - 1]).isTrue();
    }

    private static double standardDeviation(float[] weights, Slice slice) {
        double sum = 0;
        double squares = 0;
        for (int i = slice.offset(); i < slice.end(); i++) {
            sum += weights[i];
            squares += weights[i] * weights[i];
        }
        double mean = sum / slice.size();
        return Math.sqrt(squares / slice.size() - mean * mean);
    }
}
