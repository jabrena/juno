package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrMethod;

/** Authoritative target-backend stack sizing shared with static runtime-risk analysis. */
public final class StackFrameSizing {
    private StackFrameSizing() {
    }

    /** Bytes reserved below the saved-register area by the method prologue. */
    public static int reservedFrameBytes(IrMethod method) {
        return FrameLayout.of(method).frameSize();
    }

    /** Total fixed per-invocation stack cost, including registers saved by the prologue. */
    public static int methodStackBytes(IrMethod method) {
        return AsmEmitter.PUSH_BYTES + reservedFrameBytes(method);
    }
}
