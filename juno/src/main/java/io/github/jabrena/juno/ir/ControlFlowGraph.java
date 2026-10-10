package io.github.jabrena.juno.ir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The control-flow graph of one {@link IrMethod}, shared by the data-flow passes: every block's successors and
 * predecessors (by block start), a reverse postorder from the entry block, and the dominator tree. Blocks are
 * identified by {@link IrBasicBlock#start()}; the entry is the method's first block.
 */
public final class ControlFlowGraph {
    private final List<IrBasicBlock> blocks;
    private final Map<Integer, IrBasicBlock> blocksByStart;
    private final Map<Integer, List<Integer>> successors;
    private final Map<Integer, Set<Integer>> predecessors;
    private final List<Integer> reversePostorder;
    private final Map<Integer, Integer> immediateDominators;

    private ControlFlowGraph(IrMethod method) {
        blocks = method.blocks();
        blocksByStart = new LinkedHashMap<>();
        successors = new LinkedHashMap<>();
        predecessors = new LinkedHashMap<>();
        for (IrBasicBlock block : blocks) {
            blocksByStart.put(block.start(), block);
            predecessors.put(block.start(), new LinkedHashSet<>());
        }
        for (IrBasicBlock block : blocks) {
            List<Integer> targets = successors(block.terminator()).stream()
                    .filter(blocksByStart::containsKey).distinct().toList();
            successors.put(block.start(), targets);
            for (int target : targets) {
                predecessors.get(target).add(block.start());
            }
        }
        reversePostorder = blocks.isEmpty() ? List.of() : computeReversePostorder(blocks.getFirst().start());
        immediateDominators = computeDominators();
    }

    public static ControlFlowGraph of(IrMethod method) {
        return new ControlFlowGraph(method);
    }

    /** Where control may go after {@code terminator}, in the order its targets are listed. */
    public static List<Integer> successors(IrTerminator terminator) {
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

    public List<IrBasicBlock> blocks() {
        return blocks;
    }

    public IrBasicBlock block(int start) {
        return blocksByStart.get(start);
    }

    public List<Integer> successorsOf(int start) {
        return successors.getOrDefault(start, List.of());
    }

    public Set<Integer> predecessorsOf(int start) {
        return predecessors.getOrDefault(start, Set.of());
    }

    /** Blocks reachable from the entry, each before its successors except along backedges. */
    public List<Integer> reversePostorder() {
        return reversePostorder;
    }

    public boolean isReachable(int start) {
        return immediateDominators.containsKey(start);
    }

    /** Whether every path from the entry to {@code block} passes through {@code dominator} (a block dominates itself). */
    public boolean dominates(int dominator, int block) {
        if (!isReachable(block)) {
            return false;
        }
        Integer current = block;
        while (current != null) {
            if (current == dominator) {
                return true;
            }
            Integer parent = immediateDominators.get(current);
            current = parent == null || parent.equals(current) ? null : parent;
        }
        return false;
    }

    private List<Integer> computeReversePostorder(int entry) {
        List<Integer> postorder = new ArrayList<>();
        Set<Integer> visited = new LinkedHashSet<>();
        // Iterative DFS: methods can be long enough to overflow a recursive walk.
        List<int[]> stack = new ArrayList<>();
        visited.add(entry);
        stack.add(new int[]{entry, 0});
        while (!stack.isEmpty()) {
            int[] frame = stack.getLast();
            List<Integer> next = successorsOf(frame[0]);
            if (frame[1] < next.size()) {
                int successor = next.get(frame[1]++);
                if (visited.add(successor)) {
                    stack.add(new int[]{successor, 0});
                }
            } else {
                postorder.add(frame[0]);
                stack.removeLast();
            }
        }
        return List.copyOf(postorder.reversed());
    }

    /** Cooper, Harvey and Kennedy's iterative dominator algorithm over the reverse postorder. */
    private Map<Integer, Integer> computeDominators() {
        Map<Integer, Integer> order = new HashMap<>();
        for (int index = 0; index < reversePostorder.size(); index++) {
            order.put(reversePostorder.get(index), index);
        }
        Map<Integer, Integer> dominators = new HashMap<>();
        if (reversePostorder.isEmpty()) {
            return dominators;
        }
        int entry = reversePostorder.getFirst();
        dominators.put(entry, entry);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int block : reversePostorder.subList(1, reversePostorder.size())) {
                Integer chosen = null;
                for (int predecessor : predecessorsOf(block)) {
                    if (!dominators.containsKey(predecessor)) {
                        continue;
                    }
                    chosen = chosen == null ? predecessor : intersect(predecessor, chosen, dominators, order);
                }
                if (chosen != null && !chosen.equals(dominators.get(block))) {
                    dominators.put(block, chosen);
                    changed = true;
                }
            }
        }
        return Map.copyOf(dominators);
    }

    private static int intersect(int first, int second, Map<Integer, Integer> dominators, Map<Integer, Integer> order) {
        int left = first;
        int right = second;
        while (left != right) {
            while (order.get(left) > order.get(right)) {
                left = dominators.get(left);
            }
            while (order.get(right) > order.get(left)) {
                right = dominators.get(right);
            }
        }
        return left;
    }
}
