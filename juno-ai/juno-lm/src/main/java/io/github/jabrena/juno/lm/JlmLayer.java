package io.github.jabrena.juno.lm;

/**
 * One transformer block, in file order: normalization, attention projections, normalization, feed-forward.
 *
 * @param attentionGain gains of the RMS normalization before attention
 * @param query query projection, width by width
 * @param key key projection, width by width
 * @param value value projection, width by width
 * @param output attention output projection, width by width
 * @param feedForwardGain gains of the RMS normalization before the feed-forward block
 * @param up feed-forward expansion, hidden by width, followed by ReLU
 * @param down feed-forward contraction, width by hidden
 */
public record JlmLayer(GainVector attentionGain, QuantizedTable query, QuantizedTable key, QuantizedTable value,
        QuantizedTable output, GainVector feedForwardGain, QuantizedTable up, QuantizedTable down) {

    void checkShape(JlmConfig config) {
        int width = config.width();
        int hidden = config.hidden();
        JlmModel.checkGain("attention gain", attentionGain, width);
        JlmModel.checkTable("query", query, width, width);
        JlmModel.checkTable("key", key, width, width);
        JlmModel.checkTable("value", value, width, width);
        JlmModel.checkTable("output", output, width, width);
        JlmModel.checkGain("feed-forward gain", feedForwardGain, width);
        JlmModel.checkTable("up", up, hidden, width);
        JlmModel.checkTable("down", down, width, hidden);
    }
}
