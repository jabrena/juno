package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrRewriting;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import io.github.jabrena.juno.linker.ScopedValueSupport;
import io.github.jabrena.juno.linker.StructuredTaskSupport;
import io.github.jabrena.juno.linker.ThreadSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Replaces a {@link IrInstruction.Call} to a small leaf method with the method's own blocks, so getters, setters,
 * constructors and small math helpers cost no call, no prologue and no argument shuffling.
 *
 * <p>A callee qualifies when it is tiny (at most {@link #MAX_COST} instructions besides the local loads, stores and
 * constants that copy propagation cleans up, and {@link #MAX_INSTRUCTIONS} in all, in at most {@link #MAX_BLOCKS}
 * blocks), calls nothing and allocates nothing (so it can neither recurse nor throw), and passes and returns only
 * one-slot values. Its locals move to a window above the caller's own; every inlined site in a method shares that
 * window, since a leaf body never overlaps another. Arguments are stored into the window's parameter slots and the
 * result comes back through one more slot, which copy propagation then removes.
 *
 * <p>Caller blocks are renumbered to their rank in start order times {@link #SPACING}, which makes room for the
 * inlined blocks between a caller block and its continuation. Order is preserved, so a jump is a loop backedge (where the backend polls {@code yield}) exactly when
 * it was one before. A method inlined everywhere and referenced nowhere else is dropped.
 */
public final class Inliner implements CompilerPass {
    /** The most real work a callee may do: its instructions other than local moves and constants. */
    static final int MAX_COST = 8;
    /** A cap on every instruction, moves included, so the inlined copy stays small before copy propagation runs. */
    static final int MAX_INSTRUCTIONS = 32;
    static final int MAX_BLOCKS = 8;
    static final int SPACING = 4096;
    private static final int MAX_ROUNDS = 4;
    /** Methods the runtime shim enters by symbol, which no IR call names. */
    private static final Set<MethodRef> RUNTIME_ENTRIES = Set.of(ThreadSupport.ENTRY_METHOD,
            StructuredTaskSupport.ENTRY_METHOD, ScopedValueSupport.ENTRY_METHOD);

    @Override
    public IrProgram apply(IrProgram program) {
        // A method that only called leaves is a leaf itself once they are inlined: repeat a few rounds.
        IrProgram current = program;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            IrProgram next = inlineOnce(current);
            if (next == current) {
                return current;
            }
            // Rejoin each inlined body with its surroundings, so the method's block count reflects its size again.
            current = new BlockMerging().apply(next);
        }
        return current;
    }

    private IrProgram inlineOnce(IrProgram program) {
        Map<MethodRef, IrMethod> inlinable = new LinkedHashMap<>();
        for (IrMethod method : program.methods()) {
            if (qualifies(method, program)) {
                inlinable.put(method.reference(), method);
            }
        }
        List<IrMethod> methods = new ArrayList<>(program.methods().size());
        boolean inlined = false;
        for (IrMethod method : program.methods()) {
            boolean calls = callsAny(method, inlinable);
            inlined |= calls;
            methods.add(calls ? new Site(method, inlinable).inline() : method);
        }
        if (!inlined) {
            return program;
        }
        Set<MethodRef> referenced = References.of(program.entryPoint(), methods);
        return program.withMethods(methods.stream()
                .filter(method -> !inlinable.containsKey(method.reference()) || referenced.contains(method.reference()))
                .toList());
    }

    private static boolean qualifies(IrMethod method, IrProgram program) {
        if (method.reference().equals(program.entryPoint()) || method.reference().name().equals("<clinit>")
                || RUNTIME_ENTRIES.contains(method.reference())
                || !method.arrayDeclarations().isEmpty() || method.blocks().size() > MAX_BLOCKS
                || !Descriptors.oneSlotOnly(method.reference().descriptor())) {
            return false;
        }
        int instructions = 0;
        int cost = 0;
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                if (!isLeafInstruction(instruction) || ++instructions > MAX_INSTRUCTIONS) {
                    return false;
                }
                if (!(instruction instanceof IrInstruction.LoadLocal || instruction instanceof IrInstruction.StoreLocal
                        || instruction instanceof IrInstruction.Const) && ++cost > MAX_COST) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isLeafInstruction(IrInstruction instruction) {
        return !(instruction instanceof IrInstruction.Call || instruction instanceof IrInstruction.LambdaCall
                || instruction instanceof IrInstruction.InterfaceCall || instruction instanceof IrInstruction.IntrinsicCall
                || instruction instanceof IrInstruction.NewObject || instruction instanceof IrInstruction.NewArray
                || instruction instanceof IrInstruction.NewMultiArray || instruction instanceof IrInstruction.LambdaCreate);
    }

    private static boolean callsAny(IrMethod method, Map<MethodRef, IrMethod> inlinable) {
        return method.blocks().stream().flatMap(block -> block.instructions().stream())
                .anyMatch(instruction -> instruction instanceof IrInstruction.Call call
                        && inlinable.containsKey(call.method()) && !call.method().equals(method.reference()));
    }

    /** Inlines every qualifying call in one caller. */
    private static final class Site {
        private final IrMethod caller;
        private final Map<MethodRef, IrMethod> inlinable;
        private final List<Value> values;
        private final int window;
        private int windowSize;
        private final List<IrBasicBlock> blocks = new ArrayList<>();
        /** Each caller block's rank in start order: scaling ranks, not raw starts, keeps repeated rounds small. */
        private final Map<Integer, Integer> ranks = new HashMap<>();

        Site(IrMethod caller, Map<MethodRef, IrMethod> inlinable) {
            this.caller = caller;
            this.inlinable = inlinable;
            this.values = new ArrayList<>(caller.values());
            this.window = caller.maxLocals();
            caller.blocks().stream().map(IrBasicBlock::start).sorted()
                    .forEach(start -> ranks.put(start, ranks.size()));
        }

        private int scaled(int start) {
            return Objects.requireNonNull(ranks.get(start)) * SPACING;
        }

        IrMethod inline() {
            for (IrBasicBlock block : caller.blocks()) {
                split(block);
            }
            return new IrMethod(caller.reference(), caller.isStatic(), window + windowSize, values,
                    caller.arrayDeclarations(), blocks);
        }

        private void split(IrBasicBlock block) {
            int start = scaled(block.start());
            int nextId = start + 1;
            int limit = start + SPACING - MAX_BLOCKS - 2;
            List<IrInstruction> segment = new ArrayList<>();
            for (IrInstruction instruction : block.instructions()) {
                IrMethod callee = instruction instanceof IrInstruction.Call call && nextId < limit
                        && !call.method().equals(caller.reference()) ? inlinable.get(call.method()) : null;
                if (callee == null) {
                    segment.add(instruction);
                    continue;
                }
                IrInstruction.Call call = (IrInstruction.Call) instruction;
                Map<Integer, Integer> calleeBlocks = new HashMap<>();
                for (IrBasicBlock calleeBlock : callee.blocks().stream()
                        .sorted((left, right) -> Integer.compare(left.start(), right.start())).toList()) {
                    calleeBlocks.put(calleeBlock.start(), nextId++);
                }
                int continuation = nextId++;
                for (int argument = 0; argument < call.arguments().size(); argument++) {
                    segment.add(new IrInstruction.StoreLocal(window + argument, call.arguments().get(argument)));
                }
                blocks.add(new IrBasicBlock(start, List.copyOf(segment),
                        new IrTerminator.Jump(Objects.requireNonNull(calleeBlocks.get(callee.blocks().getFirst().start())))));
                copyCallee(callee, calleeBlocks, continuation);
                segment = new ArrayList<>();
                if (call.target().isPresent()) {
                    segment.add(new IrInstruction.LoadLocal(call.target().get(), window + callee.maxLocals()));
                }
                start = continuation;
            }
            blocks.add(new IrBasicBlock(start, List.copyOf(segment),
                    IrRewriting.map(block.terminator(), value -> value, this::scaled)));
        }

        /** The callee's blocks, renumbered, with fresh values and locals in the window; returns jump onward. */
        private void copyCallee(IrMethod callee, Map<Integer, Integer> calleeBlocks, int continuation) {
            windowSize = Math.max(windowSize, callee.maxLocals() + 1);
            Map<Value, Value> renamed = new HashMap<>();
            int resultSlot = window + callee.maxLocals();
            for (IrBasicBlock block : callee.blocks()) {
                List<IrInstruction> instructions = new ArrayList<>();
                for (IrInstruction instruction : block.instructions()) {
                    instructions.add(IrRewriting.map(instruction, value -> rename(renamed, value),
                            local -> window + local));
                }
                IrTerminator terminator;
                if (block.terminator() instanceof IrTerminator.Return returned) {
                    returned.value().ifPresent(value ->
                            instructions.add(new IrInstruction.StoreLocal(resultSlot, rename(renamed, value))));
                    terminator = new IrTerminator.Jump(continuation);
                } else {
                    terminator = IrRewriting.map(block.terminator(), value -> rename(renamed, value),
                            calleeBlocks::get);
                }
                blocks.add(new IrBasicBlock(Objects.requireNonNull(calleeBlocks.get(block.start())), List.copyOf(instructions), terminator));
            }
        }

        private Value rename(Map<Value, Value> renamed, Value value) {
            return renamed.computeIfAbsent(value, original -> {
                Value fresh = new Value(values.size(), original.type());
                values.add(fresh);
                return fresh;
            });
        }
    }

    /** Parameter shapes the inliner handles: one JVM slot each, in and out. */
    static final class Descriptors {
        private Descriptors() {
        }

        static boolean oneSlotOnly(String descriptor) {
            return !containsWide(descriptor);
        }

        /** Whether a {@code long} or {@code double} appears as a parameter or the result, not inside a class name. */
        private static boolean containsWide(String descriptor) {
            int index = 0;
            while (index < descriptor.length()) {
                char type = descriptor.charAt(index);
                if (type == 'L') {
                    index = descriptor.indexOf(';', index) + 1;
                    continue;
                }
                if (type == 'J' || type == 'D') {
                    // An array of longs or doubles is a reference: one slot.
                    if (index == 0 || descriptor.charAt(index - 1) != '[') {
                        return true;
                    }
                }
                index++;
            }
            return false;
        }
    }

    /** Every method the program can still reach without going through an inlined call. */
    static final class References {
        private References() {
        }

        static Set<MethodRef> of(MethodRef entryPoint, List<IrMethod> methods) {
            Set<MethodRef> referenced = new HashSet<>(RUNTIME_ENTRIES);
            referenced.add(entryPoint);
            for (IrMethod method : methods) {
                if (method.reference().name().equals("<clinit>")) {
                    referenced.add(method.reference());
                }
                for (IrBasicBlock block : method.blocks()) {
                    for (IrInstruction instruction : block.instructions()) {
                        collect(instruction, referenced);
                    }
                }
            }
            return referenced;
        }

        private static void collect(IrInstruction instruction, Set<MethodRef> referenced) {
            switch (instruction) {
                case IrInstruction.Call call -> referenced.add(call.method());
                case IrInstruction.InterfaceCall call -> call.targets().forEach(target -> {
                    referenced.add(target.method());
                    if (target.lambda() != null) {
                        referenced.add(target.lambda().implementation().method());
                    }
                });
                case IrInstruction.LambdaCreate lambda -> referenced.add(lambda.site().implementation().method());
                case IrInstruction.LambdaCall call -> referenced.add(call.site().implementation().method());
                default -> {
                }
            }
        }
    }
}
