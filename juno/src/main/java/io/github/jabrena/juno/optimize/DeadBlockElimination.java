package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes basic blocks unreachable from a method's entry block. On its own the lowered IR has no dead
 * blocks, but {@link ConstantFolder} can turn a {@code Branch} into an unconditional {@code Jump}, at which
 * point the branch's other target may no longer be reachable by anything.
 */
public final class DeadBlockElimination implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>();
        for (IrMethod method : program.methods()) {
            methods.add(pruneMethod(method));
        }
        return new IrProgram(program.entryPoint(), List.copyOf(methods), program.watchdogTimeoutMillis());
    }

    private IrMethod pruneMethod(IrMethod method) {
        if (method.blocks().isEmpty()) {
            return method;
        }
        Map<Integer, IrBasicBlock> byStart = new HashMap<>();
        for (IrBasicBlock block : method.blocks()) {
            byStart.put(block.start(), block);
        }

        Set<Integer> reachable = new LinkedHashSet<>();
        Deque<Integer> work = new ArrayDeque<>();
        int entry = method.blocks().get(0).start();
        reachable.add(entry);
        work.add(entry);
        while (!work.isEmpty()) {
            IrBasicBlock block = byStart.get(work.removeFirst());
            for (int successor : successorsOf(block.terminator())) {
                if (reachable.add(successor)) {
                    work.add(successor);
                }
            }
        }

        List<IrBasicBlock> kept = new ArrayList<>();
        for (IrBasicBlock block : method.blocks()) {
            if (reachable.contains(block.start())) {
                kept.add(block);
            }
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(kept));
    }

    private List<Integer> successorsOf(IrTerminator terminator) {
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
}
