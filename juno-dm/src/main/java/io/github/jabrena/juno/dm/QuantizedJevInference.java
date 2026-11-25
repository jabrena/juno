package io.github.jabrena.juno.dm;

/** Selects legal actions using an INT8-weight JEV model without allocating during evaluation. */
public final class QuantizedJevInference {
    private final QuantizedJevModel model;

    public QuantizedJevInference(QuantizedJevModel model) {
        this.model = model;
    }

    public int evaluate(float[] features, int[] legalActions, int legalActionCount) {
        if (legalActionCount <= 0 || legalActionCount > legalActions.length) {
            throw new JevModelException("legal action count must be positive and within the action buffer");
        }
        int bestAction = legalActions[0];
        float bestScore = model.score(bestAction, features, model.featureCount());
        for (int index = 1; index < legalActionCount; index++) {
            int action = legalActions[index];
            float score = model.score(action, features, model.featureCount());
            if (score > bestScore) {
                bestAction = action;
                bestScore = score;
            }
        }
        return bestAction;
    }
}
