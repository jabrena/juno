package io.github.jabrena.juno.lm;

import java.util.List;

/**
 * A complete quantized JLM1 model. The token embedding is tied: it also projects the final state onto the
 * vocabulary.
 *
 * @param config the header: dimensions, context and vocabulary
 * @param embedding token embedding, vocabulary size by width
 * @param position position embedding, {@link JlmConfig#context()} by width
 * @param layers the transformer blocks, one per {@link JlmConfig#layers()}
 * @param finalGain gains of the RMS normalization before the vocabulary projection
 */
public record JlmModel(JlmConfig config, QuantizedTable embedding, QuantizedTable position, List<JlmLayer> layers,
        GainVector finalGain) {

    public JlmModel {
        layers = List.copyOf(layers);
        checkTable("embedding", embedding, config.vocabulary().size(), config.width());
        checkTable("position", position, config.context(), config.width());
        if (layers.size() != config.layers()) {
            throw new JlmFormatException(
                    "model declares %d layers but has %d".formatted(config.layers(), layers.size()));
        }
        layers.forEach(layer -> layer.checkShape(config));
        checkGain("final gain", finalGain, config.width());
    }

    static void checkTable(String name, QuantizedTable table, int rows, int cols) {
        if (table.rows() != rows || table.cols() != cols) {
            throw new JlmFormatException("%s table must be %dx%d, not %dx%d".formatted(name, rows, cols,
                    table.rows(), table.cols()));
        }
    }

    static void checkGain(String name, GainVector gain, int size) {
        if (gain.size() != size) {
            throw new JlmFormatException("%s must have %d values, not %d".formatted(name, size, gain.size()));
        }
    }
}
