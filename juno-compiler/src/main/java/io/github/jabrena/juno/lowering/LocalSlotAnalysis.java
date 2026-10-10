package io.github.jabrena.juno.lowering;

import static io.github.jabrena.juno.lowering.ConstantAndStackSupport.*;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.analysis.BasicBlock;
import io.github.jabrena.juno.analysis.ControlFlowGraph;
import io.github.jabrena.juno.analysis.Terminator;
import io.github.jabrena.juno.bytecode.Instruction;
import io.github.jabrena.juno.classfile.ExceptionHandler;
import io.github.jabrena.juno.linker.Descriptor;
import io.github.jabrena.juno.linker.LinkedMethod;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Local-variable-slot and array-parameter dataflow analysis, split out of {@link BytecodeToIr}. */
final class LocalSlotAnalysis {
    private LocalSlotAnalysis() {
    }
    static Set<Integer> arrayParameterSlots(Descriptor methodDescriptor, boolean isStatic) {
        Set<Integer> slots = new HashSet<>();
        int slot = isStatic ? 0 : 1;
        for (String parameter : methodDescriptor.parameters()) {
            if (Descriptor.isArrayType(parameter)) {
                slots.add(slot);
            }
            slot += Descriptor.jvmSlots(parameter);
        }
        return slots;
    }    static Map<Integer, Integer> computeSingleAssignmentArrayLocals(LinkedMethod linked) {
        List<Instruction> all = linked.instructions();
        Map<Integer, Integer> storeCounts = new HashMap<>();
        Map<Integer, Integer> candidateLength = new HashMap<>();
        for (int index = 0; index < all.size(); index++) {
            Integer slot = astoreSlot(all.get(index));
            if (slot == null) {
                continue;
            }
            storeCounts.merge(slot, 1, Integer::sum);
            if (index < 2) {
                continue;
            }
            Instruction newArrayInstruction = all.get(index - 1);
            Instruction lengthPush = all.get(index - 2);
            if ((newArrayInstruction.opcode() == 188
                    && ArrayElementType.fromAtype(newArrayInstruction.operandA()).isPresent())
                    || newArrayInstruction.opcode() == 189) {
                Integer length = constantPushValue(lengthPush, linked);
                if (length != null) {
                    candidateLength.put(slot, length);
                }
            }
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : candidateLength.entrySet()) {
            if (storeCounts.get(entry.getKey()) == 1) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }    static Integer astoreSlot(Instruction instruction) {
        return switch (instruction.opcode()) {
            case 58 -> instruction.operandA();
            case 75, 76, 77, 78 -> instruction.opcode() - 75;
            default -> null;
        };
    }    static Map<Integer, Integer> computeEntryDepths(LinkedMethod linked) {
        ControlFlowGraph cfg = linked.controlFlowGraph();
        Map<Integer, Integer> entryDepth = new HashMap<>();
        Deque<Integer> work = new ArrayDeque<>();
        int entryStart = cfg.entry().start();
        entryDepth.put(entryStart, 0);
        work.add(entryStart);
        // A handler always starts with just the caught exception on the stack, even when no explicit
        // throw reaches it (its range may hold only calls, whose exceptions Juno doesn't propagate).
        for (ExceptionHandler handler : linked.method().exceptionHandlers()) {
            if (entryDepth.putIfAbsent(handler.handlerPc(), 1) == null) {
                work.add(handler.handlerPc());
            }
        }
        while (!work.isEmpty()) {
            int blockStart = work.removeFirst();
            BasicBlock block = cfg.blockAt(blockStart).orElseThrow();
            int depth = entryDepth.get(blockStart);
            for (Instruction instruction : block.instructions()) {
                depth += ConstantAndStackSupport.stackDelta(linked, instruction);
            }
            if (block.terminator() instanceof Terminator.Throw) {
                continue;
            }
            for (int successor : successorsOf(block.terminator())) {
                Integer existing = entryDepth.get(successor);
                if (existing == null) {
                    entryDepth.put(successor, depth);
                    work.add(successor);
                } else if (existing != depth) {
                    throw new CompileException(linked.method().reference().displayName()
                            + ": inconsistent operand-stack depth entering block at offset " + successor
                            + " (" + existing + " from an earlier predecessor, " + depth
                            + " from block at offset " + blockStart + ")");
                }
            }
        }
        return entryDepth;
    }    static List<Integer> successorsOf(Terminator terminator) {
        return switch (terminator) {
            case Terminator.Jump jump -> List.of(jump.target());
            case Terminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case Terminator.Fallthrough fallthrough -> List.of(fallthrough.target());
            case Terminator.Return ignored -> List.of();
            case Terminator.Throw thrown -> thrown.handlers();
            case Terminator.Switch switched -> {
                List<Integer> targets = new ArrayList<>(switched.targets());
                targets.add(switched.defaultTarget());
                yield List.copyOf(targets);
            }
        };
    }    static Set<Integer> computeSingleAssignmentLocals(LinkedMethod linked) {
        Map<Integer, Integer> storeCounts = new HashMap<>();
        for (Instruction instruction : linked.instructions()) {
            Integer slot = astoreSlot(instruction);
            if (slot != null) {
                storeCounts.merge(slot, 1, Integer::sum);
            }
        }
        Set<Integer> result = new HashSet<>();
        for (Map.Entry<Integer, Integer> entry : storeCounts.entrySet()) {
            if (entry.getValue() == 1) {
                result.add(entry.getKey());
            }
        }
        return result;
    }    static Integer intLoadSlot(Instruction instruction) {
        return switch (instruction.opcode()) {
            case 21 -> instruction.operandA();
            case 26, 27, 28, 29 -> instruction.opcode() - 26;
            default -> null;
        };
    }    static void trackRecordLocalIfSingleAssignment(int slot, Value value, ValueTracking tracking,
                                                     Set<Integer> singleAssignmentLocals,
                                                     Map<Integer, RecordInstance> slotRecordInstance) {
        if (singleAssignmentLocals.contains(slot)) {
            RecordInstance instance = tracking.knownRecord(value);
            if (instance != null) {
                slotRecordInstance.put(slot, instance);
            }
        }
    }    static void trackStringLocalIfSingleAssignment(int slot, Value value, ValueTracking tracking,
                                                     Set<Integer> singleAssignmentLocals,
                                                     Map<Integer, String> slotStringInstance) {
        if (singleAssignmentLocals.contains(slot)) {
            String literal = tracking.knownString(value);
            if (literal != null) {
                slotStringInstance.put(slot, literal);
            }
        }
    }}
