package io.github.jabrena.juno.tny2.training;

import io.github.jabrena.juno.tny2.Tny2Config;
import io.github.jabrena.juno.tny2.Tny2Vocabulary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Assigns every TNY2 tensor a {@link Slice} of one flat {@code float} array, in file order, so weights, gradients
 * and optimizer moments are plain arrays of the same size.
 */
final class ParameterLayout {

    /** The slices of one transformer block, in file order. */
    record Block(Slice attentionGain, Slice query, Slice key, Slice value, Slice output, Slice feedForwardGain,
            Slice up, Slice down) {
    }

    private final Tny2Config config;
    private final Slice embedding;
    private final Slice position;
    private final List<Block> blocks;
    private final Slice finalGain;
    private final int size;
    private int next;

    ParameterLayout(Tny2Config config) {
        this.config = config;
        int width = config.width();
        int hidden = config.hidden();
        embedding = allocate(Tny2Vocabulary.SIZE, width);
        position = allocate(Tny2Config.CONTEXT, width);
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

    Tny2Config config() {
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
