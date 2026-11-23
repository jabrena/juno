package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Model;
import io.github.jabrena.juno.tny2.TrainedModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("TNY2 network, layout and optimizer")
class Tny2NetworkTest {
    private static final Tny2Config SMALL = new Tny2Config(8, 2, 2, 12);

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
        Tny2Network network = Tny2Network.initialize(SMALL, new SplittableRandom(1));

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
        Tny2Model model = TrainedModels.model();

        // When
        Tny2Model again = Tny2Network.from(model).quantize();

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
