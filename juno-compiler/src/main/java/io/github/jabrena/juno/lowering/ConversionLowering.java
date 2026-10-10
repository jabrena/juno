package io.github.jabrena.juno.lowering;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;

import java.util.BitSet;
import java.util.List;

import static io.github.jabrena.juno.lowering.BytecodeToIr.*;
import static io.github.jabrena.juno.lowering.StackValueOps.*;

/** Numeric conversion, {@code iinc}, and compare-to-int opcode lowering (132-152), split out of {@link BytecodeToIr}. */
final class ConversionLowering {
    private ConversionLowering() {
    }
    static final BitSet WIDENING_CONVERSION_OPCODES = bitSetOf(133, 134, 135, 136, 137, 138, 139, 140, 141);    static Lowered lowerConversionOrCompare(Instruction instruction, int opcode, List<IrInstruction> instructions,
                                             int stackBase, int depth, int nextValueId, ValueTracking tracking) {
        if (WIDENING_CONVERSION_OPCODES.get(opcode)) {
            return lowerWideningConversion(opcode, instructions, stackBase, depth, nextValueId, tracking);
        }
        return lowerNarrowConversionOrCompare(instruction, opcode, instructions, stackBase, depth, nextValueId,
                tracking);
    }    static Lowered lowerWideningConversion(int opcode, List<IrInstruction> instructions, int stackBase, int depth,
                                            int nextValueId, ValueTracking tracking) {
        switch (opcode) {

                    case 133 -> {
                        Popped value = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value targetLow = Value.int32(nextValueId++);
                        Value targetHigh = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.IntToLong(targetLow, targetHigh, value.value()));
                        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
                        depth += 2;
                    }

                    case 134 -> {
                        Popped value = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float32(nextValueId++);
                        instructions.add(new IrInstruction.IntToFloat(target, value.value()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }

                    case 135 -> {
                        Popped value = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float64(nextValueId++);
                        instructions.add(new IrInstruction.IntToDouble(target, value.value()));
                        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                        depth += 2;
                    }

                    case 136 -> {
                        depth -= 2;
                        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.LongToInt(target, value.low(), value.high()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }

                    case 137 -> {
                        depth -= 2;
                        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float32(nextValueId++);
                        instructions.add(new IrInstruction.LongToFloat(target, value.low(), value.high()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }

                    case 138 -> {
                        depth -= 2;
                        WidePopped value = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float64(nextValueId++);
                        instructions.add(new IrInstruction.LongToDouble(target, value.low(), value.high()));
                        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                        depth += 2;
                    }

                    case 139 -> {
                        Popped value = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.FloatToInt(target, value.value()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }

                    case 140 -> {
                        Popped value = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value targetLow = Value.int32(nextValueId++);
                        Value targetHigh = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.FloatToLong(targetLow, targetHigh, value.value()));
                        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
                        depth += 2;
                    }

                    case 141 -> {
                        Popped value = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float64(nextValueId++);
                        instructions.add(new IrInstruction.FloatToDouble(target, value.value()));
                        storeDoubleToStack(instructions, stackBase, depth, target, tracking);
                        depth += 2;
                    }
            default -> throw new IllegalStateException("unreachable widening-conversion opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }    static Lowered lowerNarrowConversionOrCompare(Instruction instruction, int opcode,
                                                   List<IrInstruction> instructions, int stackBase, int depth,
                                                   int nextValueId, ValueTracking tracking) {
        switch (opcode) {

                    case 142 -> {
                        depth -= 2;
                        Popped value = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleToInt(target, value.value()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }

                    case 143 -> {
                        depth -= 2;
                        Popped value = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value targetLow = Value.int32(nextValueId++);
                        Value targetHigh = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleToLong(targetLow, targetHigh, value.value()));
                        storeWideToStack(instructions, stackBase, depth, targetLow, targetHigh, tracking);
                        depth += 2;
                    }

                    case 144 -> {
                        depth -= 2;
                        Popped value = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = value.nextValueId();
                        Value target = Value.float32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleToFloat(target, value.value()));
                        storeToStack(instructions, stackBase, depth, target, tracking);
                        depth++;
                    }

                    case 148 -> {
                        depth -= 2;
                        WidePopped right = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        depth -= 2;
                        WidePopped left = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value result = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.LongCompare(
                                result, left.low(), left.high(), right.low(), right.high()));
                        storeToStack(instructions, stackBase, depth, result, tracking);
                        depth++;
                    }

                    case 149, 150 -> {
                        Popped right = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        Popped left = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value result = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.FloatCompare(
                                result, left.value(), right.value(), opcode == 149 ? -1 : 1));
                        storeToStack(instructions, stackBase, depth, result, tracking);
                        depth++;
                    }

                    case 151, 152 -> {
                        depth -= 2;
                        Popped right = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = right.nextValueId();
                        depth -= 2;
                        Popped left = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = left.nextValueId();
                        Value result = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.DoubleCompare(
                                result, left.value(), right.value(), opcode == 151 ? -1 : 1));
                        storeToStack(instructions, stackBase, depth, result, tracking);
                        depth++;
                    }

                    case 132 -> {
                        Value loaded = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.LoadLocal(loaded, instruction.operandA()));
                        Value amount = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Const(amount, instruction.operandB()));
                        Value sum = Value.int32(nextValueId++);
                        instructions.add(new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, amount));
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), sum));
                    }

                    case 145 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_BYTE, tracking);

                    case 146 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_CHAR, tracking);

                    case 147 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.TO_SHORT, tracking);
            default -> throw new IllegalStateException("unreachable conversion/compare opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }}
