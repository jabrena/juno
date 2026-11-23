package io.github.jabrena.juno.tny2;

import java.util.List;

/**
 * A complete quantized TNY2 model. The token embedding is tied: it also projects the final state onto the
 * vocabulary.
 *
 * @param config the header dimensions
 * @param embedding token embedding, vocabulary size by width
 * @param position position embedding, {@link Tny2Config#CONTEXT} by width
 * @param layers the transformer blocks, one per {@link Tny2Config#layers()}
 * @param finalGain gains of the RMS normalization before the vocabulary projection
 */
public record Tny2Model(Tny2Config config, QuantizedTable embedding, QuantizedTable position, List<Tny2Layer> layers,
        GainVector finalGain) {

    public Tny2Model {
        layers = List.copyOf(layers);
        checkTable("embedding", embedding, Tny2Vocabulary.SIZE, config.width());
        checkTable("position", position, Tny2Config.CONTEXT, config.width());
        if (layers.size() != config.layers()) {
            throw new Tny2FormatException(
                    "model declares %d layers but has %d".formatted(config.layers(), layers.size()));
        }
        layers.forEach(layer -> layer.checkShape(config));
        checkGain("final gain", finalGain, config.width());
    }

    static void checkTable(String name, QuantizedTable table, int rows, int cols) {
        if (table.rows() != rows || table.cols() != cols) {
            throw new Tny2FormatException("%s table must be %dx%d, not %dx%d".formatted(name, rows, cols,
                    table.rows(), table.cols()));
        }
    }

    static void checkGain(String name, GainVector gain, int size) {
        if (gain.size() != size) {
            throw new Tny2FormatException("%s must have %d values, not %d".formatted(name, size, gain.size()));
        }
    }
}
