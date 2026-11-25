package io.github.jabrena.juno.dm;

/** An INT8-weight JEV model with one dequantization scale per action. */
public final class QuantizedJevModel {
    private final byte[][] weights;
    private final float[] scales;
    private final float[] biases;

    private QuantizedJevModel(byte[][] weights, float[] scales, float[] biases) {
        if (weights.length == 0 || weights.length != scales.length || weights.length != biases.length) {
            throw new JevModelException("quantized model dimensions must agree and contain an action");
        }
        int featureCount = weights[0].length;
        if (featureCount == 0) {
            throw new JevModelException("quantized model must contain a feature");
        }
        this.weights = new byte[weights.length][];
        for (int action = 0; action < weights.length; action++) {
            if (weights[action].length != featureCount || scales[action] <= 0f) {
                throw new JevModelException("quantized model rows must have equal width and positive scales");
            }
            this.weights[action] = weights[action].clone();
        }
        this.scales = scales.clone();
        this.biases = biases.clone();
    }

    public static QuantizedJevModel from(JevModel model) {
        byte[][] weights = new byte[model.actionCount()][model.featureCount()];
        float[] scales = new float[model.actionCount()];
        float[] biases = new float[model.actionCount()];
        for (int action = 0; action < model.actionCount(); action++) {
            float largest = 0f;
            for (int feature = 0; feature < model.featureCount(); feature++) {
                largest = Math.max(largest, Math.abs(model.weight(action, feature)));
            }
            float scale = largest == 0f ? 1f : largest / Byte.MAX_VALUE;
            scales[action] = scale;
            biases[action] = model.bias(action);
            for (int feature = 0; feature < model.featureCount(); feature++) {
                weights[action][feature] = (byte) Math.round(model.weight(action, feature) / scale);
            }
        }
        return new QuantizedJevModel(weights, scales, biases);
    }

    static QuantizedJevModel encoded(byte[][] weights, float[] scales, float[] biases) {
        return new QuantizedJevModel(weights, scales, biases);
    }

    public int actionCount() {
        return weights.length;
    }

    public int featureCount() {
        return weights[0].length;
    }

    byte weight(int action, int feature) {
        return weights[action][feature];
    }

    float scale(int action) {
        return scales[action];
    }

    float bias(int action) {
        return biases[action];
    }

    float score(int action, float[] features, int featureCount) {
        if (featureCount != featureCount() || features.length < featureCount) {
            throw new JevModelException(
                    "feature count must be %d, not %d".formatted(featureCount(), featureCount));
        }
        if (action < 0 || action >= actionCount()) {
            throw new JevModelException("action must be between 0 and %d, not %d".formatted(actionCount() - 1, action));
        }
        float score = biases[action];
        for (int feature = 0; feature < featureCount; feature++) {
            score += weights[action][feature] * scales[action] * features[feature];
        }
        return score;
    }
}
