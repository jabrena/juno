package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.BytecodeToIr.*;
import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.ir.IrInstruction;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Constant-push and local-load opcode lowering (0-45), split out of {@link BytecodeToIr}. */
final class ConstAndLoadLowering {
    private ConstAndLoadLowering() {
    }
    static final BitSet PUSH_OP_OPCODES = bitSetOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
            20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
            40, 41, 42, 43, 44, 45);    static final BitSet PUSH_CONST_OPCODES = bitSetOf(
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);    static final BitSet IMMEDIATE_CONST_OPCODES = bitSetOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);    static final BitSet NARROW_LOAD_OPCODES = bitSetOf(21, 22, 23, 24, 25);    static Lowered lowerStackOp(LinkedMethod linked, Instruction instruction, int opcode,
                                 List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                 ValueTracking tracking, Map<Integer, Integer> slotArrayLength,
                                 Set<Integer> arrayParameterSlots, Map<Integer, RecordInstance> slotRecordInstance,
                                 Map<Integer, String> slotStringInstance, Set<Integer> singleAssignmentLocals) {
        if (PUSH_OP_OPCODES.get(opcode)) {
            return lowerPushOp(linked, instruction, opcode, instructions, stackBase, depth, nextValueId, tracking,
                    slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance);
        }
        return StoreLowering.lowerPopOp(instruction, opcode, instructions, stackBase, depth, nextValueId, tracking,
                singleAssignmentLocals, slotRecordInstance, slotStringInstance);
    }    static Lowered lowerPushOp(LinkedMethod linked, Instruction instruction, int opcode,
                                List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                ValueTracking tracking, Map<Integer, Integer> slotArrayLength,
                                Set<Integer> arrayParameterSlots, Map<Integer, RecordInstance> slotRecordInstance,
                                Map<Integer, String> slotStringInstance) {
        if (PUSH_CONST_OPCODES.get(opcode)) {
            return lowerPushConst(linked, instruction, opcode, instructions, stackBase, depth, nextValueId, tracking);
        }
        return lowerLoad(instruction, opcode, instructions, stackBase, depth, nextValueId, tracking, slotArrayLength,
                arrayParameterSlots, slotRecordInstance, slotStringInstance);
    }    static Lowered lowerPushConst(LinkedMethod linked, Instruction instruction, int opcode,
                                   List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                   ValueTracking tracking) {
        if (IMMEDIATE_CONST_OPCODES.get(opcode)) {
            return lowerImmediateConst(opcode, instructions, stackBase, depth, nextValueId, tracking);
        }
        return lowerOperandConst(linked, instruction, opcode, instructions, stackBase, depth, nextValueId, tracking);
    }    static Lowered lowerImmediateConst(int opcode, List<IrInstruction> instructions, int stackBase, int depth,
                                        int nextValueId, ValueTracking tracking) {
        switch (opcode) {

                    case 0 -> { }

                    case 1 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, 0, tracking);
                        depth++;
                    }

                    case 2 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, -1, tracking);
                        depth++;
                    }

                    case 3, 4, 5, 6, 7, 8 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, opcode - 3, tracking);
                        depth++;
                    }

                    case 9, 10 -> {
                        nextValueId = pushWideConst(instructions, stackBase, depth, nextValueId, opcode - 9, tracking);
                        depth += 2;
                    }

                    case 11, 12, 13 -> {
                        nextValueId = pushFloatConst(instructions, stackBase, depth, nextValueId,
                                (float) (opcode - 11), tracking);
                        depth++;
                    }

                    case 14, 15 -> {
                        nextValueId = pushDoubleConst(instructions, stackBase, depth, nextValueId,
                                (double) (opcode - 14), tracking);
                        depth += 2;
                    }
            default -> throw new IllegalStateException("unreachable immediate-const opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }    static Lowered lowerOperandConst(LinkedMethod linked, Instruction instruction, int opcode,
                                      List<IrInstruction> instructions, int stackBase, int depth, int nextValueId,
                                      ValueTracking tracking) {
        switch (opcode) {

                    case 20 -> {
                        if (linked.owner().constantPool().isDouble(instruction.operandA())) {
                            nextValueId = pushDoubleConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().doubleValue(instruction.operandA()), tracking);
                        } else {
                            long value = linked.owner().constantPool().longValue(instruction.operandA());
                            nextValueId = pushWideConst(instructions, stackBase, depth, nextValueId, value, tracking);
                        }
                        depth += 2;
                    }

                    case 16, 17 -> {
                        nextValueId = pushConst(instructions, stackBase, depth, nextValueId, instruction.operandA(), tracking);
                        depth++;
                    }

                    case 18, 19 -> {
                        if (linked.owner().constantPool().isFloat(instruction.operandA())) {
                            nextValueId = pushFloatConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().floatValue(instruction.operandA()), tracking);
                        } else if (linked.owner().constantPool().isString(instruction.operandA())) {
                            nextValueId = pushStringConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().string(instruction.operandA()), tracking);
                        } else {
                            nextValueId = pushConst(instructions, stackBase, depth, nextValueId,
                                    linked.owner().constantPool().integer(instruction.operandA()), tracking);
                        }
                        depth++;
                    }
            default -> throw new IllegalStateException("unreachable operand-const opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }    static Lowered lowerLoad(Instruction instruction, int opcode, List<IrInstruction> instructions, int stackBase,
                              int depth, int nextValueId, ValueTracking tracking,
                              Map<Integer, Integer> slotArrayLength, Set<Integer> arrayParameterSlots,
                              Map<Integer, RecordInstance> slotRecordInstance, Map<Integer, String> slotStringInstance) {
        if (NARROW_LOAD_OPCODES.get(opcode)) {
            return lowerNarrowLoad(instruction, opcode, instructions, stackBase, depth, nextValueId, tracking,
                    slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance);
        }
        return lowerLoadN(opcode, instructions, stackBase, depth, nextValueId, tracking, slotArrayLength,
                arrayParameterSlots, slotRecordInstance, slotStringInstance);
    }    static Lowered lowerNarrowLoad(Instruction instruction, int opcode, List<IrInstruction> instructions,
                                    int stackBase, int depth, int nextValueId, ValueTracking tracking,
                                    Map<Integer, Integer> slotArrayLength, Set<Integer> arrayParameterSlots,
                                    Map<Integer, RecordInstance> slotRecordInstance,
                                    Map<Integer, String> slotStringInstance) {
        switch (opcode) {

                    case 21, 25 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, instruction.operandA(),
                                slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance, tracking);
                        depth++;
                    }

                    case 22 -> {
                        nextValueId = pushWideLoad(instructions, stackBase, depth, nextValueId,
                                instruction.operandA(), tracking);
                        depth += 2;
                    }

                    case 23 -> {
                        nextValueId = pushFloatLoad(instructions, stackBase, depth, nextValueId,
                                instruction.operandA(), tracking);
                        depth++;
                    }

                    case 24 -> {
                        nextValueId = pushDoubleLoad(instructions, stackBase, depth, nextValueId,
                                instruction.operandA(), tracking);
                        depth += 2;
                    }
            default -> throw new IllegalStateException("unreachable narrow-load opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }    static Lowered lowerLoadN(int opcode, List<IrInstruction> instructions, int stackBase, int depth,
                               int nextValueId, ValueTracking tracking, Map<Integer, Integer> slotArrayLength,
                               Set<Integer> arrayParameterSlots, Map<Integer, RecordInstance> slotRecordInstance,
                               Map<Integer, String> slotStringInstance) {
        switch (opcode) {

                    case 30, 31, 32, 33 -> {
                        nextValueId = pushWideLoad(instructions, stackBase, depth, nextValueId, opcode - 30, tracking);
                        depth += 2;
                    }

                    case 34, 35, 36, 37 -> {
                        nextValueId = pushFloatLoad(instructions, stackBase, depth, nextValueId,
                                opcode - 34, tracking);
                        depth++;
                    }

                    case 38, 39, 40, 41 -> {
                        nextValueId = pushDoubleLoad(instructions, stackBase, depth, nextValueId,
                                opcode - 38, tracking);
                        depth += 2;
                    }

                    case 26, 27, 28, 29 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, opcode - 26,
                                slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance, tracking);
                        depth++;
                    }

                    case 42, 43, 44, 45 -> {
                        nextValueId = pushLoad(instructions, stackBase, depth, nextValueId, opcode - 42,
                                slotArrayLength, arrayParameterSlots, slotRecordInstance, slotStringInstance, tracking);
                        depth++;
                    }
            default -> throw new IllegalStateException("unreachable load opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }}
