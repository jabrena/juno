package io.github.jabrena.juno.lm.training;

import io.github.jabrena.juno.lm.GainVector;
import io.github.jabrena.juno.lm.JlmConfig;
import io.github.jabrena.juno.lm.JlmLayer;
import io.github.jabrena.juno.lm.JlmModel;
import io.github.jabrena.juno.lm.QuantizedTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The full-precision weights of a JLM1 model during training, in one flat array laid out by
 * {@link ParameterLayout}. {@link #quantize()} turns it into the signed-byte model the board runs.
 */
public final class JlmNetwork {
    private static final float EMBEDDING_STD = 0.1f;

    private final ParameterLayout layout;
    private final float[] weights;

    private JlmNetwork(ParameterLayout layout, float[] weights) {
        this.layout = layout;
        this.weights = weights;
    }

    /**
     * A fresh network: gains of 1, embeddings with standard deviation 0.1, and matrices with standard deviation
     * {@code 1 / sqrt(inputs)}, scaled down by {@code sqrt(2 * layers)} on the two projections that write into the
     * residual stream so the stream starts small.
     */
    public static JlmNetwork initialize(JlmConfig config, RandomGenerator random) {
        ParameterLayout layout = new ParameterLayout(config);
        float[] weights = new float[layout.size()];
        gaussian(weights, layout.embedding(), EMBEDDING_STD, random);
        gaussian(weights, layout.position(), EMBEDDING_STD, random);
        float residualScale = (float) Math.sqrt(2.0 * config.layers());
        for (ParameterLayout.Block block : layout.blocks()) {
            ones(weights, block.attentionGain());
            fanIn(weights, block.query(), 1f, random);
            fanIn(weights, block.key(), 1f, random);
            fanIn(weights, block.value(), 1f, random);
            fanIn(weights, block.output(), residualScale, random);
            ones(weights, block.feedForwardGain());
            fanIn(weights, block.up(), 1f, random);
            fanIn(weights, block.down(), residualScale, random);
        }
        ones(weights, layout.finalGain());
        return new JlmNetwork(layout, weights);
    }

    /** A network that continues from a quantized model, for example to fine-tune the bundled JUNO.BIN. */
    public static JlmNetwork from(JlmModel model) {
        ParameterLayout layout = new ParameterLayout(model.config());
        float[] weights = new float[layout.size()];
        model.embedding().dequantize(weights, layout.embedding().offset());
        model.position().dequantize(weights, layout.position().offset());
        for (int l = 0; l < model.layers().size(); l++) {
            JlmLayer layer = model.layers().get(l);
            ParameterLayout.Block block = layout.blocks().get(l);
            layer.attentionGain().copyTo(weights, block.attentionGain().offset());
            layer.query().dequantize(weights, block.query().offset());
            layer.key().dequantize(weights, block.key().offset());
            layer.value().dequantize(weights, block.value().offset());
            layer.output().dequantize(weights, block.output().offset());
            layer.feedForwardGain().copyTo(weights, block.feedForwardGain().offset());
            layer.up().dequantize(weights, block.up().offset());
            layer.down().dequantize(weights, block.down().offset());
        }
        model.finalGain().copyTo(weights, layout.finalGain().offset());
        return new JlmNetwork(layout, weights);
    }

    /** Quantizes every matrix per row to signed bytes; gains stay {@code float}. */
    public JlmModel quantize() {
        List<JlmLayer> layers = new ArrayList<>(layout.blocks().size());
        for (ParameterLayout.Block block : layout.blocks()) {
            layers.add(new JlmLayer(gains(block.attentionGain()), table(block.query()), table(block.key()),
                    table(block.value()), table(block.output()), gains(block.feedForwardGain()), table(block.up()),
                    table(block.down())));
        }
        return new JlmModel(layout.config(), table(layout.embedding()), table(layout.position()), layers,
                gains(layout.finalGain()));
    }

    public JlmConfig config() {
        return layout.config();
    }

    ParameterLayout layout() {
        return layout;
    }

    /** The live weight array, updated in place by the optimizer. */
    float[] weights() {
        return weights;
    }

    private QuantizedTable table(Slice slice) {
        return QuantizedTable.quantize(slice.rows(), slice.cols(), weights, slice.offset());
    }

    private GainVector gains(Slice slice) {
        return new GainVector(Arrays.copyOfRange(weights, slice.offset(), slice.end()));
    }

    private static void ones(float[] weights, Slice slice) {
        Arrays.fill(weights, slice.offset(), slice.end(), 1f);
    }

    private static void fanIn(float[] weights, Slice slice, float divisor, RandomGenerator random) {
        gaussian(weights, slice, (float) (1.0 / Math.sqrt(slice.cols()) / divisor), random);
    }

    private static void gaussian(float[] weights, Slice slice, float std, RandomGenerator random) {
        for (int i = slice.offset(); i < slice.end(); i++) {
            weights[i] = (float) random.nextGaussian() * std;
        }
    }
}
