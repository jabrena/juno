package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.BytecodeToIr.*;
import static io.github.jabrena.juno.lowering.ControlFlowLowering.*;
import static io.github.jabrena.juno.lowering.ConversionLowering.*;
import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.JavaClass;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.FloatBinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.UnaryOp;

import java.util.BitSet;
import java.util.List;
import java.util.Map;

/** Binary arithmetic, shift, and bitwise opcode lowering (96-131), split out of {@link BytecodeToIr}. */
final class ArithmeticLowering {
    private ArithmeticLowering() {
    }
    static final BitSet BINARY_ARITHMETIC_OPCODES = bitSetOf(
            96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115,
            116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131);    static final BitSet ADD_SUB_MUL_DIV_REM_OPCODES = bitSetOf(
            96, 97, 98, 99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115);    static ArithLowered lowerArithmetic(LinkedMethod linked, Instruction instruction, int opcode, int irBlockStart,
                                         List<IrInstruction> instructions, List<IrBasicBlock> blocks, int stackBase,
                                         int depth, int nextValueId, Map<String, JavaClass> classes,
                                         ValueTracking tracking) {
        if (BINARY_ARITHMETIC_OPCODES.get(opcode)) {
            return lowerBinaryArithmetic(linked, instruction, opcode, irBlockStart, instructions, blocks, stackBase,
                    depth, nextValueId, classes, tracking);
        }
        Lowered lowered = lowerConversionOrCompare(instruction, opcode, instructions, stackBase, depth, nextValueId,
                tracking);
        return new ArithLowered(lowered.nextValueId(), lowered.depth(), irBlockStart);
    }    static ArithLowered lowerBinaryArithmetic(LinkedMethod linked, Instruction instruction, int opcode,
                                               int irBlockStart, List<IrInstruction> instructions,
                                               List<IrBasicBlock> blocks, int stackBase, int depth, int nextValueId,
                                               Map<String, JavaClass> classes, ValueTracking tracking) {
        if (ADD_SUB_MUL_DIV_REM_OPCODES.get(opcode)) {
            return lowerAddSubMulDivRem(linked, instruction, opcode, irBlockStart, instructions, blocks, stackBase,
                    depth, nextValueId, classes, tracking);
        }
        Lowered lowered = lowerNegateShiftBitwise(opcode, instructions, stackBase, depth, nextValueId, tracking);
        return new ArithLowered(lowered.nextValueId(), lowered.depth(), irBlockStart);
    }    static ArithLowered lowerAddSubMulDivRem(LinkedMethod linked, Instruction instruction, int opcode,
                                              int irBlockStart, List<IrInstruction> instructions,
                                              List<IrBasicBlock> blocks, int stackBase, int depth, int nextValueId,
                                              Map<String, JavaClass> classes, ValueTracking tracking) {
        switch (opcode) {

                    case 96 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.ADD, tracking);
                        depth--;
                    }

