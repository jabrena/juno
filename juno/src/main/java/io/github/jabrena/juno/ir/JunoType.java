package io.github.jabrena.juno.ir;

/**
 * The scalar runtime types a Juno IR {@link Value} can carry.
 *
 * <p>Current bytecode lowering produces only {@link #INT32}. The floating-point variants establish the typed
 * IR/backend boundary needed by future float and double lowering; their JVM opcodes remain unsupported.
 */
public enum JunoType {
    INT32(1),
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
