package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;

import java.util.List;
import java.util.Set;

/**
 * {@code long}/{@code float}/{@code double} instructions. A {@code long} is a split low/high pair of
 * {@code int32} slots and a {@code float}/{@code double} is its raw IEEE-754 bit pattern in core
 * registers (never VFP registers), so every nontrivial operation (arithmetic, comparison, conversion)
 * is a call to a small {@code extern "C"} runtime-shim function (see {@link ShimLibraries}) rather
 * than hand-rolled soft-float assembly; only constants, sign flips and plain word moves stay inline.
 */
final class WideArithmeticLowering {
    private final AsmEmitter asm;
    private final Set<ShimFeature> features;

    WideArithmeticLowering(AsmEmitter asm, Set<ShimFeature> features) {
        this.asm = asm;
        this.features = features;
    }

    void emit(StringBuilder output, FrameLayout frame, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.LongConst constant -> {
                asm.emitLoadImmediate(output, "r0", (int) constant.value());
                asm.store(output, frame, "r0", constant.targetLow());
                asm.emitLoadImmediate(output, "r0", (int) (constant.value() >>> 32));
                asm.store(output, frame, "r0", constant.targetHigh());
            }
            case IrInstruction.LongBinary binary -> {
                features.add(ShimFeature.LONG);
                asm.load(output, frame, "r0", binary.leftLow());
                asm.load(output, frame, "r1", binary.leftHigh());
                asm.load(output, frame, "r2", binary.rightLow());
                asm.load(output, frame, "r3", binary.rightHigh());
                output.append("    bl ").append(longHelperFor(binary.operation())).append('\n');
                asm.store(output, frame, "r0", binary.targetLow());
                asm.store(output, frame, "r1", binary.targetHigh());
            }
            case IrInstruction.LongShift shift -> {
                features.add(ShimFeature.LONG);
                asm.load(output, frame, "r0", shift.valueLow());
                asm.load(output, frame, "r1", shift.valueHigh());
                asm.load(output, frame, "r2", shift.shiftAmount());
                output.append("    bl ").append(longShiftHelperFor(shift.operation())).append('\n');
                asm.store(output, frame, "r0", shift.targetLow());
                asm.store(output, frame, "r1", shift.targetHigh());
            }
            case IrInstruction.LongNegate negate -> {
                features.add(ShimFeature.LONG);
                asm.load(output, frame, "r0", negate.valueLow());
                asm.load(output, frame, "r1", negate.valueHigh());
                output.append("    bl juno_lneg\n");
                asm.store(output, frame, "r0", negate.targetLow());
                asm.store(output, frame, "r1", negate.targetHigh());
            }
            case IrInstruction.LongCompare compare -> {
                features.add(ShimFeature.LONG);
                asm.load(output, frame, "r0", compare.leftLow());
                asm.load(output, frame, "r1", compare.leftHigh());
                asm.load(output, frame, "r2", compare.rightLow());
                asm.load(output, frame, "r3", compare.rightHigh());
                output.append("    bl juno_lcmp\n");
                asm.store(output, frame, "r0", compare.target());
            }
            case IrInstruction.IntToLong widen -> {
                // Sign-extending needs no arithmetic: the low half is the value unchanged, and the high
                // half is all-0s or all-1s depending on its sign, i.e. value >> 31.
                asm.load(output, frame, "r0", widen.value());
                asm.store(output, frame, "r0", widen.targetLow());
                output.append("    asr r0, r0, #31\n");
                asm.store(output, frame, "r0", widen.targetHigh());
            }
            case IrInstruction.LongToInt narrow -> {
                asm.load(output, frame, "r0", narrow.valueLow());
                asm.store(output, frame, "r0", narrow.target());
            }
            case IrInstruction.FloatConst constant -> {
                asm.emitLoadImmediate(output, "r0", Float.floatToRawIntBits(constant.value()));
                asm.store(output, frame, "r0", constant.target());
            }
            case IrInstruction.FloatBinary binary -> {
                features.add(ShimFeature.FLOAT);
                asm.load(output, frame, "r0", binary.left());
                asm.load(output, frame, "r1", binary.right());
                output.append("    bl ").append(floatHelperFor(binary.operation())).append('\n');
                asm.store(output, frame, "r0", binary.target());
            }
            case IrInstruction.FloatNegate negate -> {
                // Flips the sign bit directly; no soft-float call needed for plain negation.
                asm.load(output, frame, "r0", negate.value());
                output.append("    eor r0, r0, #0x80000000\n");
                asm.store(output, frame, "r0", negate.target());
            }
            case IrInstruction.FloatCompare compare -> {
                features.add(ShimFeature.FLOAT);
                asm.load(output, frame, "r0", compare.left());
                asm.load(output, frame, "r1", compare.right());
                asm.emitLoadImmediate(output, "r2", compare.nanResult());
                output.append("    bl juno_fcmp\n");
                asm.store(output, frame, "r0", compare.target());
            }
            case IrInstruction.IntToFloat conversion -> {
                features.add(ShimFeature.FLOAT);
                asm.load(output, frame, "r0", conversion.value());
                output.append("    bl juno_i2f\n");
                asm.store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.FloatToInt conversion -> {
                features.add(ShimFeature.FLOAT);
                asm.load(output, frame, "r0", conversion.value());
                output.append("    bl juno_f2i\n");
                asm.store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.DoubleConst constant -> {
                long bits = Double.doubleToRawLongBits(constant.value());
                asm.emitLoadImmediate(output, "r0", (int) bits);
                asm.emitLoadImmediate(output, "r1", (int) (bits >>> 32));
                asm.store64(output, frame, "r0", "r1", constant.target());
            }
            case IrInstruction.DoubleBinary binary -> {
                features.add(ShimFeature.DOUBLE);
                asm.load64(output, frame, "r0", "r1", binary.left());
                asm.load64(output, frame, "r2", "r3", binary.right());
                output.append("    bl ").append(doubleHelperFor(binary.operation())).append('\n');
                asm.store64(output, frame, "r0", "r1", binary.target());
            }
            case IrInstruction.DoubleNegate negate -> {
                // The sign bit of a low-word/high-word split double is bit 31 of the high word.
                asm.load64(output, frame, "r0", "r1", negate.value());
                output.append("    eor r1, r1, #0x80000000\n");
                asm.store64(output, frame, "r0", "r1", negate.target());
            }
            case IrInstruction.DoubleCompare compare -> {
                features.add(ShimFeature.DOUBLE);
                asm.emitShimCall(output, frame, "juno_dcmp", List.of(
                        new WordSource.FromValueLow(compare.left()), new WordSource.FromValueHigh(compare.left()),
                        new WordSource.FromValueLow(compare.right()), new WordSource.FromValueHigh(compare.right()),
                        new WordSource.Immediate(compare.nanResult())));
                asm.store(output, frame, "r0", compare.target());
            }
            case IrInstruction.IntToDouble conversion -> {
                features.add(ShimFeature.DOUBLE);
                asm.load(output, frame, "r0", conversion.value());
                output.append("    bl juno_i2d\n");
                asm.store64(output, frame, "r0", "r1", conversion.target());
            }
            case IrInstruction.DoubleToInt conversion -> {
                features.add(ShimFeature.DOUBLE);
                asm.load64(output, frame, "r0", "r1", conversion.value());
                output.append("    bl juno_d2i\n");
                asm.store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.FloatToDouble conversion -> {
                features.add(ShimFeature.DOUBLE);
                asm.load(output, frame, "r0", conversion.value());
                output.append("    bl juno_f2d\n");
                asm.store64(output, frame, "r0", "r1", conversion.target());
            }
            case IrInstruction.DoubleToFloat conversion -> {
                features.add(ShimFeature.DOUBLE);
                asm.load64(output, frame, "r0", "r1", conversion.value());
                output.append("    bl juno_d2f\n");
                asm.store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.LongToDouble conversion -> {
                features.add(ShimFeature.DOUBLE);
                asm.load(output, frame, "r0", conversion.valueLow());
                asm.load(output, frame, "r1", conversion.valueHigh());
                output.append("    bl juno_l2d\n");
                asm.store64(output, frame, "r0", "r1", conversion.target());
            }
            case IrInstruction.DoubleToLong conversion -> {
                features.add(ShimFeature.DOUBLE);
                asm.load64(output, frame, "r0", "r1", conversion.value());
                output.append("    bl juno_d2l\n");
                asm.store(output, frame, "r0", conversion.targetLow());
                asm.store(output, frame, "r1", conversion.targetHigh());
            }
            case IrInstruction.PackLong packed -> {
                asm.load(output, frame, "r0", packed.valueLow());
                asm.load(output, frame, "r1", packed.valueHigh());
                asm.store64(output, frame, "r0", "r1", packed.target());
            }
            case IrInstruction.UnpackLong unpacked -> {
                asm.load64(output, frame, "r0", "r1", unpacked.value());
                asm.store(output, frame, "r0", unpacked.targetLow());
                asm.store(output, frame, "r1", unpacked.targetHigh());
            }
            case IrInstruction.LongToFloat conversion -> {
                features.add(ShimFeature.FLOAT);
                asm.load(output, frame, "r0", conversion.valueLow());
                asm.load(output, frame, "r1", conversion.valueHigh());
                output.append("    bl juno_l2f\n");
                asm.store(output, frame, "r0", conversion.target());
            }
            case IrInstruction.FloatToLong conversion -> {
                features.add(ShimFeature.FLOAT);
                asm.load(output, frame, "r0", conversion.value());
                output.append("    bl juno_f2l\n");
                asm.store(output, frame, "r0", conversion.targetLow());
                asm.store(output, frame, "r1", conversion.targetHigh());
            }
            default -> throw Thumb2AsmBackend.unsupported(instruction.getClass().getSimpleName());
        }
    }

    private String longHelperFor(BinaryOp operation) {
        return switch (operation) {
            case ADD -> "juno_ladd";
            case SUBTRACT -> "juno_lsub";
            case MULTIPLY -> "juno_lmul";
            case DIVIDE -> "juno_ldiv";
            case REMAINDER -> "juno_lrem";
            case AND -> "juno_land";
            case OR -> "juno_lor";
            case XOR -> "juno_lxor";
            case SHIFT_LEFT, SHIFT_RIGHT, UNSIGNED_SHIFT_RIGHT ->
                    throw new IllegalStateException("Long shifts use longShiftHelperFor: " + operation);
        };
    }

    private String longShiftHelperFor(BinaryOp operation) {
        return switch (operation) {
            case SHIFT_LEFT -> "juno_lshl";
            case SHIFT_RIGHT -> "juno_lshr";
            case UNSIGNED_SHIFT_RIGHT -> "juno_lushr";
            default -> throw new IllegalStateException("Not a long shift operation: " + operation);
        };
    }

    private String floatHelperFor(FloatBinaryOp operation) {
        return switch (operation) {
            case ADD -> "juno_fadd";
            case SUBTRACT -> "juno_fsub";
            case MULTIPLY -> "juno_fmul";
            case DIVIDE -> "juno_fdiv";
            case REMAINDER -> "juno_frem";
        };
    }

    private String doubleHelperFor(FloatBinaryOp operation) {
        return switch (operation) {
            case ADD -> "juno_dadd";
            case SUBTRACT -> "juno_dsub";
            case MULTIPLY -> "juno_dmul";
            case DIVIDE -> "juno_ddiv";
            case REMAINDER -> "juno_drem";
        };
    }
}
