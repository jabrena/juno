package io.github.jabrena.juno.tny2;

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
public record Tny2Layer(GainVector attentionGain, QuantizedTable query, QuantizedTable key, QuantizedTable value,
        QuantizedTable output, GainVector feedForwardGain, QuantizedTable up, QuantizedTable down) {

    void checkShape(Tny2Config config) {
        int width = config.width();
        int hidden = config.hidden();
        Tny2Model.checkGain("attention gain", attentionGain, width);
        Tny2Model.checkTable("query", query, width, width);
        Tny2Model.checkTable("key", key, width, width);
        Tny2Model.checkTable("value", value, width, width);
        Tny2Model.checkTable("output", output, width, width);
        Tny2Model.checkGain("feed-forward gain", feedForwardGain, width);
        Tny2Model.checkTable("up", up, hidden, width);
        Tny2Model.checkTable("down", down, width, hidden);
    }
}
