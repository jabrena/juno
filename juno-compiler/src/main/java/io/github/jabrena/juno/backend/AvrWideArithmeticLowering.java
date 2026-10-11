package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.Value;

import java.util.List;
import java.util.Set;

/**
 * {@code long}/{@code float}/{@code double} on AVR: constants, word moves, sign-extension and sign flips inline,
 * everything else a call to the runtime shim's thunks ({@code long} arithmetic in C, floating point in
 * {@link SoftFloatRuntime}). A two-word result comes back in r18-r25, its low word first.
 */
final class AvrWideArithmeticLowering {
    private static final int A = AvrEmitter.A;
    private static final int B = AvrEmitter.B;

    private final AvrEmitter avr;
    private final Set<ShimFeature> features;

    AvrWideArithmeticLowering(AvrEmitter avr, Set<ShimFeature> features) {
        this.avr = avr;
        this.features = features;
    }

    /** {@code long}/{@code float}/{@code double}: inline moves and sign flips, shim thunks for everything else. */
    void emit(StringBuilder output, FrameLayout frame, IrInstruction instruction) {
        switch (instruction) {
            case IrInstruction.LongConst constant -> {
                avr.loadImmediate(output, A, (int) constant.value());
                avr.store(output, frame, A, constant.targetLow());
                avr.loadImmediate(output, A, (int) (constant.value() >>> 32));
                avr.store(output, frame, A, constant.targetHigh());
            }
            case IrInstruction.LongBinary binary -> {
                features.add(ShimFeature.LONG);
                wideCall(output, frame, longHelper(binary.operation()), List.of(
                        word(binary.leftLow()), word(binary.leftHigh()),
                        word(binary.rightLow()), word(binary.rightHigh())));
                avr.store(output, frame, B, binary.targetLow());
                avr.store(output, frame, A, binary.targetHigh());
            }
            case IrInstruction.LongShift shift -> {
                features.add(ShimFeature.LONG);
                wideCall(output, frame, switch (shift.operation()) {
                    case SHIFT_LEFT -> "juno_lshl";
                    case SHIFT_RIGHT -> "juno_lshr";
                    case UNSIGNED_SHIFT_RIGHT -> "juno_lushr";
                    default -> throw new IllegalStateException("Not a long shift: " + shift.operation());
                }, List.of(word(shift.valueLow()), word(shift.valueHigh()), word(shift.shiftAmount())));
                avr.store(output, frame, B, shift.targetLow());
                avr.store(output, frame, A, shift.targetHigh());
            }
            case IrInstruction.LongNegate negate -> {
                features.add(ShimFeature.LONG);
                wideCall(output, frame, "juno_lneg", List.of(word(negate.valueLow()), word(negate.valueHigh())));
                avr.store(output, frame, B, negate.targetLow());
                avr.store(output, frame, A, negate.targetHigh());
            }
            case IrInstruction.LongCompare compare -> {
                features.add(ShimFeature.LONG);
                wideCall(output, frame, "juno_lcmp", List.of(word(compare.leftLow()), word(compare.leftHigh()),
                        word(compare.rightLow()), word(compare.rightHigh())));
                avr.store(output, frame, A, compare.target());
            }
            case IrInstruction.IntToLong widen -> {
                avr.load(output, frame, A, widen.value());
                avr.store(output, frame, A, widen.targetLow());
                output.append("    lsl r25\n    sbc r22, r22\n    mov r23, r22\n    mov r24, r22\n    mov r25, r22\n");
                avr.store(output, frame, A, widen.targetHigh());
            }
            case IrInstruction.LongToInt narrow -> {
                avr.load(output, frame, A, narrow.valueLow());
                avr.store(output, frame, A, narrow.target());
            }
            case IrInstruction.FloatConst constant -> {
                avr.loadImmediate(output, A, Float.floatToRawIntBits(constant.value()));
                avr.store(output, frame, A, constant.target());
            }
            case IrInstruction.FloatBinary binary -> {
                features.add(ShimFeature.FLOAT);
                wideCall(output, frame, floatHelper("juno_f", binary.operation()),
                        List.of(word(binary.left()), word(binary.right())));
                avr.store(output, frame, A, binary.target());
            }
            case IrInstruction.FloatNegate negate -> {
                avr.load(output, frame, A, negate.value());
                output.append("    subi r25, 0x80\n");
                avr.store(output, frame, A, negate.target());
            }
            case IrInstruction.FloatCompare compare -> {
                features.add(ShimFeature.FLOAT);
                wideCall(output, frame, "juno_fcmp", List.of(word(compare.left()), word(compare.right()),
                        new WordSource.Immediate(compare.nanResult())));
                avr.store(output, frame, A, compare.target());
            }
            case IrInstruction.IntToFloat conversion -> narrowConversion(output, frame, ShimFeature.FLOAT, "juno_i2f",
                    List.of(word(conversion.value())), conversion.target());
            case IrInstruction.FloatToInt conversion -> narrowConversion(output, frame, ShimFeature.FLOAT, "juno_f2i",
                    List.of(word(conversion.value())), conversion.target());
            case IrInstruction.DoubleConst constant -> {
                long bits = Double.doubleToRawLongBits(constant.value());
                avr.loadImmediate(output, A, (int) bits);
                avr.loadImmediate(output, B, (int) (bits >>> 32));
                avr.store64(output, frame, A, B, constant.target());
            }
            case IrInstruction.DoubleBinary binary -> {
                features.add(ShimFeature.DOUBLE);
                wideCall(output, frame, floatHelper("juno_d", binary.operation()),
                        List.of(low(binary.left()), high(binary.left()), low(binary.right()), high(binary.right())));
                avr.store64(output, frame, B, A, binary.target());
            }
            case IrInstruction.DoubleNegate negate -> {
                avr.load64(output, frame, B, A, negate.value());
                output.append("    subi r25, 0x80\n");
                avr.store64(output, frame, B, A, negate.target());
            }
            case IrInstruction.DoubleCompare compare -> {
                features.add(ShimFeature.DOUBLE);
                wideCall(output, frame, "juno_dcmp", List.of(low(compare.left()), high(compare.left()),
                        low(compare.right()), high(compare.right()), new WordSource.Immediate(compare.nanResult())));
                avr.store(output, frame, A, compare.target());
            }
            case IrInstruction.IntToDouble conversion -> wideConversion(output, frame, ShimFeature.DOUBLE, "juno_i2d",
                    List.of(word(conversion.value())), conversion.target());
            case IrInstruction.DoubleToInt conversion -> narrowConversion(output, frame, ShimFeature.DOUBLE,
                    "juno_d2i", List.of(low(conversion.value()), high(conversion.value())), conversion.target());
            case IrInstruction.FloatToDouble conversion -> wideConversion(output, frame, ShimFeature.DOUBLE,
                    "juno_f2d", List.of(word(conversion.value())), conversion.target());
            case IrInstruction.DoubleToFloat conversion -> narrowConversion(output, frame, ShimFeature.DOUBLE,
                    "juno_d2f", List.of(low(conversion.value()), high(conversion.value())), conversion.target());
            case IrInstruction.LongToDouble conversion -> wideConversion(output, frame, ShimFeature.DOUBLE,
                    "juno_l2d", List.of(word(conversion.valueLow()), word(conversion.valueHigh())),
                    conversion.target());
            case IrInstruction.DoubleToLong conversion -> {
                features.add(ShimFeature.DOUBLE);
                wideCall(output, frame, "juno_d2l", List.of(low(conversion.value()), high(conversion.value())));
                avr.store(output, frame, B, conversion.targetLow());
                avr.store(output, frame, A, conversion.targetHigh());
            }
            case IrInstruction.PackLong packed -> {
                avr.load(output, frame, B, packed.valueLow());
                avr.load(output, frame, A, packed.valueHigh());
                avr.store64(output, frame, B, A, packed.target());
            }
            case IrInstruction.UnpackLong unpacked -> {
                avr.load64(output, frame, B, A, unpacked.value());
                avr.store(output, frame, B, unpacked.targetLow());
                avr.store(output, frame, A, unpacked.targetHigh());
            }
            case IrInstruction.LongToFloat conversion -> narrowConversion(output, frame, ShimFeature.FLOAT, "juno_l2f",
                    List.of(word(conversion.valueLow()), word(conversion.valueHigh())), conversion.target());
            case IrInstruction.FloatToLong conversion -> {
                features.add(ShimFeature.FLOAT);
                wideCall(output, frame, "juno_f2l", List.of(word(conversion.value())));
                avr.store(output, frame, B, conversion.targetLow());
                avr.store(output, frame, A, conversion.targetHigh());
            }
            default -> throw AvrAsmBackend.unsupported(instruction.getClass().getSimpleName());
        }
    }

