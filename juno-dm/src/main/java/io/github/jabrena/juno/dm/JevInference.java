package io.github.jabrena.juno.dm;

/** Selects the highest-scoring legal action without allocating during evaluation. */
public final class JevInference {
    private final JevModel model;

    public JevInference(JevModel model) {
        this.model = model;
    }

    /**
     * Returns the highest-scoring action from the first {@code legalActionCount} entries of
     * {@code legalActions}. Ties preserve the first legal action.
     */
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
