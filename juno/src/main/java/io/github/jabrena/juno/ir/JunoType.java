package io.github.jabrena.juno.ir;

/**
 * The scalar runtime types a Juno IR {@link Value} can carry.
 *
 * <p>Current bytecode lowering produces {@link #INT32} and {@link #FLOAT32}. {@link #FLOAT64} reserves the
 * typed IR/backend lane needed by future double lowering; double bytecodes remain unsupported.
 */
public enum JunoType {
    INT32(1),
    INT64(2),
    FLOAT32(1),
    FLOAT64(2);

    private final int jvmSlots;

    JunoType(int jvmSlots) {
        this.jvmSlots = jvmSlots;
    }

    /** Number of JVM local/operand-stack slots occupied by this type. */
    public int jvmSlots() {
        return jvmSlots;
    }
}
