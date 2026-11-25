package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.ai.DecisionModel;
import io.github.jabrena.juno.hardware.Psram;

/**
 * The board's {@link DecisionModelRuntime}: compiler intrinsics for the native JEV1 evaluator, which reads either the
 * table the build linked into flash or a PSRAM copy of an SD-card file in place, and {@link Psram} for that copy.
 */
public final class NativeDecisionModel implements DecisionModelRuntime {
    private static final DecisionModel INSTANCE = new JevDecisionModel(new NativeDecisionModel());

    private NativeDecisionModel() {
    }

    /** The selected board's shared decision-model engine. */
    public static DecisionModel decisionModel() {
        return INSTANCE;
    }

    @Override
    public native int beginLinked();

    @Override
    public native int beginPsram(int handle, int size);

    @Override
    public native int actionCount();

    @Override
    public native int featureCount();

    /** Runs the loaded JEV1 model over caller-owned buffers. */
    @Override
    public native int evaluate(int[] state, int[] legalActions);

    @Override
    public native void unload();

    @Override
    public int allocate(int bytes) {
        return Psram.allocate(bytes);
    }

    @Override
    public int write(int handle, int offset, byte[] source, int length) {
        return Psram.write(handle, offset, source, 0, length);
    }

    @Override
    public void free(int handle) {
        Psram.release(handle);
    }
}
