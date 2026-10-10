package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Removes stores to JVM/synthetic local slots that the optimized method never reads. */
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
        Set<Integer> readLocals = new HashSet<>();
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                if (instruction instanceof IrInstruction.LoadLocal load) {
                    readLocals.add(load.local());
                }
            }
        }

        List<IrBasicBlock> blocks = new ArrayList<>(method.blocks().size());
        for (IrBasicBlock block : method.blocks()) {
            List<IrInstruction> instructions = block.instructions().stream()
                    .filter(instruction -> !(instruction instanceof IrInstruction.StoreLocal store)
                            || readLocals.contains(store.local()))
                    .toList();
            blocks.add(new IrBasicBlock(block.start(), instructions, block.terminator()));
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks));
    }
}
