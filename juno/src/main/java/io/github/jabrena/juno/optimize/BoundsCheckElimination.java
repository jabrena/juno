package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.ControlFlowGraph;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.optimize.IntRanges.Range;
import io.github.jabrena.juno.ir.Value;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Removes a {@link IrInstruction.BoundsCheck} whose index is proved to lie in {@code [0, length)}.
 *
 * <p>A forward interval analysis over the CFG tracks a signed range for every int local slot and, where branches
 * narrow them, for individual values. Ranges come from constants, {@code +}/{@code -}, masks, remainders and
 * narrowing conversions; an operation that could overflow gives the full {@code int} range, so wrapping arithmetic
 * never yields a false proof. A {@code Branch} on a {@code Compare} narrows both operands along each edge (the loop
 * test {@code i < 16} bounds {@code i} in the body), and a kept check narrows its index to {@code [0, length)}, so a
 * second check of the same index is redundant. Loop headers are widened after a few rounds so the analysis ends.
 * Calls cannot change a caller's local slots, so they need no special handling.
 */
public final class BoundsCheckElimination implements CompilerPass {
    private static final int ROUNDS_BEFORE_WIDENING = 3;

    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>(program.methods().size());
        for (IrMethod method : program.methods()) {
            methods.add(hasChecks(method) ? new Analysis(method).eliminate() : method);
        }
        return program.withMethods(methods);
    }

    private static boolean hasChecks(IrMethod method) {
        return method.blocks().stream().flatMap(block -> block.instructions().stream())
                .anyMatch(IrInstruction.BoundsCheck.class::isInstance);
    }

    /** Ranges at one program point: per local slot, plus per value where a branch or check narrowed it. */
    private record State(Map<Integer, Range> locals, Map<Value, Range> narrowed) {
        State copy() {
            return new State(new HashMap<>(locals), new HashMap<>(narrowed));
        }

        State meet(State other) {
            State result = new State(new HashMap<>(), new HashMap<>());
            locals.forEach((slot, range) -> {
                Range theirs = other.locals.get(slot);
                if (theirs != null) {
                    result.locals.put(slot, range.hull(theirs));
                }
            });
            narrowed.forEach((value, range) -> {
                Range theirs = other.narrowed.get(value);
                if (theirs != null) {
                    result.narrowed.put(value, range.hull(theirs));
                }
            });
            return result;
        }

        State widen(State next) {
            State result = new State(new HashMap<>(), new HashMap<>());
            next.locals.forEach((slot, range) -> {
                Range old = locals.get(slot);
                if (old != null) {
                    result.locals.put(slot, new Range(range.low() < old.low() ? Integer.MIN_VALUE : range.low(),
                            range.high() > old.high() ? Integer.MAX_VALUE : range.high()));
                }
            });
            return result;
        }
    }

    private static final class Analysis {
        private final IrMethod method;
        private final ControlFlowGraph graph;
        /** Each value's range where it is defined; values are defined once, so this holds wherever it is used. */
        private final Map<Value, Range> defined = new HashMap<>();
        /** The state on each edge, keyed by source block then target block. */
        private final Map<Integer, Map<Integer, State>> edges = new HashMap<>();

        Analysis(IrMethod method) {
            this.method = method;
            this.graph = ControlFlowGraph.of(method);
        }

        IrMethod eliminate() {
            Map<Integer, State> entries = solve();
            List<IrBasicBlock> blocks = new ArrayList<>(method.blocks().size());
            for (IrBasicBlock block : method.blocks()) {
                State entry = entries.get(block.start());
                blocks.add(entry == null ? block : rewrite(block, entry.copy()));
            }
            return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                    method.arrayDeclarations(), List.copyOf(blocks));
        }

        private Map<Integer, State> solve() {
            Map<Integer, State> entries = new HashMap<>();
            Map<Integer, Integer> rounds = new HashMap<>();
            if (graph.reversePostorder().isEmpty()) {
                return entries;
            }
            entries.put(graph.reversePostorder().getFirst(), new State(new HashMap<>(), new HashMap<>()));
            boolean changed = true;
            while (changed) {
                changed = false;
                for (int start : graph.reversePostorder()) {
                    State entry = start == graph.reversePostorder().getFirst() ? entries.get(start) : incoming(start);
                    if (entry == null) {
                        continue;
                    }
                    State previous = entries.get(start);
                    if (previous != null && isLoopHeader(start)
                            && rounds.merge(start, 1, Integer::sum) > ROUNDS_BEFORE_WIDENING) {
                        entry = previous.widen(entry);
                    }
                    if (!entry.equals(previous)) {
                        changed = true;
                    }
                    entries.put(start, entry);
                    transfer(graph.block(start), entry.copy());
                }
            }
            return entries;
        }

        private State incoming(int start) {
            State result = null;
            for (int predecessor : graph.predecessorsOf(start)) {
                State state = edges.getOrDefault(predecessor, Map.of()).get(start);
                if (state != null) {
                    result = result == null ? state.copy() : result.meet(state);
                }
            }
            return result;
        }

        private boolean isLoopHeader(int start) {
            return graph.predecessorsOf(start).stream().anyMatch(predecessor -> graph.dominates(start, predecessor));
        }

        /** Runs the block's instructions over {@code state} and records the state leaving along each edge. */
        private void transfer(IrBasicBlock block, State state) {
            Map<Value, Integer> loadedFrom = new HashMap<>();
            for (IrInstruction instruction : block.instructions()) {
                step(instruction, state, loadedFrom);
            }
            Map<Integer, State> out = new HashMap<>();
            if (block.terminator() instanceof IrTerminator.Branch branch) {
                IrInstruction.Compare compare = definingCompare(block, branch.condition());
                State whenTrue = state.copy();
                State whenFalse = state.copy();
                if (compare != null) {
                    whenTrue = refine(whenTrue, compare.condition(), compare, loadedFrom);
                    whenFalse = refine(whenFalse, IntRanges.inverse(compare.condition()), compare, loadedFrom);
                }
                put(out, branch.trueTarget(), whenTrue);
                put(out, branch.falseTarget(), whenFalse);
            } else {
                for (int target : ControlFlowGraph.successors(block.terminator())) {
                    put(out, target, state.copy());
                }
            }
            edges.put(block.start(), out);
        }

        private static void put(Map<Integer, State> out, int target, State state) {
            if (state == null) {
                return;
            }
            State existing = out.get(target);
            out.put(target, existing == null ? state : existing.meet(state));
        }

        private IrBasicBlock rewrite(IrBasicBlock block, State state) {
            Map<Value, Integer> loadedFrom = new HashMap<>();
            List<IrInstruction> kept = new ArrayList<>(block.instructions().size());
            for (IrInstruction instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.BoundsCheck check
                        && range(check.index(), state).within(0, check.length() - 1L)) {
                    continue;
                }
                kept.add(instruction);
                step(instruction, state, loadedFrom);
            }
            return new IrBasicBlock(block.start(), List.copyOf(kept), block.terminator());
        }

        private void step(IrInstruction instruction, State state, Map<Value, Integer> loadedFrom) {
            switch (instruction) {
                case IrInstruction.Const constant -> define(state, constant.target(), Range.constant(constant.value()));
                case IrInstruction.LoadLocal load -> {
                    if (load.target().type() == JunoType.INT32) {
                        define(state, load.target(), state.locals().getOrDefault(load.local(), Range.TOP));
                        loadedFrom.put(load.target(), load.local());
                    }
                }
                case IrInstruction.StoreLocal store -> {
                    if (store.value().type() == JunoType.INT32) {
                        state.locals().put(store.local(), range(store.value(), state));
                    } else {
                        state.locals().remove(store.local());
                    }
                    loadedFrom.values().removeIf(slot -> slot == store.local());
                    if (store.value().type() == JunoType.FLOAT64) {
                        // A double fills two consecutive slots.
                        state.locals().remove(store.local() + 1);
                        loadedFrom.values().removeIf(slot -> slot == store.local() + 1);
                    }
                }
                case IrInstruction.Binary binary -> define(state, binary.target(), IntRanges.binary(binary.operation(),
                        range(binary.left(), state), range(binary.right(), state)));
                case IrInstruction.Unary unary ->
                        define(state, unary.target(), IntRanges.unary(unary.operation(), range(unary.value(), state)));
                case IrInstruction.ArrayLoad load -> define(state, load.target(), IntRanges.element(load.elementType()));
                case IrInstruction.Compare compare -> define(state, compare.target(), new Range(0, 1));
                case IrInstruction.BoundsCheck check ->
                        narrow(state, check.index(), new Range(0, check.length() - 1L), loadedFrom);
                default -> {
                }
            }
        }

        /**
         * Records a value's range at its definition. Inside a loop the same value is defined again on every
         * iteration, so any narrowing that came around the backedge belongs to the previous one and is dropped.
         */
        private void define(State state, Value value, Range range) {
            defined.put(value, range);
            state.narrowed().remove(value);
        }

        private Range range(Value value, State state) {
            Range narrowed = state.narrowed().get(value);
            Range base = defined.getOrDefault(value, Range.TOP);
            return narrowed == null ? base : narrowed.intersect(base);
        }

        /** The {@code Compare} in this block that defines {@code condition}, or null. */
        private static IrInstruction.Compare definingCompare(IrBasicBlock block, Value condition) {
            for (IrInstruction instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.Compare compare && compare.target().equals(condition)) {
                    return compare;
                }
            }
            return null;
        }

        /** {@code state} on the edge where {@code left <condition> right} holds, or null when it never can. */
        private State refine(State state, Condition condition, IrInstruction.Compare compare,
                             Map<Value, Integer> loadedFrom) {
            IntRanges.Narrowed narrowed = IntRanges.narrow(condition, range(compare.left(), state),
                    range(compare.right(), state));
            if (!narrowed.feasible()) {
                return null;
            }
            narrow(state, compare.left(), narrowed.left(), loadedFrom);
            narrow(state, compare.right(), narrowed.right(), loadedFrom);
            return state;
        }

        /** Narrows a value, and the slot it was just loaded from when that slot still holds it. */
        private void narrow(State state, Value value, Range range, Map<Value, Integer> loadedFrom) {
            if (value.type() != JunoType.INT32) {
                return;
            }
            Range narrowed = range(value, state).intersect(range);
            state.narrowed().put(value, narrowed);
            Integer slot = loadedFrom.get(value);
            if (slot != null) {
                state.locals().put(slot, state.locals().getOrDefault(slot, Range.TOP).intersect(narrowed));
            }
        }
    }
}