                    case 97 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.ADD, tracking);
                        depth -= 2;
                    }

                    case 98 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.ADD, tracking);
                        depth--;
                    }

                    case 99 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.ADD, tracking);
                        depth -= 2;
                    }

                    case 100 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SUBTRACT, tracking);
                        depth--;
                    }

                    case 101 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SUBTRACT, tracking);
                        depth -= 2;
                    }

                    case 102 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.SUBTRACT, tracking);
                        depth--;
                    }

                    case 103 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.SUBTRACT, tracking);
                        depth -= 2;
                    }

                    case 104 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.MULTIPLY, tracking);
                        depth--;
                    }

                    case 105 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.MULTIPLY, tracking);
                        depth -= 2;
                    }

                    case 106 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.MULTIPLY, tracking);
                        depth--;
                    }

                    case 107 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.MULTIPLY, tracking);
                        depth -= 2;
                    }

                    case 108 -> {
                        DivisorGuard guard = guardZeroDivisor(linked, instruction, false, irBlockStart,
                                instructions, blocks, stackBase, depth, nextValueId, classes, tracking);
                        nextValueId = guard.nextValueId();
                        irBlockStart = guard.blockStart();
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.DIVIDE, tracking);
                        depth--;
                    }

                    case 109 -> {
                        DivisorGuard guard = guardZeroDivisor(linked, instruction, true, irBlockStart,
                                instructions, blocks, stackBase, depth, nextValueId, classes, tracking);
                        nextValueId = guard.nextValueId();
                        irBlockStart = guard.blockStart();
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.DIVIDE, tracking);
                        depth -= 2;
                    }

                    case 110 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.DIVIDE, tracking);
                        depth--;
                    }

                    case 111 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.DIVIDE, tracking);
                        depth -= 2;
                    }

                    case 112 -> {
                        DivisorGuard guard = guardZeroDivisor(linked, instruction, false, irBlockStart,
                                instructions, blocks, stackBase, depth, nextValueId, classes, tracking);
                        nextValueId = guard.nextValueId();
                        irBlockStart = guard.blockStart();
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.REMAINDER, tracking);
                        depth--;
                    }

                    case 113 -> {
                        DivisorGuard guard = guardZeroDivisor(linked, instruction, true, irBlockStart,
                                instructions, blocks, stackBase, depth, nextValueId, classes, tracking);
                        nextValueId = guard.nextValueId();
                        irBlockStart = guard.blockStart();
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.REMAINDER, tracking);
                        depth -= 2;
                    }

                    case 114 -> {
                        nextValueId = pushFloatBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.REMAINDER, tracking);
                        depth--;
                    }

                    case 115 -> {
                        nextValueId = pushDoubleBinary(instructions, stackBase, depth, nextValueId,
                                FloatBinaryOp.REMAINDER, tracking);
                        depth -= 2;
                    }
            default -> throw new IllegalStateException("unreachable add/sub/mul/div/rem opcode " + opcode);
        }
        return new ArithLowered(nextValueId, depth, irBlockStart);
    }    static Lowered lowerNegateShiftBitwise(int opcode, List<IrInstruction> instructions, int stackBase, int depth,
                                            int nextValueId, ValueTracking tracking) {
        switch (opcode) {

                    case 116 -> nextValueId = pushUnary(instructions, stackBase, depth, nextValueId, UnaryOp.NEGATE, tracking);

                    case 117 -> nextValueId = pushLongNegate(instructions, stackBase, depth, nextValueId, tracking);

                    case 118 -> nextValueId = pushFloatNegate(instructions, stackBase, depth, nextValueId, tracking);

                    case 119 -> nextValueId = pushDoubleNegate(instructions, stackBase, depth, nextValueId, tracking);

                    case 120 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_LEFT, tracking);
                        depth--;
                    }

                    case 121 -> {
                        nextValueId = pushLongShift(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_LEFT, tracking);
                        depth--;
                    }

                    case 122 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_RIGHT, tracking);
                        depth--;
                    }

                    case 123 -> {
                        nextValueId = pushLongShift(instructions, stackBase, depth, nextValueId, BinaryOp.SHIFT_RIGHT, tracking);
                        depth--;
                    }

                    case 124 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId,
                                BinaryOp.UNSIGNED_SHIFT_RIGHT, tracking);
                        depth--;
                    }

                    case 125 -> {
                        nextValueId = pushLongShift(instructions, stackBase, depth, nextValueId,
                                BinaryOp.UNSIGNED_SHIFT_RIGHT, tracking);
                        depth--;
                    }

                    case 126 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.AND, tracking);
                        depth--;
                    }

                    case 127 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.AND, tracking);
                        depth -= 2;
                    }

                    case 128 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.OR, tracking);
                        depth--;
                    }

                    case 129 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.OR, tracking);
                        depth -= 2;
                    }

                    case 130 -> {
                        nextValueId = pushBinary(instructions, stackBase, depth, nextValueId, BinaryOp.XOR, tracking);
                        depth--;
                    }

                    case 131 -> {
                        nextValueId = pushLongBinary(instructions, stackBase, depth, nextValueId, BinaryOp.XOR, tracking);
                        depth -= 2;
                    }
            default -> throw new IllegalStateException("unreachable negate/shift/bitwise opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }}
