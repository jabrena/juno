package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.ControlFlowGraph;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.JunoType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes stores to JVM/synthetic local slots that no later load can read. A backward liveness analysis over the CFG
 * finds, after every store, whether some path still reads the slot before overwriting it; a store nothing reads is
 * dropped. A {@code double} occupies two consecutive slots, so its loads and stores touch both. Exceptional control
 * flow is explicit in the IR (a throwing call ends its block and branches to the handler), so a handler's reads are
 * ordinary successor reads.
 */
public final class DeadLocalStoreElimination implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>(program.methods().size());
        for (IrMethod method : program.methods()) {
            methods.add(eliminate(method));
        }
        return program.withMethods(methods);
    }

    private IrMethod eliminate(IrMethod method) {
        ControlFlowGraph graph = ControlFlowGraph.of(method);
        Map<Integer, Set<Integer>> liveIn = new HashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrBasicBlock block : method.blocks().reversed()) {
                Set<Integer> live = liveOut(block, graph, liveIn);
                for (IrInstruction instruction : block.instructions().reversed()) {
                    transfer(instruction, live);
                }
                if (!live.equals(liveIn.put(block.start(), live))) {
                    changed = true;
                }
            }
        }

        List<IrBasicBlock> blocks = new ArrayList<>(method.blocks().size());
        for (IrBasicBlock block : method.blocks()) {
            Set<Integer> live = liveOut(block, graph, liveIn);
            List<IrInstruction> kept = new ArrayList<>(block.instructions().size());
            for (IrInstruction instruction : block.instructions().reversed()) {
                if (instruction instanceof IrInstruction.StoreLocal store && !isRead(store, live)) {
                    continue;
                }
                transfer(instruction, live);
                kept.add(instruction);
            }
            blocks.add(new IrBasicBlock(block.start(), List.copyOf(kept.reversed()), block.terminator()));
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks));
    }

    private static Set<Integer> liveOut(IrBasicBlock block, ControlFlowGraph graph, Map<Integer, Set<Integer>> liveIn) {
        Set<Integer> live = new HashSet<>();
        for (int successor : graph.successorsOf(block.start())) {
            live.addAll(liveIn.getOrDefault(successor, Set.of()));
        }
        return live;
    }

    /** Steps {@code live} backward over one instruction: a store kills its slots, a load revives them. */
    private static void transfer(IrInstruction instruction, Set<Integer> live) {
        if (instruction instanceof IrInstruction.StoreLocal store) {
            live.removeAll(slots(store.local(), store.value().type()));
        } else if (instruction instanceof IrInstruction.LoadLocal load) {
            live.addAll(slots(load.local(), load.target().type()));
        }
    }

    private static boolean isRead(IrInstruction.StoreLocal store, Set<Integer> live) {
        return slots(store.local(), store.value().type()).stream().anyMatch(live::contains);
    }

    private static List<Integer> slots(int local, JunoType type) {
        return type == JunoType.FLOAT64 ? List.of(local, local + 1) : List.of(local);
    }
}
