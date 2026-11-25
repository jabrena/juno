package io.github.jabrena.juno.hardware.internal;

/**
 * What the decision-model engine needs from the board: the native JEV1 evaluator, which works on whichever model it
 * was pointed at, and PSRAM buffers for a model loaded from an SD card. The begin methods return {@code ModelStatus}
 * ordinals.
 */
public interface DecisionModelRuntime {
    /** Points the evaluator at the JEV1 table the build linked into flash. */
    int beginLinked();

    /**
     * Points the evaluator at the {@code size} JEV1 bytes in PSRAM buffer {@code handle}, which the runtime then owns
     * and frees on {@link #unload()}. A refused buffer stays the caller's.
     */
    int beginPsram(int handle, int size);

    /** The loaded model's action count, or 0. */
    int actionCount();

    /** The loaded model's feature count, or 0. */
    int featureCount();

    /** The selected action from {@code legalActions} for {@code state}, or -1. */
    int evaluate(int[] state, int[] legalActions);

    /** Detaches the evaluator from its model and frees the PSRAM copy it owns, if any. */
    void unload();

    /** A new PSRAM buffer of {@code bytes}: its handle, or a value of 0 or less when it does not fit. */
    int allocate(int bytes);

    /** Copies {@code length} bytes of {@code source} to {@code offset} in {@code handle}; {@code length}, or an error. */
    int write(int handle, int offset, byte[] source, int length);

    /** Frees a PSRAM buffer the runtime does not own. */
    void free(int handle);
}
