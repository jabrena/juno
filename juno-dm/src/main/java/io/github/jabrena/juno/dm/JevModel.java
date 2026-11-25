package io.github.jabrena.juno.dm;

import java.util.Arrays;

/** A dense linear decision model whose rows score actions from a fixed feature vector. */
public final class JevModel {
    private final float[][] weights;
    private final float[] biases;

    public JevModel(float[][] weights, float[] biases) {
        if (weights.length == 0) {
            throw new JevModelException("a JEV model needs at least one action");
        }
        if (weights[0].length == 0) {
            throw new JevModelException("a JEV model needs at least one feature");
        }
        if (biases.length != weights.length) {
            throw new JevModelException("bias count must equal action count");
        }
        this.weights = new float[weights.length][];
        for (int action = 0; action < weights.length; action++) {
            if (weights[action].length != weights[0].length) {
                throw new JevModelException("all action rows must have the same feature count");
            }
            this.weights[action] = weights[action].clone();
        }
        this.biases = biases.clone();
    }

    public int actionCount() {
        return weights.length;
    }

    public int featureCount() {
        return weights[0].length;
    }

    /** Scores one action for a feature vector. The caller owns and reuses {@code features}. */
    public float score(int action, float[] features, int featureCount) {
        checkFeatures(features, featureCount);
        checkAction(action);
        float score = biases[action];
        for (int feature = 0; feature < featureCount; feature++) {
            score += weights[action][feature] * features[feature];
        }
        return score;
    }

    float weight(int action, int feature) {
        return weights[action][feature];
    }

    float bias(int action) {
        return biases[action];
    }

    private void checkFeatures(float[] features, int featureCount) {
        if (featureCount != featureCount() || features.length < featureCount) {
            throw new JevModelException(
                    "feature count must be %d, not %d".formatted(featureCount(), featureCount));
        }
    }

    private void checkAction(int action) {
        if (action < 0 || action >= actionCount()) {
            throw new JevModelException("action must be between 0 and %d, not %d".formatted(actionCount() - 1, action));
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JevModel model
                && Arrays.deepEquals(weights, model.weights)
                && Arrays.equals(biases, model.biases);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.deepHashCode(weights) + Arrays.hashCode(biases);
    }
}
