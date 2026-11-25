package io.github.jabrena.juno.lm.training;

import io.github.jabrena.juno.lm.JlmConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Assigns every JLM1 tensor a {@link Slice} of one flat {@code float} array, in file order, so weights, gradients
 * and optimizer moments are plain arrays of the same size.
 */
final class ParameterLayout {

    /** The slices of one transformer block, in file order. */
    record Block(Slice attentionGain, Slice query, Slice key, Slice value, Slice output, Slice feedForwardGain,
            Slice up, Slice down) {
    }

    private final JlmConfig config;
    private final Slice embedding;
    private final Slice position;
    private final List<Block> blocks;
    private final Slice finalGain;
    private final int size;
    private int next;

    ParameterLayout(JlmConfig config) {
        this.config = config;
        int width = config.width();
        int hidden = config.hidden();
        embedding = allocate(config.vocabulary().size(), width);
        position = allocate(config.context(), width);
        List<Block> layers = new ArrayList<>(config.layers());
        for (int l = 0; l < config.layers(); l++) {
            layers.add(new Block(allocate(1, width), allocate(width, width), allocate(width, width),
                    allocate(width, width), allocate(width, width), allocate(1, width), allocate(hidden, width),
                    allocate(width, hidden)));
        }
        blocks = List.copyOf(layers);
        finalGain = allocate(1, width);
        size = next;
    }

    private Slice allocate(int rows, int cols) {
        Slice slice = new Slice(next, rows, cols);
        next = slice.end();
        return slice;
    }

    JlmConfig config() {
        return config;
    }

    Slice embedding() {
        return embedding;
    }

    Slice position() {
        return position;
    }

    List<Block> blocks() {
        return blocks;
    }

    Slice finalGain() {
        return finalGain;
    }

    int size() {
        return size;
    }

    /** True for every matrix value and false for the RMS gains, which weight decay must leave alone. */
    boolean[] decayMask() {
        boolean[] mask = new boolean[size];
        Arrays.fill(mask, true);
        for (Block block : blocks) {
            Arrays.fill(mask, block.attentionGain().offset(), block.attentionGain().end(), false);
            Arrays.fill(mask, block.feedForwardGain().offset(), block.feedForwardGain().end(), false);
        }
        Arrays.fill(mask, finalGain.offset(), finalGain.end(), false);
        return mask;
    }
}
