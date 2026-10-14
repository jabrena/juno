package io.github.jabrena.juno.backend.avr;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.Map;
import java.util.Set;

/**
 * {@code int} arithmetic, narrowing and comparisons on AVR, four bytes at a time: add, subtract, logic, negation and
 * constant shifts inline; multiply, divide, remainder and variable shifts in shim helpers called with avr-gcc's own
 * convention (operands in r22-r25 and r18-r21, the result in r22-r25).
 */
final class AvrIntArithmeticLowering {
    /** Integer helpers called with avr-gcc's own convention: operands in r22-r25 and r18-r21, result in r22-r25. */
    private static final Map<BinaryOp, String> INT_HELPERS = Map.of(
            BinaryOp.MULTIPLY, "juno_avr_imul",
            BinaryOp.DIVIDE, "juno_avr_idiv",
            BinaryOp.REMAINDER, "juno_avr_irem",
            BinaryOp.SHIFT_LEFT, "juno_avr_ishl",
            BinaryOp.SHIFT_RIGHT, "juno_avr_ishr",
            BinaryOp.UNSIGNED_SHIFT_RIGHT, "juno_avr_iushr");
    private static final int A = AvrEmitter.A;
    private static final int B = AvrEmitter.B;

    private final AvrEmitter avr;
    private final Set<ShimFeature> features;

    AvrIntArithmeticLowering(AvrEmitter avr, Set<ShimFeature> features) {
        this.avr = avr;
        this.features = features;
    }

    void emitBinary(StringBuilder output, FrameLayout frame, IrInstruction.Binary binary) {
        avr.load(output, frame, A, binary.left());
        Integer constant = frame.constant(binary.right());
        if (constant == null || !emitConstantBinary(output, binary.operation(), constant)) {
            avr.load(output, frame, B, binary.right());
            switch (binary.operation()) {
                case ADD -> byteWise(output, "add", "adc");
                case SUBTRACT -> byteWise(output, "sub", "sbc");
                case AND -> byteWise(output, "and", "and");
                case OR -> byteWise(output, "or", "or");
                case XOR -> byteWise(output, "eor", "eor");
                default -> {
                    features.add(ShimFeature.AVR_INT_HELPERS);
                    output.append("    call ").append(INT_HELPERS.get(binary.operation())).append('\n');
                }
            }
        }
        avr.store(output, frame, A, binary.target());
    }

    private void byteWise(StringBuilder output, String first, String rest) {
        for (int index = 0; index < AvrEmitter.WORD; index++) {
            output.append("    ").append(index == 0 ? first : rest).append(' ').append(avr.r(A + index))
                    .append(", ").append(avr.r(B + index)).append('\n');
        }
    }

    /** {@code A = A <op> constant} without a second operand, or false when that takes a full operand load anyway. */
    private boolean emitConstantBinary(StringBuilder output, BinaryOp operation, int constant) {
        return switch (operation) {
            case ADD, SUBTRACT -> {
                // subi/sbci subtract with borrow; adding c is subtracting -c.
                int subtrahend = operation == BinaryOp.ADD ? -constant : constant;
                for (int index = 0; index < AvrEmitter.WORD; index++) {
                    output.append("    ").append(index == 0 ? "subi " : "sbci ").append(avr.r(A + index))
                            .append(", ").append((subtrahend >>> (8 * index)) & 0xFF).append('\n');
                }
                yield true;
            }
            case AND, OR -> {
                for (int index = 0; index < AvrEmitter.WORD; index++) {
                    int part = (constant >>> (8 * index)) & 0xFF;
                    if (operation == BinaryOp.AND && part != 0xFF) {
                        output.append("    andi ").append(avr.r(A + index)).append(", ").append(part).append('\n');
                    } else if (operation == BinaryOp.OR && part != 0) {
                        output.append("    ori ").append(avr.r(A + index)).append(", ").append(part).append('\n');
                    }
                }
                yield true;
            }
            case SHIFT_LEFT, SHIFT_RIGHT, UNSIGNED_SHIFT_RIGHT -> {
                emitConstantShift(output, operation, constant & 31);
                yield true;
            }
            case MULTIPLY -> {
                if (constant > 0 && Integer.bitCount(constant) == 1) {
                    emitConstantShift(output, BinaryOp.SHIFT_LEFT, Integer.numberOfTrailingZeros(constant));
                    yield true;
                }
                yield false;
            }
            default -> false;
        };
    }

