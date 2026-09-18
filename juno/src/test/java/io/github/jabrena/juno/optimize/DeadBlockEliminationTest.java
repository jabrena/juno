package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadBlockEliminationTest {
    private final DeadBlockElimination elimination = new DeadBlockElimination();
    private final MethodRef method = new MethodRef("demo/Program", "main", "()V");

    @Test
    void removesABlockThatOnlyAnUnfoldedBranchUsedToReach() {
        // Block 0 used to conditionally branch to either 10 or 20; ConstantFolder already turned
        // it into an unconditional jump to 10, so block 20 is now unreachable and dead.
        IrBasicBlock header = new IrBasicBlock(0, List.of(), new IrTerminator.Jump(10));
        IrBasicBlock live = new IrBasicBlock(10, List.of(), new IrTerminator.Return(Optional.empty()));
        IrBasicBlock dead = new IrBasicBlock(20, List.of(), new IrTerminator.Return(Optional.empty()));
        IrMethod irMethod = new IrMethod(method, 1, 0, List.of(), List.of(header, live, dead));
        IrProgram program = new IrProgram(method, List.of(irMethod));

        IrMethod pruned = elimination.apply(program).methods().get(0);

        assertEquals(2, pruned.blocks().size());
        assertTrue(pruned.blocks().stream().anyMatch(block -> block.start() == 0));
        assertTrue(pruned.blocks().stream().anyMatch(block -> block.start() == 10));
        assertTrue(pruned.blocks().stream().noneMatch(block -> block.start() == 20));
    }

    @Test
    void keepsBothTargetsOfARealBranch() {
        IrBasicBlock header = new IrBasicBlock(0, List.of(),
                new IrTerminator.Branch(new io.github.jabrena.juno.ir.Value(0), 10, 20));
        IrBasicBlock trueBlock = new IrBasicBlock(10, List.of(), new IrTerminator.Return(Optional.empty()));
        IrBasicBlock falseBlock = new IrBasicBlock(20, List.of(), new IrTerminator.Return(Optional.empty()));
        IrMethod irMethod = new IrMethod(method, 1, 1, List.of(), List.of(header, trueBlock, falseBlock));
        IrProgram program = new IrProgram(method, List.of(irMethod));

        IrMethod pruned = elimination.apply(program).methods().get(0);

        assertEquals(3, pruned.blocks().size());
    }

    @Test
    void aLoneUnreachableBlockAfterTheEntryIsDropped() {
        IrBasicBlock header = new IrBasicBlock(0, List.of(), new IrTerminator.Return(Optional.empty()));
        IrBasicBlock orphan = new IrBasicBlock(5, List.of(), new IrTerminator.Return(Optional.empty()));
        IrMethod irMethod = new IrMethod(method, 1, 0, List.of(), List.of(header, orphan));
        IrProgram program = new IrProgram(method, List.of(irMethod));

        IrMethod pruned = elimination.apply(program).methods().get(0);

        assertEquals(1, pruned.blocks().size());
        assertEquals(0, pruned.blocks().get(0).start());
    }
}
