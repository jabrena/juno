package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detects which of an {@link IrMethod}'s basic blocks are reachable from a back-edge (i.e. sit in a loop). */
final class IrCyclicBlocks {
    private IrCyclicBlocks() {
    }

    static Set<Integer> cyclicBlocks(IrMethod method) {
        Map<Integer, List<Integer>> edges = new HashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            // A panic never returns; the self-jump after it only terminates the block.
            edges.put(block.start(), endsInNoReturnCall(block) ? List.of() : successors(block.terminator()));
        }
        Set<Integer> cyclic = new HashSet<>();
        for (int block : edges.keySet()) {
            for (int successor : edges.get(block)) {
                if (reaches(successor, block, edges, new HashSet<>())) {
                    cyclic.add(block);
                    break;
                }
            }
        }
        return cyclic;
    }

    private static boolean endsInNoReturnCall(IrBasicBlock block) {
        List<IrInstruction> instructions = block.instructions();
        if (instructions.isEmpty()) {
            return false;
        }
        IrInstruction last = instructions.get(instructions.size() - 1);
        return last instanceof IrInstruction.Panic;
    }

    private static List<Integer> successors(IrTerminator terminator) {
        return switch (terminator) {
            case IrTerminator.Jump jump -> List.of(jump.target());
            case IrTerminator.Branch branch -> List.of(branch.trueTarget(), branch.falseTarget());
            case IrTerminator.Return ignored -> List.of();
            case IrTerminator.Switch switched -> {
                List<Integer> targets = new ArrayList<>(switched.targets());
                targets.add(switched.defaultTarget());
                yield List.copyOf(targets);
            }
        };
    }

    private static boolean reaches(int start, int target, Map<Integer, List<Integer>> edges, Set<Integer> visited) {
        if (start == target) return true;
        if (!visited.add(start)) return false;
        for (int successor : edges.getOrDefault(start, List.of())) {
            if (reaches(successor, target, edges, visited)) return true;
        }
        return false;
    }
}
