package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.BytecodeToIr.*;
import static io.github.jabrena.juno.lowering.LocalSlotAnalysis.*;
import static io.github.jabrena.juno.lowering.StackValueOps.*;

import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local-store and stack-shuffle opcode lowering (54-89), split out of {@link BytecodeToIr}. */
final class StoreLowering {
    private StoreLowering() {
    }
    static final BitSet STORE_OPCODES = bitSetOf(54, 55, 56, 57, 58, 59, 60, 61, 62);    static Lowered lowerPopOp(Instruction instruction, int opcode, List<IrInstruction> instructions, int stackBase,
                               int depth, int nextValueId, ValueTracking tracking,
                               Set<Integer> singleAssignmentLocals, Map<Integer, RecordInstance> slotRecordInstance,
                               Map<Integer, String> slotStringInstance) {
        if (STORE_OPCODES.get(opcode)) {
            return lowerStore(instruction, opcode, instructions, stackBase, depth, nextValueId, tracking,
                    singleAssignmentLocals, slotRecordInstance, slotStringInstance);
        }
        return lowerStoreWideOrShuffle(instruction, opcode, instructions, stackBase, depth, nextValueId, tracking,
                singleAssignmentLocals, slotRecordInstance, slotStringInstance);
    }    static Lowered lowerStore(Instruction instruction, int opcode, List<IrInstruction> instructions, int stackBase,
                               int depth, int nextValueId, ValueTracking tracking,
                               Set<Integer> singleAssignmentLocals, Map<Integer, RecordInstance> slotRecordInstance,
                               Map<Integer, String> slotStringInstance) {
        switch (opcode) {

                    case 54, 58 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                        trackRecordLocalIfSingleAssignment(instruction.operandA(), popped.value(), tracking,
                                singleAssignmentLocals, slotRecordInstance);
                        trackStringLocalIfSingleAssignment(instruction.operandA(), popped.value(), tracking,
                                singleAssignmentLocals, slotStringInstance);
                    }

                    case 55 -> {
                        depth -= 2;
                        WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.low()));
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA() + 1, popped.high()));
                    }

                    case 56 -> {
                        Popped popped = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                    }

                    case 57 -> {
                        depth -= 2;
                        Popped popped = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(instruction.operandA(), popped.value()));
                    }

                    case 59, 60, 61, 62 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 59, popped.value()));
                        trackRecordLocalIfSingleAssignment(opcode - 59, popped.value(), tracking,
                                singleAssignmentLocals, slotRecordInstance);
                        trackStringLocalIfSingleAssignment(opcode - 59, popped.value(), tracking,
                                singleAssignmentLocals, slotStringInstance);
                    }
            default -> throw new IllegalStateException("unreachable store opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }    static Lowered lowerStoreWideOrShuffle(Instruction instruction, int opcode, List<IrInstruction> instructions,
                                            int stackBase, int depth, int nextValueId, ValueTracking tracking,
                                            Set<Integer> singleAssignmentLocals,
                                            Map<Integer, RecordInstance> slotRecordInstance,
                                            Map<Integer, String> slotStringInstance) {
        switch (opcode) {

                    case 63, 64, 65, 66 -> {
                        depth -= 2;
                        WidePopped popped = popWide(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        int local = opcode - 63;
                        instructions.add(new IrInstruction.StoreLocal(local, popped.low()));
                        instructions.add(new IrInstruction.StoreLocal(local + 1, popped.high()));
                    }

                    case 67, 68, 69, 70 -> {
                        Popped popped = popFloat(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 67, popped.value()));
                    }

                    case 71, 72, 73, 74 -> {
                        depth -= 2;
                        Popped popped = popDouble(instructions, stackBase, depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 71, popped.value()));
                    }

                    case 75, 76, 77, 78 -> {
                        Popped popped = pop(instructions, stackBase, --depth, nextValueId, tracking);
                        nextValueId = popped.nextValueId();
                        instructions.add(new IrInstruction.StoreLocal(opcode - 75, popped.value()));
                        trackRecordLocalIfSingleAssignment(opcode - 75, popped.value(), tracking,
                                singleAssignmentLocals, slotRecordInstance);
                        trackStringLocalIfSingleAssignment(opcode - 75, popped.value(), tracking,
                                singleAssignmentLocals, slotStringInstance);
                    }

                    case 87 -> depth--;

                    case 88 -> depth -= 2;

                    case 89 -> {
                        JunoType type = tracking.stackSlotType(stackBase + depth - 1);
                        Value top = new Value(nextValueId++, type == null ? JunoType.INT32 : type);
                        instructions.add(new IrInstruction.LoadLocal(top, stackBase + depth - 1));
                        tracking.recordPop(stackBase + depth - 1, top);
                        storeToStack(instructions, stackBase, depth, top, tracking);
                        depth++;
                    }
            default -> throw new IllegalStateException("unreachable pop opcode " + opcode);
        }
        return new Lowered(nextValueId, depth);
    }}
