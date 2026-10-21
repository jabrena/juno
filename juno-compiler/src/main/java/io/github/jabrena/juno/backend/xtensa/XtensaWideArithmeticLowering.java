package io.github.jabrena.juno.backend.xtensa;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.WordSource;
import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;
import io.github.jabrena.juno.board.Architecture;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.A;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.B;
import static io.github.jabrena.juno.backend.xtensa.XtensaEmitter.C;

/**
 * {@code long}, {@code float} and {@code double} instructions. A {@code long} is a split low/high pair of word
 * slots and a {@code float} its raw IEEE-754 bits in one word, so every nontrivial operation is a call to the same
 * {@code extern "C"} shim helpers the Thumb-2 backend uses ({@code juno_ladd}, {@code juno_fadd}, ...): the windowed
 * ABI passes a {@code float} in an {@code a} register and an {@code int64_t} in an even-aligned register pair, which
 * is exactly the word layout below, and the ESP32 toolchain compiles the float helpers to the ESP32-S3's
 * single-precision FPU. A {@code double} fills an even-aligned register pair the same way; with no double-precision
 * FPU, its helpers compile to libgcc soft-float. Only constants, sign flips and plain word moves stay inline.
 */
final class XtensaWideArithmeticLowering {
    private final XtensaEmitter xtensa;
    private final Set<ShimFeature> features;

    XtensaWideArithmeticLowering(XtensaEmitter xtensa, Set<ShimFeature> features) {
        this.xtensa = xtensa;
        this.features = features;
    }

