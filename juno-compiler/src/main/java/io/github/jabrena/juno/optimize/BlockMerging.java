package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.ir.ControlFlowGraph;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Joins a block that ends in a {@code Jump} with its target when it is that target's only way in, so straight-line
 * code (an inlined body and its continuation, say) is one block with no branch between the halves.
 *
 * <p>The backend polls {@code yield} on a jump to a block that does not start later than the jumping block (a loop
 * backedge). A merge must not change that: the jump being removed must go forward, and every jump leaving the merged
 * block must be a backedge from its new start exactly when it was one from the old.
 */
public final class BlockMerging implements CompilerPass {
    @Override
    public IrProgram apply(IrProgram program) {
        List<IrMethod> methods = new ArrayList<>(program.methods().size());
        for (IrMethod method : program.methods()) {
            methods.add(merge(method));
        }
        return program.withMethods(methods);
    }

    private IrMethod merge(IrMethod method) {
        if (method.blocks().size() < 2) {
            return method;
        }
        ControlFlowGraph graph = ControlFlowGraph.of(method);
        int entry = method.blocks().getFirst().start();
        Map<Integer, IrBasicBlock> blocks = new LinkedHashMap<>();
        method.blocks().forEach(block -> blocks.put(block.start(), block));
        boolean merged = false;
        for (IrBasicBlock original : method.blocks()) {
            IrBasicBlock block = blocks.get(original.start());
            while (block != null && block.terminator() instanceof IrTerminator.Jump jump
                    && canMerge(block, jump.target(), entry, graph, blocks)) {
                IrBasicBlock next = Objects.requireNonNull(blocks.remove(jump.target()));
                List<IrInstruction> instructions = new ArrayList<>(block.instructions());
                instructions.addAll(next.instructions());
                block = new IrBasicBlock(block.start(), List.copyOf(instructions), next.terminator());
                blocks.put(block.start(), block);
                merged = true;
            }
        }
        if (!merged) {
            return method;
        }
        return new IrMethod(method.reference(), method.isStatic(), method.maxLocals(), method.values(),
                method.arrayDeclarations(), List.copyOf(blocks.values()));
    }

    private static boolean canMerge(IrBasicBlock block, int target, int entry, ControlFlowGraph graph,
                                    Map<Integer, IrBasicBlock> blocks) {
        IrBasicBlock next = blocks.get(target);
        if (next == null || target == entry || target <= block.start()
                || graph.predecessorsOf(target).size() != 1) {
            return false;
        }
        for (int successor : ControlFlowGraph.successors(next.terminator())) {
            if ((successor <= block.start()) != (successor <= next.start())) {
                return false;
            }
        }
        return true;
    }
}
