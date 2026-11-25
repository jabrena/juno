package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.DecisionModel;

/** The compiler intrinsic behind {@link io.github.jabrena.juno.hardware.MCU#getDecisionModel()}. */
public final class NativeDecisionModel implements DecisionModel {
    private static final NativeDecisionModel INSTANCE = new NativeDecisionModel();

    private NativeDecisionModel() {
    }

    /** The selected board's shared decision-model engine. */
    public static DecisionModel decisionModel() {
        return INSTANCE;
    }

    /** Runs the linked JEV model over caller-owned buffers. */
    @Override
    public native int evaluate(int[] state, int[] legalActions);
}
