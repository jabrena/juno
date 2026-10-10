package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

/**
 * 32-bit {@code int} arithmetic, narrowing conversions and comparisons, all inline Thumb-2. A rematerialized
 * constant operand (see {@link FrameLayout}) is folded into the instruction's immediate field whenever Thumb-2 can
 * encode it, instead of being loaded into a second register.
 */
final class IntArithmeticLowering {
    private final AsmEmitter asm;

    IntArithmeticLowering(AsmEmitter asm) {
        this.asm = asm;
    }

    void emitBinary(StringBuilder output, FrameLayout frame, IrInstruction.Binary binary) {
        asm.load(output, frame, "r0", binary.left());
        Integer constant = frame.constant(binary.right());
        String immediate = constant == null ? null : immediateForm(binary.operation(), constant);
        if (immediate != null) {
            output.append(immediate);
        } else {
            asm.load(output, frame, "r1", binary.right());
            switch (binary.operation()) {
                case ADD -> output.append("    add r0, r0, r1\n");
                case SUBTRACT -> output.append("    sub r0, r0, r1\n");
                case MULTIPLY -> output.append("    mul r0, r0, r1\n");
                case DIVIDE -> output.append("    sdiv r0, r0, r1\n");
                case REMAINDER -> output.append("    sdiv r2, r0, r1\n")
                        .append("    mls r0, r2, r1, r0\n");
                case SHIFT_LEFT -> output.append("    and r1, r1, #31\n").append("    lsl r0, r0, r1\n");
                case SHIFT_RIGHT -> output.append("    and r1, r1, #31\n").append("    asr r0, r0, r1\n");
                case UNSIGNED_SHIFT_RIGHT -> output.append("    and r1, r1, #31\n").append("    lsr r0, r0, r1\n");
                case AND -> output.append("    and r0, r0, r1\n");
                case OR -> output.append("    orr r0, r0, r1\n");
                case XOR -> output.append("    eor r0, r0, r1\n");
            }
        }
        asm.store(output, frame, "r0", binary.target());
    }

    /**
     * {@code r0 = r0 <op> constant} without a second register, or null when Thumb-2 cannot encode it. Shifts use the
     * JVM's masked amount; a multiplication by a power of two is a left shift; an operation that leaves {@code r0}
     * unchanged emits nothing.
     */
    static String immediateForm(BinaryOp operation, int constant) {
        return switch (operation) {
            case ADD -> addImmediate("add", "sub", constant);
            case SUBTRACT -> addImmediate("sub", "add", constant);
            case AND -> AsmEmitter.isModifiedImmediate(constant) ? "    and r0, r0, #" + constant + "\n"
                    : AsmEmitter.isModifiedImmediate(~constant) ? "    bic r0, r0, #" + ~constant + "\n" : null;
            case OR -> AsmEmitter.isModifiedImmediate(constant) ? "    orr r0, r0, #" + constant + "\n"
                    : AsmEmitter.isModifiedImmediate(~constant) ? "    orn r0, r0, #" + ~constant + "\n" : null;
            case XOR -> AsmEmitter.isModifiedImmediate(constant) ? "    eor r0, r0, #" + constant + "\n" : null;
            case SHIFT_LEFT -> shift("lsl", constant & 31);
            case SHIFT_RIGHT -> shift("asr", constant & 31);
            case UNSIGNED_SHIFT_RIGHT -> shift("lsr", constant & 31);
            case MULTIPLY -> constant > 0 && Integer.bitCount(constant) == 1
                    ? shift("lsl", Integer.numberOfTrailingZeros(constant)) : null;
            case DIVIDE, REMAINDER -> null;
        };
    }

    private static String addImmediate(String operation, String inverse, int constant) {
        if (AsmEmitter.isModifiedImmediate(constant)) {
            return "    " + operation + " r0, r0, #" + constant + "\n";
        }
        if (constant >= 0 && constant <= 4095) {
            return "    " + operation + "w r0, r0, #" + constant + "\n";
        }
        if (constant != Integer.MIN_VALUE && constant < 0) {
            return addImmediate(inverse, operation, -constant);
        }
        return null;
    }

    private static String shift(String operation, int amount) {
        return amount == 0 ? "" : "    " + operation + " r0, r0, #" + amount + "\n";
    }

    void emitUnary(StringBuilder output, FrameLayout frame, IrInstruction.Unary unary) {
        asm.load(output, frame, "r0", unary.value());
        switch (unary.operation()) {
            case NEGATE -> output.append("    rsb r0, r0, #0\n");
            case TO_BYTE -> output.append("    sxtb r0, r0\n");
            case TO_CHAR -> output.append("    uxth r0, r0\n");
            case TO_SHORT -> output.append("    sxth r0, r0\n");
        }
        asm.store(output, frame, "r0", unary.target());
    }

    /** Sets the flags as {@code cmp left, right}, with a constant {@code right} as an immediate when encodable. */
    void emitFlags(StringBuilder output, FrameLayout frame, Value left, Value right) {
        asm.load(output, frame, "r0", left);
        Integer constant = frame.constant(right);
        if (constant != null && AsmEmitter.isModifiedImmediate(constant)) {
            output.append("    cmp r0, #").append(constant).append('\n');
        } else if (constant != null && constant != Integer.MIN_VALUE && AsmEmitter.isModifiedImmediate(-constant)) {
            output.append("    cmn r0, #").append(-constant).append('\n');
        } else {
            asm.load(output, frame, "r1", right);
            output.append("    cmp r0, r1\n");
        }
    }

    void emitCompare(StringBuilder output, FrameLayout frame, IrInstruction.Compare compare) {
        emitFlags(output, frame, compare.left(), compare.right());
        String condition = suffix(compare.condition());
        String inverse = suffix(inverse(compare.condition()));
        output.append("    ite ").append(condition).append('\n')
                .append("    mov").append(condition).append(" r0, #1\n")
                .append("    mov").append(inverse).append(" r0, #0\n");
        asm.store(output, frame, "r0", compare.target());
    }

    static String suffix(Condition condition) {
        return switch (condition) {
            case EQUAL -> "eq";
            case NOT_EQUAL -> "ne";
            case LESS_THAN -> "lt";
            case GREATER_EQUAL -> "ge";
            case GREATER_THAN -> "gt";
            case LESS_EQUAL -> "le";
        };
    }

    static Condition inverse(Condition condition) {
        return switch (condition) {
            case EQUAL -> Condition.NOT_EQUAL;
            case NOT_EQUAL -> Condition.EQUAL;
            case LESS_THAN -> Condition.GREATER_EQUAL;
            case GREATER_EQUAL -> Condition.LESS_THAN;
            case GREATER_THAN -> Condition.LESS_EQUAL;
            case LESS_EQUAL -> Condition.GREATER_THAN;
        };
    }
}
