package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BlockMergingAndDeadValueTest {
    private static final MethodRef METHOD = new MethodRef("demo/Program", "work", "()I");

    @Test
    void mergesAForwardJumpIntoItsOnlyPredecessor() {
        IrMethod merged = merge(
                new IrBasicBlock(0, List.of(new IrInstruction.Const(Value.int32(0), 1)), new IrTerminator.Jump(10)),
                new IrBasicBlock(10, List.of(new IrInstruction.Const(Value.int32(1), 2)),
                        new IrTerminator.Return(Optional.of(Value.int32(1)))));

        assertThat(merged.blocks()).hasSize(1);
        assertThat(merged.blocks().getFirst().instructions()).hasSize(2);
    }

    @Test
    void keepsAJoinWithTwoPredecessorsApart() {
        IrMethod merged = merge(
                new IrBasicBlock(0, List.of(), new IrTerminator.Branch(Value.int32(0), 10, 20)),
                new IrBasicBlock(10, List.of(), new IrTerminator.Jump(30)),
                new IrBasicBlock(20, List.of(), new IrTerminator.Jump(30)),
                new IrBasicBlock(30, List.of(), new IrTerminator.Return(Optional.empty())));

        assertThat(merged.blocks()).extracting(IrBasicBlock::start).containsExactly(0, 10, 20, 30);
    }

    @Test
    void neverTurnsALoopBackedgeIntoAForwardJump() {
        // 0 -> 20 -> 10 (a backedge from 20) -> 30 ... merging 20 into 0 would make 0 -> 10 look forward.
        IrMethod merged = merge(
                new IrBasicBlock(0, List.of(), new IrTerminator.Jump(20)),
                new IrBasicBlock(10, List.of(), new IrTerminator.Branch(Value.int32(0), 20, 30)),
                new IrBasicBlock(20, List.of(), new IrTerminator.Jump(10)),
                new IrBasicBlock(30, List.of(), new IrTerminator.Return(Optional.empty())));

        assertThat(merged.blocks()).extracting(IrBasicBlock::start).contains(20);
    }

    @Test
    void dropsAnUnusedComputationAndThenTheConstantsItRead() {
        IrMethod method = new IrMethod(METHOD, 0, Value.int32Values(4), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(Value.int32(0), 6),
                        new IrInstruction.Const(Value.int32(1), 7),
                        new IrInstruction.Binary(Value.int32(2), BinaryOp.MULTIPLY, Value.int32(0), Value.int32(1)),
                        new IrInstruction.Const(Value.int32(3), 1)),
                        new IrTerminator.Return(Optional.of(Value.int32(3))))));

        IrMethod optimized = new DeadValueElimination().apply(new IrProgram(METHOD, List.of(method))).methods()
                .getFirst();

        assertThat(optimized.blocks().getFirst().instructions())
                .containsExactly(new IrInstruction.Const(Value.int32(3), 1));
    }

    @Test
    void keepsAnUnusedDivisionBecauseItCanPanic() {
        IrMethod method = new IrMethod(METHOD, 1, Value.int32Values(3), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.LoadLocal(Value.int32(0), 0),
                        new IrInstruction.Const(Value.int32(1), 0),
                        new IrInstruction.Binary(Value.int32(2), BinaryOp.DIVIDE, Value.int32(0), Value.int32(1))),
                        new IrTerminator.Return(Optional.empty()))));

        IrMethod optimized = new DeadValueElimination().apply(new IrProgram(METHOD, List.of(method))).methods()
                .getFirst();

        assertThat(optimized.blocks().getFirst().instructions()).hasSize(3);
    }

    private static IrMethod merge(IrBasicBlock... blocks) {
        IrMethod method = new IrMethod(METHOD, 0, Value.int32Values(2), List.of(), List.of(blocks));
        return new BlockMerging().apply(new IrProgram(METHOD, List.of(method))).methods().getFirst();
    }
}
