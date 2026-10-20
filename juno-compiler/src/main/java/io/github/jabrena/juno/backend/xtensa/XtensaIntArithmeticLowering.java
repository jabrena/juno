package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.A;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.B;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.C;

/**
 * {@code int} arithmetic, conversions and comparisons. The ESP32-S3's LX7 has the 32-bit multiply and divide options
 * ({@code mull}, {@code quos}, {@code rems}) and {@code sext}; a shift takes its amount from the low five bits of the
 * {@code SAR} setup instruction, which is exactly Java's {@code & 31}. Xtensa has no condition flags: a comparison is
 * a compare-and-branch, so {@link #emitBranchUnless} jumps on the inverse condition.
 */
final class XtensaIntArithmeticLowering {
    private final XtensaEmitter xtensa;

    XtensaIntArithmeticLowering(XtensaEmitter xtensa) {
        this.xtensa = xtensa;
    }

    void emitBinary(StringBuilder output, FrameLayout frame, IrInstruction.Binary binary) {
        xtensa.load(output, frame, A, binary.left());
        xtensa.load(output, frame, B, binary.right());
        switch (binary.operation()) {
            case ADD -> output.append("    add a8, a8, a9\n");
            case SUBTRACT -> output.append("    sub a8, a8, a9\n");
            case MULTIPLY -> output.append("    mull a8, a8, a9\n");
            case DIVIDE -> emitDivision(output, "quos", "neg a8, a8");
            case REMAINDER -> emitDivision(output, "rems", "movi a8, 0");
            case SHIFT_LEFT -> output.append("    ssl a9\n    sll a8, a8\n");
            case SHIFT_RIGHT -> output.append("    ssr a9\n    sra a8, a8\n");
            case UNSIGNED_SHIFT_RIGHT -> output.append("    ssr a9\n    srl a8, a8\n");
            case AND -> output.append("    and a8, a8, a9\n");
            case OR -> output.append("    or a8, a8, a9\n");
            case XOR -> output.append("    xor a8, a8, a9\n");
        }
        xtensa.store(output, frame, A, binary.target());
    }

    /**
     * {@code quos}/{@code rems} leave {@code MIN_VALUE / -1} undefined, so a divisor of -1 takes {@code byMinusOne}
     * instead: Java's wrapping negation for a quotient, 0 for a remainder. A zero divisor raises the core's
     * integer-divide-by-zero exception, the panic a division the IR did not guard gets on every board.
     */
    private void emitDivision(StringBuilder output, String instruction, String byMinusOne) {
        String general = xtensa.newLabel(".Ldivide");
        String done = xtensa.newLabel(".LdivideDone");
        output.append("    movi a10, -1\n")
                .append("    bne a9, a10, ").append(general).append('\n')
                .append("    ").append(byMinusOne).append('\n')
                .append("    j ").append(done).append('\n')
                .append(general).append(":\n")
                .append("    ").append(instruction).append(" a8, a8, a9\n")
                .append(done).append(":\n");
    }

    void emitUnary(StringBuilder output, FrameLayout frame, IrInstruction.Unary unary) {
        xtensa.load(output, frame, A, unary.value());
        switch (unary.operation()) {
            case NEGATE -> output.append("    neg a8, a8\n");
            case TO_BYTE -> output.append("    sext a8, a8, 7\n");
            case TO_CHAR -> output.append("    extui a8, a8, 0, 16\n");
            case TO_SHORT -> output.append("    sext a8, a8, 15\n");
        }
        xtensa.store(output, frame, A, unary.target());
    }

    /** {@code target = left <condition> right ? 1 : 0}. */
    void emitCompare(StringBuilder output, FrameLayout frame, IrInstruction.Compare compare) {
        String done = xtensa.newLabel(".Lcompare");
        output.append("    movi ").append(C).append(", 0\n");
        emitBranchUnless(output, frame, compare.condition(), compare.left(), compare.right(), done);
        output.append("    movi ").append(C).append(", 1\n")
                .append(done).append(":\n");
        xtensa.store(output, frame, C, compare.target());
    }

    /**
     * Branches to {@code label} when {@code left <condition> right} does not hold. The label must be within the
     * conditional branch's reach of 127 bytes, so callers hop to a local label next to a {@code j}.
     */
    void emitBranchUnless(StringBuilder output, FrameLayout frame, Condition condition, Value left, Value right,
                          String label) {
        xtensa.load(output, frame, A, left);
        xtensa.load(output, frame, B, right);
        String instruction = switch (condition) {
            case EQUAL -> "bne a8, a9, ";
            case NOT_EQUAL -> "beq a8, a9, ";
            case LESS_THAN -> "bge a8, a9, ";
            case GREATER_EQUAL -> "blt a8, a9, ";
            case GREATER_THAN -> "bge a9, a8, ";
            case LESS_EQUAL -> "blt a9, a8, ";
        };
        output.append("    ").append(instruction).append(label).append('\n');
    }
}
