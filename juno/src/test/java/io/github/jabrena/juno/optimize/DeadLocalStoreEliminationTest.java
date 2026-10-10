package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.MethodRef;
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

class DeadLocalStoreEliminationTest {
    private static final MethodRef METHOD = new MethodRef("demo/Program", "work", "()I");
    private final DeadLocalStoreElimination elimination = new DeadLocalStoreElimination();

    @Test
    void removesStoresToLocalsWithNoRemainingReads() {
        Value value = Value.int32(0);
        IrMethod method = new IrMethod(METHOD, 2, List.of(value), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(value, 7),
                        new IrInstruction.StoreLocal(1, value)),
                        new IrTerminator.Return(Optional.of(value)))));

        IrMethod optimized = apply(method);

        assertThat(optimized.blocks().getFirst().instructions())
                .containsExactly(new IrInstruction.Const(value, 7));
    }

    @Test
    void preservesAStoreReadFromAnotherBasicBlock() {
        Value stored = Value.int32(0);
        Value loaded = Value.int32(1);
        IrMethod method = new IrMethod(METHOD, 1, List.of(stored, loaded), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(stored, 7),
                        new IrInstruction.StoreLocal(0, stored)),
                        new IrTerminator.Jump(10)),
                new IrBasicBlock(10, List.of(new IrInstruction.LoadLocal(loaded, 0)),
                        new IrTerminator.Return(Optional.of(loaded)))));

        IrMethod optimized = apply(method);

        assertThat(optimized.blocks().getFirst().instructions())
                .contains(new IrInstruction.StoreLocal(0, stored));
    }

    private IrMethod apply(IrMethod method) {
        return elimination.apply(new IrProgram(METHOD, List.of(method))).methods().getFirst();
    }
}
