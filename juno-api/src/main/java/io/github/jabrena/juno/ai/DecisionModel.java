package io.github.jabrena.juno.ai;

/**
 * The board's embedded decision-model engine. The compiler links the model selected by the program into the
 * firmware; evaluation reads the caller-owned state and legal-action buffers and allocates nothing.
 */
public interface DecisionModel {
    /**
     * Returns the selected action from {@code legalActions} for {@code state}.
     *
     * <p>The buffers are read-only to the engine and remain owned by the caller. Both arrays must contain the model's
     * expected dimensions, and {@code legalActions} must contain at least one legal action.
     */
    int evaluate(int[] state, int[] legalActions);
}
