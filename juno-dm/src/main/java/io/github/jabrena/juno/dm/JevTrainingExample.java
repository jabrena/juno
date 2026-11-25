package io.github.jabrena.juno.dm;

/** One labelled state used by the host-side JEV trainer. */
public final class JevTrainingExample {
    private final float[] features;
    private final int[] legalActions;
    private final int expectedAction;

    public JevTrainingExample(float[] features, int[] legalActions, int expectedAction) {
        this.features = features.clone();
        this.legalActions = legalActions.clone();
        this.expectedAction = expectedAction;
    }

    public float[] features() {
        return features.clone();
    }

    public int[] legalActions() {
        return legalActions.clone();
    }

    public int expectedAction() {
        return expectedAction;
    }
}
