package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.corpus.Example;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Sequence pass")
class SequencePassTest {
    private static final Tny2Config SMALL = new Tny2Config(8, 2, 2, 12);
    private static final Example EXAMPLE = new Example("Hi there?", "Yes.");
    private static final float STEP = 1e-2f;

    @Test
    @DisplayName("Back-propagates the same gradient as finite differences, for every tensor")
    void shouldBackPropagateTheSameGradientAsFiniteDifferences() {
        // Given
        Tny2Network network = Tny2Network.initialize(SMALL, new SplittableRandom(7));
        ParameterLayout layout = network.layout();
        float[] weights = network.weights();
        float[] gradient = new float[layout.size()];
        SequencePass pass = new SequencePass(layout);

        // When
        pass.accumulate(EXAMPLE, weights, gradient);

        // Then
        ParameterLayout.Block block = layout.blocks().get(0);
        List<Slice> slices = List.of(layout.embedding(), layout.position(), block.attentionGain(), block.query(),
                block.key(), block.value(), block.output(), block.feedForwardGain(), block.up(), block.down(),
                layout.blocks().get(1).up(), layout.finalGain());
        SplittableRandom pick = new SplittableRandom(3);
        for (Slice slice : slices) {
            for (int sample = 0; sample < 4; sample++) {
                int index = slice.offset() + pick.nextInt(slice.size());
                double numeric = numericGradient(pass, weights, index);
                assertThat((double) gradient[index]).as("gradient of %s at %d", slice, index)
                        .isCloseTo(numeric, within(2e-3 + 2e-2 * Math.abs(numeric)));
            }
        }
    }

    @Test
    @DisplayName("Counts the answer and its closing END as targets")
    void shouldCountTheAnswerAndItsClosingEndAsTargets() {
        // When & Then
        assertThat(SequencePass.targets(EXAMPLE)).isEqualTo("Yes.".length() + 1);
    }

    @Test
    @DisplayName("Starts near the uniform loss and gives the same loss on every call")
    void shouldStartNearTheUniformLossAndRepeatIt() {
        // Given
        Tny2Network network = Tny2Network.initialize(SMALL, new SplittableRandom(7));
        SequencePass pass = new SequencePass(network.layout());

        // When
        double first = pass.accumulate(EXAMPLE, network.weights(), new float[network.layout().size()]);
        double second = pass.accumulate(EXAMPLE, network.weights(), new float[network.layout().size()]);

        // Then
        double uniform = SequencePass.targets(EXAMPLE) * Math.log(97);
        assertThat(first).isEqualTo(second).isCloseTo(uniform, within(uniform * 0.2));
    }

    @Test
    @DisplayName("Dropout changes the loss, reproducibly for a seed, and no dropout matches the plain pass")
    void shouldDropBranchOutputsReproducibly() {
        // Given
        Tny2Network network = Tny2Network.initialize(SMALL, new SplittableRandom(7));
        float[] weights = network.weights();
        int size = network.layout().size();

        // When
        double plain = new SequencePass(network.layout()).accumulate(EXAMPLE, weights, new float[size]);
        double none = new SequencePass(network.layout(), 0f, new SplittableRandom(1))
                .accumulate(EXAMPLE, weights, new float[size]);
        double first = new SequencePass(network.layout(), 0.5f, new SplittableRandom(1))
                .accumulate(EXAMPLE, weights, new float[size]);
        double second = new SequencePass(network.layout(), 0.5f, new SplittableRandom(1))
                .accumulate(EXAMPLE, weights, new float[size]);

        // Then
        assertThat(none).isEqualTo(plain);
        assertThat(first).isEqualTo(second).isNotEqualTo(plain);
    }

    private static double numericGradient(SequencePass pass, float[] weights, int index) {
        float original = weights[index];
        float[] scratch = new float[weights.length];
        weights[index] = original + STEP;
        double plus = pass.accumulate(EXAMPLE, weights, scratch);
        weights[index] = original - STEP;
        double minus = pass.accumulate(EXAMPLE, weights, scratch);
        weights[index] = original;
        return (plus - minus) / (2 * STEP);
    }
}
