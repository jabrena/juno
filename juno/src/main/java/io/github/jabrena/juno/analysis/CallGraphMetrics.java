package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Call-graph analyses over the direct-call edges collected by {@link RuntimeRiskAnalyzer}. */
final class CallGraphMetrics {
    private CallGraphMetrics() {
    }

    static Set<MethodRef> recursiveMethods(Map<MethodRef, Set<MethodRef>> calls) {
        Set<MethodRef> recursive = new HashSet<>();
        for (MethodRef method : calls.keySet()) {
            for (MethodRef callee : calls.get(method)) {
                if (reaches(callee, method, calls, new HashSet<>())) {
                    recursive.add(method);
                    break;
                }
            }
        }
        return recursive;
    }

    static Set<MethodRef> transitiveAllocators(Map<MethodRef, Set<MethodRef>> calls,
                                                Map<MethodRef, Integer> allocations) {
        Set<MethodRef> allocators = new HashSet<>();
        for (MethodRef method : calls.keySet()) {
            if (allocations.getOrDefault(method, 0) > 0 || reachesAllocator(method, calls, allocations, new HashSet<>())) {
                allocators.add(method);
            }
        }
        return allocators;
    }

    /** Methods whose loop body calls a (possibly indirect) allocator, in addition to allocating directly. */
    static Set<MethodRef> loopAllocatorsCallingAllocator(IrProgram program, Set<MethodRef> transitiveAllocators) {
        Set<MethodRef> loopAllocators = new LinkedHashSet<>();
        for (IrMethod method : program.methods()) {
            Set<Integer> cyclicBlocks = IrCyclicBlocks.cyclicBlocks(method);
            for (IrBasicBlock block : method.blocks()) {
                if (!cyclicBlocks.contains(block.start())) {
                    continue;
                }
                boolean callsAllocator = block.instructions().stream()
                        .anyMatch(instruction -> switch (instruction) {
                            case IrInstruction.Call call -> transitiveAllocators.contains(call.method());
                            case IrInstruction.InterfaceCall call -> call.targets().stream()
                                    .anyMatch(target -> transitiveAllocators.contains(target.method()));
                            default -> false;
                        });
                if (callsAllocator) {
                    loopAllocators.add(method.reference());
                }
            }
        }
        return loopAllocators;
    }

    static int maximumStartupCallDepth(IrProgram program, Map<MethodRef, Set<MethodRef>> calls) {
        Map<MethodRef, Integer> memo = new HashMap<>();
        int maximum = longestCallDepth(program.entryPoint(), calls, memo);
        for (IrMethod method : program.methods()) {
            if (method.reference().name().equals("<clinit>")) {
                maximum = Math.max(maximum, longestCallDepth(method.reference(), calls, memo));
            }
        }
        return maximum;
    }

    static int maximumStartupStack(IrProgram program, Map<MethodRef, Set<MethodRef>> calls,
                                    Map<MethodRef, Integer> frames) {
        Map<MethodRef, Integer> memo = new HashMap<>();
        int maximum = longestStackPath(program.entryPoint(), calls, frames, memo);
        for (IrMethod method : program.methods()) {
            if (method.reference().name().equals("<clinit>")) {
                maximum = Math.max(maximum, longestStackPath(method.reference(), calls, frames, memo));
            }
        }
        return maximum;
    }

    /** Every method reachable from {@code start} through direct calls, {@code start} included. */
    static Set<MethodRef> reachableFrom(MethodRef start, Map<MethodRef, Set<MethodRef>> calls) {
        Set<MethodRef> reached = new LinkedHashSet<>();
        java.util.ArrayDeque<MethodRef> pending = new java.util.ArrayDeque<>(List.of(start));
        while (!pending.isEmpty()) {
            MethodRef method = pending.pop();
            if (reached.add(method)) {
                pending.addAll(calls.getOrDefault(method, Set.of()));
            }
        }
        return reached;
    }

    /** Largest summed frame size along any call path from {@code start}; the graph must be acyclic from there. */
    static int maximumStack(MethodRef start, Map<MethodRef, Set<MethodRef>> calls, Map<MethodRef, Integer> frames) {
        return longestStackPath(start, calls, frames, new HashMap<>());
    }

    static int startupAllocationEstimate(IrProgram program, Map<MethodRef, List<MethodRef>> callSites,
                                         Map<MethodRef, Integer> directAllocation) {
        int estimate = allocationPerInvocation(program.entryPoint(), callSites, directAllocation, new HashSet<>());
        for (IrMethod method : program.methods()) {
            if (method.reference().name().equals("<clinit>")) {
                estimate = Math.addExact(estimate,
                        allocationPerInvocation(method.reference(), callSites, directAllocation, new HashSet<>()));
            }
        }
        return estimate;
    }

    private static boolean reaches(MethodRef start, MethodRef target, Map<MethodRef, Set<MethodRef>> edges,
                                   Set<MethodRef> visited) {
        if (start.equals(target)) return true;
        if (!visited.add(start)) return false;
        for (MethodRef successor : edges.getOrDefault(start, Set.of())) {
            if (reaches(successor, target, edges, visited)) return true;
        }
        return false;
    }

    private static boolean reachesAllocator(MethodRef method, Map<MethodRef, Set<MethodRef>> calls,
                                            Map<MethodRef, Integer> allocations, Set<MethodRef> visited) {
        if (!visited.add(method)) return false;
        for (MethodRef callee : calls.getOrDefault(method, Set.of())) {
            if (allocations.getOrDefault(callee, 0) > 0
                    || reachesAllocator(callee, calls, allocations, visited)) return true;
        }
        return false;
    }

    private static int longestCallDepth(MethodRef method, Map<MethodRef, Set<MethodRef>> calls,
                                        Map<MethodRef, Integer> memo) {
        Integer cached = memo.get(method);
        if (cached != null) return cached;
        int depth = 1;
        for (MethodRef callee : calls.getOrDefault(method, Set.of())) {
            depth = Math.max(depth, 1 + longestCallDepth(callee, calls, memo));
        }
        memo.put(method, depth);
        return depth;
    }

    private static int longestStackPath(MethodRef method, Map<MethodRef, Set<MethodRef>> calls,
                                        Map<MethodRef, Integer> frames, Map<MethodRef, Integer> memo) {
        Integer cached = memo.get(method);
        if (cached != null) return cached;
        int child = 0;
        for (MethodRef callee : calls.getOrDefault(method, Set.of())) {
            child = Math.max(child, longestStackPath(callee, calls, frames, memo));
        }
        int bytes = frames.getOrDefault(method, 0) + child;
        memo.put(method, bytes);
        return bytes;
    }

    private static int allocationPerInvocation(MethodRef method, Map<MethodRef, List<MethodRef>> callSites,
                                               Map<MethodRef, Integer> directAllocation, Set<MethodRef> active) {
        if (!active.add(method)) return 0;
        int estimate = directAllocation.getOrDefault(method, 0);
        for (MethodRef callee : callSites.getOrDefault(method, List.of())) {
            estimate = Math.addExact(estimate,
                    allocationPerInvocation(callee, callSites, directAllocation, new HashSet<>(active)));
        }
        return estimate;
    }
}
