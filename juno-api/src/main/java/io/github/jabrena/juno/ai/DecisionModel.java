package io.github.jabrena.juno.ai;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

/**
 * The board's embedded decision-model engine: a JEV1 model that picks one action among the legal ones for a state.
 *
 * <p>Take the engine from its board once, at startup, with
 * {@link io.github.jabrena.juno.hardware.MCU#getDecisionModel()}, and {@link #begin()} it: the build links the JEV1
 * file named by {@code -Djuno.dm.model} into the firmware, so one {@code juno:flash} uploads the program and its model
 * together. {@link #begin(SdCard, String)} loads another JEV1 file from an SD card into PSRAM instead, so a model can
 * change without reflashing. Then {@link #evaluate(int[], int[])} as often as needed: evaluation reads the caller-owned
 * state and legal-action buffers and allocates nothing.
 */
public interface DecisionModel {
    /**
     * Uses the JEV1 model the build linked into the firmware, replacing any model loaded before. A program that calls
     * this method must be built with {@code -Djuno.dm.model=<file>}, or the build fails. Returns
     * {@link ModelStatus#OK}, or {@link ModelStatus#INVALID_MODEL} when the engine refuses the linked file.
     */
    ModelStatus begin();

    /**
     * Loads the JEV1 file at {@code path} on {@code card}, which must already be mounted with its own
     * {@link SdCard#begin()}, into PSRAM, replacing any model loaded before. The file is copied through a 1 KiB buffer,
     * so call it once, at startup; it needs a board with PSRAM. Returns {@link ModelStatus#OK},
     * {@link ModelStatus#UNREADABLE} for a missing or unreadable file, {@link ModelStatus#EXHAUSTED} when PSRAM has no
     * room for it, or {@link ModelStatus#INVALID_MODEL} when the engine refuses it.
     */
    ModelStatus begin(SdCard card, String path);

    /** Whether a model is loaded. */
    boolean isLoaded();

    /** The loaded model's number of actions, or 0. */
    int actions();

    /** The loaded model's number of state features, which is the length {@link #evaluate} expects, or 0. */
    int features();

    /**
     * Returns the selected action from {@code legalActions} for {@code state}, or -1 when no model is loaded, the
     * state does not have {@link #features()} entries or no legal action is one of the model's.
     *
     * <p>The buffers are read-only to the engine and remain owned by the caller.
     */
    int evaluate(int[] state, int[] legalActions);

    /** Unloads the model and frees its PSRAM; {@link #isLoaded()} is false afterwards. */
    void release();
}
