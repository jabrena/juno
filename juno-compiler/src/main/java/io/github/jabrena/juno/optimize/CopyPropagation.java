package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.ControlFlowGraph;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrRewriting;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.IrValues;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Eliminates a {@link IrInstruction.LoadLocal} when preceding stores determine the slot's value. Uses of the
 * load's target are rewritten to the stored value.
 *
 * <p>A forward CFG data-flow analysis intersects predecessor states: knowledge crosses a block boundary only
 * when every predecessor carries the exact same typed IR value in that local slot. When the predecessors carry
 * different values that are all the same int constant (two branches that each store {@code 0}), the load becomes a
 * {@code Const} of that number: no single one of those values can stand in for it, but the number can, and the load
 * keeps its value defined in its own block. Conflicting branch values become unknown. The transformation repeats until no load can be removed, allowing a propagated load/store
 * chain to expose another fact in the next iteration. Field loads are never treated as memory facts: every
 * {@code volatile} field read remains an actual read across calls and loop backedges.
 */
public final class CopyPropagation implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        IrProgram current = program;
        while (true) {
            IrProgram propagated = propagateOnce(current);
            if (propagated.equals(current)) {
                return propagated;
            }
            current = propagated;
        }
    }

    private IrProgram propagateOnce(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>();
        for (IrMethod method : program.methods()) {
            methods.add(propagateMethod(method));
        }
        return program.withMethods(methods);
    }

    private IrMethod propagateMethod(IrMethod method) {
        ControlFlowGraph graph = ControlFlowGraph.of(method);
        Map<Value, Integer> constants = constants(method);
        Set<Value> crossBlockValues = crossBlockValues(method);
        Map<Integer, Facts> outgoing = new LinkedHashMap<>();
        boolean changed;
        do {
            changed = false;
            for (int index = 0; index < method.blocks().size(); index++) {
                IrBasicBlock block = method.blocks().get(index);
                Facts incoming = index == 0 ? Facts.NONE
                        : meet(graph.predecessorsOf(block.start()), outgoing, constants);
                Facts transferred = propagateBlock(block, incoming, crossBlockValues).outgoing();
                if (!transferred.equals(outgoing.put(block.start(), transferred))) {
                    changed = true;
                }
            }
        } while (changed);

        List<IrBasicBlock> blocks = new ArrayList<>();
        for (int index = 0; index < method.blocks().size(); index++) {
            IrBasicBlock block = method.blocks().get(index);
            Facts incoming = index == 0 ? Facts.NONE
                    : meet(graph.predecessorsOf(block.start()), outgoing, constants);
            blocks.add(propagateBlock(block, incoming, crossBlockValues).block());
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks));
    }

    private Set<Value> crossBlockValues(IrMethod method) {
        Map<Value, Integer> blocksSeen = new HashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            Set<Value> values = new LinkedHashSet<>();
            block.instructions().forEach(instruction -> values.addAll(IrValues.of(instruction)));
            values.addAll(IrValues.of(block.terminator()));
            values.forEach(value -> blocksSeen.merge(value, 1, Integer::sum));
        }
        Set<Value> result = new LinkedHashSet<>();
        blocksSeen.forEach((value, count) -> {
            if (count > 1) {
                result.add(value);
            }
        });
        return Set.copyOf(result);
    }

    /** Every value a {@code Const} defines, with its number. */
    private static Map<Value, Integer> constants(IrMethod method) {
        Map<Value, Integer> constants = new HashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.Const constant) {
                    constants.put(constant.target(), constant.value());
                }
            }
        }
        return constants;
    }

    /**
     * What is known about the local slots at a block boundary: the slots every path left holding the same IR value,
     * and the int slots every path left holding the same constant through different {@code Const} values.
     */
    private record Facts(Map<Integer, Value> values, Map<Integer, Integer> constants) {
        static final Facts NONE = new Facts(Map.of(), Map.of());
    }

    private Facts meet(Set<Integer> predecessors, Map<Integer, Facts> outgoing, Map<Value, Integer> constants) {
        List<Facts> states = new ArrayList<>();
        for (int predecessor : predecessors) {
            Facts state = outgoing.get(predecessor);
            if (state != null) {
                states.add(state);
            }
        }
        if (states.isEmpty()) {
            return Facts.NONE;
        }
        Map<Integer, Value> values = new HashMap<>(states.getFirst().values());
        Set<Integer> slots = new LinkedHashSet<>(values.keySet());
        slots.addAll(states.getFirst().constants().keySet());
        Map<Integer, Integer> agreed = new HashMap<>();
        for (Facts state : states.subList(1, states.size())) {
            values.entrySet().removeIf(entry -> !entry.getValue().equals(state.values().get(entry.getKey())));
        }
        for (int slot : slots) {
            Integer number = null;
            boolean same = true;
            for (Facts state : states) {
                Integer here = numberIn(state, slot, constants);
                same = same && here != null && (number == null || number.equals(here));
                number = here;
            }
            if (same && !values.containsKey(slot)) {
                agreed.put(slot, number);
            }
        }
        return new Facts(Map.copyOf(values), Map.copyOf(agreed));
    }

    /** The int constant {@code slot} holds in {@code state}, or null. */
    private static Integer numberIn(Facts state, int slot, Map<Value, Integer> constants) {
        Integer agreed = state.constants().get(slot);
        if (agreed != null) {
            return agreed;
        }
        Value value = state.values().get(slot);
        return value != null && value.type() == JunoType.INT32 ? constants.get(value) : null;
    }

    /**
     * The block with its determined loads removed, and the slot facts at its end. Those facts must come from the
     * rewritten stores: a store of a load this block removes now stores that load's replacement, and a successor
     * rewritten to the removed load's target would read a value nothing defines any more.
     */
    private Propagated propagateBlock(IrBasicBlock block, Facts incoming, Set<Value> crossBlockValues) {
        Map<Integer, Value> storedValues = new HashMap<>(incoming.values());
        Map<Integer, Integer> agreedConstants = new HashMap<>(incoming.constants());
        Map<Value, Value> replacements = new HashMap<>();
        List<IrInstruction> instructions = new ArrayList<>();

        for (IrInstruction instruction : block.instructions()) {
            if (instruction instanceof IrInstruction.LoadLocal load) {
                Value stored = storedValues.get(load.local());
                Integer agreed = agreedConstants.get(load.local());
                if (agreed != null && load.target().type() == JunoType.INT32) {
                    // Every path stored this number, but through different values: none of them can stand in here.
                    instructions.add(new IrInstruction.Const(load.target(), agreed));
                    continue;
                }
                if (stored != null && stored.type() == load.target().type()
                        && !crossBlockValues.contains(load.target())) {
                    replacements.put(load.target(), resolve(replacements, stored));
                    continue;
                }
                instructions.add(load);
                continue;
            }

            IrInstruction rewritten = rewriteInstruction(instruction, replacements);
            instructions.add(rewritten);
            if (rewritten instanceof IrInstruction.StoreLocal store) {
                storedValues.put(store.local(), store.value());
                agreedConstants.remove(store.local());
            }
        }

        return new Propagated(new IrBasicBlock(block.start(), List.copyOf(instructions),
                rewriteTerminator(block.terminator(), replacements)),
                new Facts(Map.copyOf(storedValues), Map.copyOf(agreedConstants)));
    }

    private record Propagated(IrBasicBlock block, Facts outgoing) {
    }

    private IrInstruction rewriteInstruction(IrInstruction instruction, Map<Value, Value> replacements) {
        return IrRewriting.map(instruction, value -> resolve(replacements, value), IntUnaryOperator.identity());
    }

    private IrTerminator rewriteTerminator(IrTerminator terminator, Map<Value, Value> replacements) {
        return IrRewriting.map(terminator, value -> resolve(replacements, value), IntUnaryOperator.identity());
    }

    private Value resolve(Map<Value, Value> replacements, Value value) {
        Value current = value;
        Value replacement;
        while ((replacement = replacements.get(current)) != null) {
            current = replacement;
        }
        return current;
    }
}