    /** Shifts A by a constant {@code amount} (0-31): whole bytes by moves, the rest bit by bit. */
    private void emitConstantShift(StringBuilder output, BinaryOp operation, int amount) {
        int bytes = amount / 8;
        int bits = amount % 8;
        if (operation == BinaryOp.SHIFT_LEFT) {
            if (bytes > 0) {
                for (int index = AvrEmitter.WORD - 1; index >= 0; index--) {
                    if (index >= bytes) {
                        output.append("    mov ").append(avr.r(A + index)).append(", ")
                                .append(avr.r(A + index - bytes)).append('\n');
                    } else {
                        output.append("    ldi ").append(avr.r(A + index)).append(", 0\n");
                    }
                }
            }
            for (int bit = 0; bit < bits; bit++) {
                output.append("    lsl r22\n    rol r23\n    rol r24\n    rol r25\n");
            }
            return;
        }
        boolean arithmetic = operation == BinaryOp.SHIFT_RIGHT;
        if (bytes > 0) {
            if (arithmetic) {
                output.append("    mov r0, r25\n    lsl r0\n    sbc r0, r0\n");
            }
            for (int index = 0; index < AvrEmitter.WORD; index++) {
                if (index + bytes < AvrEmitter.WORD) {
                    output.append("    mov ").append(avr.r(A + index)).append(", ")
                            .append(avr.r(A + index + bytes)).append('\n');
                } else if (arithmetic) {
                    output.append("    mov ").append(avr.r(A + index)).append(", r0\n");
                } else {
                    output.append("    ldi ").append(avr.r(A + index)).append(", 0\n");
                }
            }
        }
        for (int bit = 0; bit < bits; bit++) {
            output.append(arithmetic ? "    asr r25\n" : "    lsr r25\n").append("    ror r24\n    ror r23\n    ror r22\n");
        }
    }

    void emitUnary(StringBuilder output, FrameLayout frame, IrInstruction.Unary unary) {
        avr.load(output, frame, A, unary.value());
        switch (unary.operation()) {
            case NEGATE -> output.append("    com r25\n    com r24\n    com r23\n    neg r22\n")
                    .append("    sbci r23, 255\n    sbci r24, 255\n    sbci r25, 255\n");
            case TO_BYTE -> output.append("    mov r23, r22\n    lsl r23\n    sbc r23, r23\n")
                    .append("    mov r24, r23\n    mov r25, r23\n");
            case TO_CHAR -> output.append("    ldi r24, 0\n    ldi r25, 0\n");
            case TO_SHORT -> output.append("    mov r24, r23\n    lsl r24\n    sbc r24, r24\n    mov r25, r24\n");
        }
        avr.store(output, frame, A, unary.target());
    }

    /**
     * Sets the flags for {@code condition} between {@code left} and {@code right} and returns the branch mnemonic taken
     * when it holds: AVR only has signed less-than and greater-or-equal, so greater-than and less-or-equal compare
     * the swapped operands.
     */
    String emitFlags(StringBuilder output, FrameLayout frame, Condition condition, Value left, Value right) {
        avr.load(output, frame, A, left);
        avr.load(output, frame, B, right);
        boolean swapped = condition == Condition.GREATER_THAN || condition == Condition.LESS_EQUAL;
        avr.compareQuads(output, swapped ? B : A, swapped ? A : B);
        return switch (condition) {
            case EQUAL -> "breq";
            case NOT_EQUAL -> "brne";
            case LESS_THAN, GREATER_THAN -> "brlt";
            case GREATER_EQUAL, LESS_EQUAL -> "brge";
        };
    }

    String inverse(String branch) {
        return switch (branch) {
            case "breq" -> "brne";
            case "brne" -> "breq";
            case "brlt" -> "brge";
            case "brge" -> "brlt";
            default -> throw new IllegalArgumentException(branch);
        };
    }

    void emitCompare(StringBuilder output, FrameLayout frame, IrInstruction.Compare compare) {
        String branch = emitFlags(output, frame, compare.condition(), compare.left(), compare.right());
        String done = avr.newLabel(".Lcompare");
        // ldi leaves the flags alone.
        output.append("    ldi r22, 1\n")
                .append("    ").append(branch).append(' ').append(done).append('\n')
                .append("    ldi r22, 0\n")
                .append(done).append(":\n")
                .append("    ldi r23, 0\n    ldi r24, 0\n    ldi r25, 0\n");
        avr.store(output, frame, A, compare.target());
    }
}
