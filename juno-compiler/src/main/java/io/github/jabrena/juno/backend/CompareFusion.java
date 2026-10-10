package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.IrValues;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides which comparisons a branch can test directly. When a block ends in a {@code Compare} whose result only its
 * own {@code Branch} reads, {@link TerminatorLowering} sets the flags and branches on them, and the 0/1 result is
 * never materialized, stored or reloaded.
 */
final class CompareFusion {
    private final Map<Value, Integer> occurrences = new HashMap<>();

    CompareFusion(IrMethod method) {
        for (IrBasicBlock block : method.blocks()) {
            for (IrInstruction instruction : block.instructions()) {
                IrValues.of(instruction).forEach(value -> occurrences.merge(value, 1, Integer::sum));
            }
            IrValues.of(block.terminator()).forEach(value -> occurrences.merge(value, 1, Integer::sum));
        }
    }

    /**
     * The block's last instruction when it is a {@code Compare} that only the block's {@code Branch} reads (its
     * definition plus that one use), or null. Only the last instruction qualifies: nothing runs between the
     * comparison and the branch, so the operands' frame slots cannot have been reused in between.
     */
    IrInstruction.@Nullable Compare fusedCompare(IrBasicBlock block) {
        if (block.instructions().isEmpty()
                || !(block.instructions().getLast() instanceof IrInstruction.Compare compare)
                || !(block.terminator() instanceof IrTerminator.Branch branch)
                || !branch.condition().equals(compare.target())) {
            return null;
        }
        return occurrences.getOrDefault(compare.target(), 0) == 2 ? compare : null;
    }
}
