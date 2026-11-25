package io.github.jabrena.juno.dm;

import java.util.List;

/** Deterministic perceptron trainer for the first JEV decision-model experiments. */
public final class JevTrainer {
    private final int actionCount;
    private final int featureCount;
    private final float learningRate;
    private final int epochs;

    public JevTrainer(int actionCount, int featureCount, float learningRate, int epochs) {
        if (actionCount <= 0 || featureCount <= 0 || learningRate <= 0f || epochs <= 0) {
            throw new JevModelException("trainer dimensions, learning rate and epochs must be positive");
        }
        this.actionCount = actionCount;
        this.featureCount = featureCount;
        this.learningRate = learningRate;
        this.epochs = epochs;
    }

    public JevModel train(List<JevTrainingExample> examples) {
        if (examples.isEmpty()) {
            throw new JevModelException("training needs at least one example");
        }
        float[][] weights = new float[actionCount][featureCount];
        float[] biases = new float[actionCount];
        for (int epoch = 0; epoch < epochs; epoch++) {
            var model = new JevModel(weights, biases);
            var inference = new JevInference(model);
            for (JevTrainingExample example : examples) {
                float[] features = example.features();
                int[] legalActions = example.legalActions();
                validate(example, features, legalActions);
                int selected = inference.evaluate(features, legalActions, legalActions.length);
                if (selected == example.expectedAction()) {
                    continue;
                }
                update(weights, biases, example.expectedAction(), features, learningRate);
                update(weights, biases, selected, features, -learningRate);
            }
        }
        return new JevModel(weights, biases);
    }

    private void validate(JevTrainingExample example, float[] features, int[] legalActions) {
        if (features.length != featureCount) {
            throw new JevModelException("example feature count must be %d, not %d".formatted(featureCount, features.length));
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

    private void update(float[][] weights, float[] biases, int action, float[] features, float amount) {
        biases[action] += amount;
        for (int feature = 0; feature < featureCount; feature++) {
            weights[action][feature] += amount * features[feature];
        }
    }
}