    private static WordSource word(Value value) {
        return new WordSource.FromValue(value);
    }

    private static WordSource low(Value value) {
        return new WordSource.FromValueLow(value);
    }

    private static WordSource high(Value value) {
        return new WordSource.FromValueHigh(value);
    }

    private void wideCall(StringBuilder output, FrameLayout frame, String function, List<WordSource> words) {
        avr.emitThunkCall(output, frame, function, words);
    }

    private void narrowConversion(StringBuilder output, FrameLayout frame, ShimFeature feature, String function,
                                  List<WordSource> words, Value target) {
        features.add(feature);
        avr.emitThunkCall(output, frame, function, words);
        avr.store(output, frame, A, target);
    }

    private void wideConversion(StringBuilder output, FrameLayout frame, ShimFeature feature, String function,
                                List<WordSource> words, Value target) {
        features.add(feature);
        avr.emitThunkCall(output, frame, function, words);
        avr.store64(output, frame, B, A, target);
    }

    private static String longHelper(BinaryOp operation) {
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
                    throw new IllegalStateException("Long shifts are LongShift: " + operation);
        };
    }

    private static String floatHelper(String prefix, FloatBinaryOp operation) {
        return prefix + switch (operation) {
            case ADD -> "add";
            case SUBTRACT -> "sub";
            case MULTIPLY -> "mul";
            case DIVIDE -> "div";
            case REMAINDER -> "rem";
        };
    }
}
