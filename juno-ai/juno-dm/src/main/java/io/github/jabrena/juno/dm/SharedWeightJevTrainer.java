package io.github.jabrena.juno.dm;

import java.util.List;

/**
 * Deterministic averaged-perceptron trainer for models whose state holds one block of features per action, describing
 * what that action would do: every action shares the same block weights, so what is learned about one action applies
 * to all of them.
 *
 * <p>The feature vector has {@code actionCount × blockSize} entries, action {@code a}'s block starting at
 * {@code a × blockSize}. The trained {@link JevModel} gives action {@code a} the shared weights on its own block and
 * zero elsewhere, so the board evaluates it like any other JEV model. The returned weights are the average over every
 * training step, which settles where the last perceptron iterate would keep oscillating on noisy labels.
 */
public final class SharedWeightJevTrainer {
    private final int actionCount;
    private final int blockSize;
    private final int epochs;

    public SharedWeightJevTrainer(int actionCount, int blockSize, int epochs) {
        if (actionCount <= 0 || blockSize <= 0 || epochs <= 0) {
            throw new JevModelException("trainer dimensions and epochs must be positive");
        }
        this.actionCount = actionCount;
        this.blockSize = blockSize;
        this.epochs = epochs;
    }

    public JevModel train(List<JevTrainingExample> examples) {
        if (examples.isEmpty()) {
            throw new JevModelException("training needs at least one example");
        }
        double[] weights = new double[blockSize];
        double[] sum = new double[blockSize];
        long steps = 0;
        for (int epoch = 0; epoch < epochs; epoch++) {
            for (JevTrainingExample example : examples) {
                float[] features = example.features();
                int[] legalActions = example.legalActions();
                validate(example, features, legalActions);
                int selected = select(weights, features, legalActions);
                if (selected != example.expectedAction()) {
                    int expectedBase = example.expectedAction() * blockSize;
                    int selectedBase = selected * blockSize;
                    for (int feature = 0; feature < blockSize; feature++) {
                        weights[feature] += features[expectedBase + feature] - features[selectedBase + feature];
                    }
                }
                for (int feature = 0; feature < blockSize; feature++) {
                    sum[feature] += weights[feature];
                }
                steps++;
            }
        }
        float[][] rows = new float[actionCount][actionCount * blockSize];
        for (int action = 0; action < actionCount; action++) {
            for (int feature = 0; feature < blockSize; feature++) {
                rows[action][action * blockSize + feature] = (float) (sum[feature] / steps);
            }
        }
        return new JevModel(rows, new float[actionCount]);
    }

    private int select(double[] weights, float[] features, int[] legalActions) {
        int best = legalActions[0];
        double bestScore = score(weights, features, best);
        for (int index = 1; index < legalActions.length; index++) {
            double score = score(weights, features, legalActions[index]);
            if (score > bestScore) {
                best = legalActions[index];
                bestScore = score;
            }
        }
        return best;
    }

    private double score(double[] weights, float[] features, int action) {
        double score = 0;
        int base = action * blockSize;
        for (int feature = 0; feature < blockSize; feature++) {
            score += weights[feature] * features[base + feature];
        }
        return score;
    }

    private void validate(JevTrainingExample example, float[] features, int[] legalActions) {
        if (features.length != actionCount * blockSize) {
            throw new JevModelException("example feature count must be %d, not %d"
                    .formatted(actionCount * blockSize, features.length));
        }
        if (legalActions.length == 0) {
            throw new JevModelException("example needs at least one legal action");
        }
        boolean expectedIsLegal = false;
        for (int action : legalActions) {
            if (action < 0 || action >= actionCount) {
                throw new JevModelException("example contains an action outside the model");
            }
            expectedIsLegal |= action == example.expectedAction();
        }
        if (!expectedIsLegal) {
            throw new JevModelException("expected action must be legal");
        }
    }
}
