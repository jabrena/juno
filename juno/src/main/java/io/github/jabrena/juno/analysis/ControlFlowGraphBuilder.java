package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Partitions a method's decoded instructions into {@link BasicBlock}s and resolves every branch target into
 * a CFG edge, replacing byte-offset jumps with an explicit block graph.
 */
public final class ControlFlowGraphBuilder {
    public ControlFlowGraph build(String methodDisplayName, List<Instruction> instructions) {
        TreeMap<Integer, Instruction> byOffset = new TreeMap<>();
        for (Instruction instruction : instructions) {
            byOffset.put(instruction.offset(), instruction);
        }

        TreeSet<Integer> leaders = new TreeSet<>();
        leaders.add(instructions.get(0).offset());
        for (Instruction instruction : instructions) {
            if (isBranch(instruction.opcode())) {
                int target = instruction.offset() + instruction.operandA();
                if (!byOffset.containsKey(target)) {
                    throw new CompileException(methodDisplayName + " at bytecode offset "
                            + instruction.offset() + ": invalid branch target " + target);
                }
                leaders.add(target);
            }
            if (isBlockEnd(instruction.opcode())) {
                Integer next = byOffset.higherKey(instruction.offset());
                if (next != null) {
                    leaders.add(next);
                }
            }
        }

        List<BasicBlock> blocks = new ArrayList<>();
        List<Integer> leaderOffsets = List.copyOf(leaders);
        for (int index = 0; index < leaderOffsets.size(); index++) {
            int start = leaderOffsets.get(index);
            int endExclusive = index + 1 < leaderOffsets.size() ? leaderOffsets.get(index + 1) : Integer.MAX_VALUE;
            List<Instruction> blockInstructions = List.copyOf(byOffset.subMap(start, endExclusive).values());
            Instruction last = blockInstructions.get(blockInstructions.size() - 1);
            blocks.add(new BasicBlock(start, blockInstructions, terminatorOf(methodDisplayName, last, byOffset)));
        }
        return new ControlFlowGraph(List.copyOf(blocks));
    }

    private Terminator terminatorOf(String methodDisplayName, Instruction last, TreeMap<Integer, Instruction> byOffset) {
        int opcode = last.opcode();
        if (opcode == 167) {
            return new Terminator.Jump(last.offset() + last.operandA());
        }
        if (isConditionalBranch(opcode)) {
            Integer falseTarget = byOffset.higherKey(last.offset());
            if (falseTarget == null) {
                throw new CompileException(methodDisplayName + " at bytecode offset " + last.offset()
                        + ": conditional branch must be followed by another instruction");
            }
            return new Terminator.Branch(last.offset() + last.operandA(), falseTarget);
        }
        if (opcode == 172 || opcode == 177) {
            return new Terminator.Return();
        }
        Integer next = byOffset.higherKey(last.offset());
        return next != null ? new Terminator.Fallthrough(next) : new Terminator.Return();
    }

    private boolean isBranch(int opcode) {
        return opcode == 167 || isConditionalBranch(opcode);
    }

    private boolean isConditionalBranch(int opcode) {
        return (opcode >= 153 && opcode <= 164) || opcode == 165 || opcode == 166;
    }

    private boolean isBlockEnd(int opcode) {
        return isBranch(opcode) || opcode == 172 || opcode == 177;
    }
}