    void emit(StringBuilder output, FrameLayout frame, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.LongConst constant -> {
                xtensa.loadImmediate(output, A, (int) constant.value());
                xtensa.store(output, frame, A, constant.targetLow());
                xtensa.loadImmediate(output, A, (int) (constant.value() >>> 32));
                xtensa.store(output, frame, A, constant.targetHigh());
            }
            case IrInstruction.LongBinary binary -> {
                features.add(ShimFeature.LONG);
                callForLong(output, frame, longHelperFor(binary.operation()), words(binary.leftLow(),
                        binary.leftHigh(), binary.rightLow(), binary.rightHigh()), binary.targetLow(),
                        binary.targetHigh());
            }
            case IrInstruction.LongShift shift -> {
                features.add(ShimFeature.LONG);
                callForLong(output, frame, longShiftHelperFor(shift.operation()), words(shift.valueLow(),
                        shift.valueHigh(), shift.shiftAmount()), shift.targetLow(), shift.targetHigh());
            }
            case IrInstruction.LongNegate negate -> {
                features.add(ShimFeature.LONG);
                callForLong(output, frame, "juno_lneg", words(negate.valueLow(), negate.valueHigh()),
                        negate.targetLow(), negate.targetHigh());
            }
            case IrInstruction.LongCompare compare -> {
                features.add(ShimFeature.LONG);
                callForWord(output, frame, "juno_lcmp", words(compare.leftLow(), compare.leftHigh(),
                        compare.rightLow(), compare.rightHigh()), compare.target());
            }
            case IrInstruction.IntToLong widen -> {
                // The low half is the value unchanged, the high half its sign copied into every bit.
                xtensa.load(output, frame, A, widen.value());
                xtensa.store(output, frame, A, widen.targetLow());
                output.append("    srai a8, a8, 31\n");
                xtensa.store(output, frame, A, widen.targetHigh());
            }
            case IrInstruction.LongToInt narrow -> {
                xtensa.load(output, frame, A, narrow.valueLow());
                xtensa.store(output, frame, A, narrow.target());
            }
            case IrInstruction.PackLong packed -> {
                xtensa.load(output, frame, A, packed.valueLow());
                xtensa.load(output, frame, B, packed.valueHigh());
                xtensa.store64(output, frame, A, B, packed.target());
            }
            case IrInstruction.UnpackLong unpacked -> {
                xtensa.load64(output, frame, A, B, unpacked.value());
                xtensa.store(output, frame, A, unpacked.targetLow());
                xtensa.store(output, frame, B, unpacked.targetHigh());
            }
            case IrInstruction.FloatConst constant -> {
                xtensa.loadImmediate(output, A, Float.floatToRawIntBits(constant.value()));
                xtensa.store(output, frame, A, constant.target());
            }
            case IrInstruction.FloatBinary binary -> {
                features.add(ShimFeature.FLOAT);
                callForWord(output, frame, floatHelperFor(binary.operation()), words(binary.left(), binary.right()),
                        binary.target());
            }
            case IrInstruction.FloatNegate negate -> {
                // Flips the sign bit directly; plain negation needs no FPU.
                xtensa.load(output, frame, A, negate.value());
                xtensa.loadImmediate(output, B, Integer.MIN_VALUE);
                output.append("    xor a8, a8, a9\n");
                xtensa.store(output, frame, A, negate.target());
            }
            case IrInstruction.FloatCompare compare -> {
                features.add(ShimFeature.FLOAT);
                callForWord(output, frame, "juno_fcmp", List.of(new WordSource.FromValue(compare.left()),
                        new WordSource.FromValue(compare.right()), new WordSource.Immediate(compare.nanResult())),
                        compare.target());
            }
            case IrInstruction.IntToFloat conversion -> {
                features.add(ShimFeature.FLOAT);
                callForWord(output, frame, "juno_i2f", words(conversion.value()), conversion.target());
            }
            case IrInstruction.FloatToInt conversion -> {
                features.add(ShimFeature.FLOAT);
                callForWord(output, frame, "juno_f2i", words(conversion.value()), conversion.target());
            }
            case IrInstruction.LongToFloat conversion -> {
                features.add(ShimFeature.FLOAT);
                callForWord(output, frame, "juno_l2f", words(conversion.valueLow(), conversion.valueHigh()),
                        conversion.target());
            }
            case IrInstruction.FloatToLong conversion -> {
                features.add(ShimFeature.FLOAT);
                callForLong(output, frame, "juno_f2l", words(conversion.value()), conversion.targetLow(),
                        conversion.targetHigh());
            }
            case IrInstruction.DoubleConst constant -> {
                long bits = Double.doubleToRawLongBits(constant.value());
                xtensa.loadImmediate(output, A, (int) bits);
                xtensa.loadImmediate(output, B, (int) (bits >>> 32));
                xtensa.store64(output, frame, A, B, constant.target());
            }
            case IrInstruction.DoubleBinary binary -> {
                features.add(ShimFeature.DOUBLE);
                callForDouble(output, frame, doubleHelperFor(binary.operation()),
                        concat(doubleWords(binary.left()), doubleWords(binary.right())), binary.target());
            }
            case IrInstruction.DoubleNegate negate -> {
                // The sign bit of a low-word/high-word split double is bit 31 of the high word.
                xtensa.load64(output, frame, A, B, negate.value());
                xtensa.loadImmediate(output, C, Integer.MIN_VALUE);
                output.append("    xor a9, a9, a10\n");
                xtensa.store64(output, frame, A, B, negate.target());
            }
            case IrInstruction.DoubleCompare compare -> {
                features.add(ShimFeature.DOUBLE);
                List<WordSource> operands = concat(doubleWords(compare.left()), doubleWords(compare.right()));
                callForWord(output, frame, "juno_dcmp",
                        concat(operands, List.of(new WordSource.Immediate(compare.nanResult()))), compare.target());
            }
            case IrInstruction.IntToDouble conversion -> {
                features.add(ShimFeature.DOUBLE);
                callForDouble(output, frame, "juno_i2d", words(conversion.value()), conversion.target());
            }
            case IrInstruction.DoubleToInt conversion -> {
                features.add(ShimFeature.DOUBLE);
                callForWord(output, frame, "juno_d2i", doubleWords(conversion.value()), conversion.target());
            }
            case IrInstruction.FloatToDouble conversion -> {
                features.add(ShimFeature.DOUBLE);
                callForDouble(output, frame, "juno_f2d", words(conversion.value()), conversion.target());
            }
            case IrInstruction.DoubleToFloat conversion -> {
                features.add(ShimFeature.DOUBLE);
                callForWord(output, frame, "juno_d2f", doubleWords(conversion.value()), conversion.target());
            }
            case IrInstruction.LongToDouble conversion -> {
                features.add(ShimFeature.DOUBLE);
                callForDouble(output, frame, "juno_l2d", words(conversion.valueLow(), conversion.valueHigh()),
                        conversion.target());
            }
            case IrInstruction.DoubleToLong conversion -> {
                features.add(ShimFeature.DOUBLE);
                callForLong(output, frame, "juno_d2l", doubleWords(conversion.value()), conversion.targetLow(),
                        conversion.targetHigh());
            }
            default -> throw Architecture.XTENSA.unsupported(instruction.getClass().getSimpleName());
        }
    }

    private List<WordSource> words(Value... values) {
        return Arrays.stream(values).<WordSource>map(WordSource.FromValue::new).toList();
    }

    /** A {@code double}'s low then high word, the order of the register pair the ABI passes it in. */
    private List<WordSource> doubleWords(Value value) {
        return List.of(new WordSource.FromValueLow(value), new WordSource.FromValueHigh(value));
    }

    private List<WordSource> concat(List<WordSource> first, List<WordSource> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private void callForDouble(StringBuilder output, FrameLayout frame, String function, List<WordSource> words,
                               Value target) {
        xtensa.emitCall(output, frame, function, words);
        xtensa.store64(output, frame, "a10", "a11", target);
    }

    private void callForWord(StringBuilder output, FrameLayout frame, String function, List<WordSource> words,
                             Value target) {
        xtensa.emitCall(output, frame, function, words);
        xtensa.store(output, frame, "a10", target);
    }

    /** Calls {@code function} and splits its {@code int64_t} result in {@code a10}/{@code a11} into two slots. */
    private void callForLong(StringBuilder output, FrameLayout frame, String function, List<WordSource> words,
                             Value targetLow, Value targetHigh) {
        xtensa.emitCall(output, frame, function, words);
        xtensa.store(output, frame, "a10", targetLow);
        xtensa.store(output, frame, "a11", targetHigh);
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

    private String doubleHelperFor(FloatBinaryOp operation) {
        return switch (operation) {
            case ADD -> "juno_dadd";
            case SUBTRACT -> "juno_dsub";
            case MULTIPLY -> "juno_dmul";
            case DIVIDE -> "juno_ddiv";
            case REMAINDER -> "juno_drem";
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
}
