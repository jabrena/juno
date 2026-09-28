package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;

/** 32-bit {@code int} arithmetic, narrowing conversions and comparisons, all inline Thumb-2. */
final class IntArithmeticLowering {
    private final AsmEmitter asm;

    IntArithmeticLowering(AsmEmitter asm) {
        this.asm = asm;
    }

    void emitBinary(StringBuilder output, FrameLayout frame, IrInstruction.Binary binary) {
        asm.load(output, frame, "r0", binary.left());
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
        asm.store(output, frame, "r0", binary.target());
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

    void emitCompare(StringBuilder output, FrameLayout frame, IrInstruction.Compare compare) {
        String trueLabel = asm.newLabel(".Lcmptrue");
        String doneLabel = asm.newLabel(".Lcmpdone");
        asm.load(output, frame, "r0", compare.left());
        asm.load(output, frame, "r1", compare.right());
        output.append("    cmp r0, r1\n")
                .append("    ").append(branchInstruction(compare.condition())).append(' ').append(trueLabel).append('\n')
                .append("    movs r0, #0\n")
                .append("    b ").append(doneLabel).append('\n')
                .append(trueLabel).append(":\n")
                .append("    movs r0, #1\n")
                .append(doneLabel).append(":\n");
        asm.store(output, frame, "r0", compare.target());
    }

    private String branchInstruction(Condition condition) {
        return switch (condition) {
            case EQUAL -> "beq";
            case NOT_EQUAL -> "bne";
            case LESS_THAN -> "blt";
            case GREATER_EQUAL -> "bge";
            case GREATER_THAN -> "bgt";
            case LESS_EQUAL -> "ble";
        };
    }
}
