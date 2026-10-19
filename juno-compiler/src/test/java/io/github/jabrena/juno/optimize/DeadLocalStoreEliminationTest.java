package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
@DisplayName("Dead local store elimination")
class DeadLocalStoreEliminationTest {
    private static final MethodRef METHOD = new MethodRef("demo/Program", "work", "()I");
    private final DeadLocalStoreElimination elimination = new DeadLocalStoreElimination();

    @Test
    @DisplayName("Removes stores to locals with no remaining reads")
    void shouldRemoveStoresToLocalsWithNoRemainingReads() {
        // Given
        Value value = Value.int32(0);
        IrMethod method = new IrMethod(METHOD, 2, List.of(value), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(value, 7),
                        new IrInstruction.StoreLocal(1, value)),
                        new IrTerminator.Return(Optional.of(value)))));

        // When
        IrMethod optimized = apply(method);

        // Then
        assertThat(optimized.blocks().getFirst().instructions())
                .containsExactly(new IrInstruction.Const(value, 7));
    }

    @Test
    @DisplayName("Preserves a store read from another basic block")
    void shouldPreserveAStoreReadFromAnotherBasicBlock() {
        // Given
        Value stored = Value.int32(0);
        Value loaded = Value.int32(1);
        IrMethod method = new IrMethod(METHOD, 1, List.of(stored, loaded), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(stored, 7),
                        new IrInstruction.StoreLocal(0, stored)),
                        new IrTerminator.Jump(10)),
                new IrBasicBlock(10, List.of(new IrInstruction.LoadLocal(loaded, 0)),
                        new IrTerminator.Return(Optional.of(loaded)))));

        // When
        IrMethod optimized = apply(method);

        // Then
        assertThat(optimized.blocks().getFirst().instructions())
                .contains(new IrInstruction.StoreLocal(0, stored));
    }

    @Test
    @DisplayName("Drops a store that every path overwrites before reading")
    void shouldDropAStoreThatEveryPathOverwritesBeforeReading() {
        // Given
        IrMethod method = new IrMethod(METHOD, 1, Value.int32Values(4), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(Value.int32(0), 1),
                        new IrInstruction.StoreLocal(0, Value.int32(0))),
                        new IrTerminator.Branch(Value.int32(3), 10, 20)),
                new IrBasicBlock(10, List.of(new IrInstruction.StoreLocal(0, Value.int32(3))),
                        new IrTerminator.Jump(30)),
                new IrBasicBlock(20, List.of(new IrInstruction.StoreLocal(0, Value.int32(3))),
                        new IrTerminator.Jump(30)),
                new IrBasicBlock(30, List.of(new IrInstruction.LoadLocal(Value.int32(1), 0)),
                        new IrTerminator.Return(Optional.of(Value.int32(1))))));

        // When
        IrMethod optimized = apply(method);

        // Then
        assertThat(optimized.blocks().getFirst().instructions())
                .containsExactly(new IrInstruction.Const(Value.int32(0), 1));
    }

    @Test
    @DisplayName("Keeps every block after the overwritten store is dropped")
    void shouldKeepEveryBlockAfterTheOverwrittenStoreIsDropped() {
        // Given
        IrMethod method = new IrMethod(METHOD, 1, Value.int32Values(4), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(Value.int32(0), 1),
                        new IrInstruction.StoreLocal(0, Value.int32(0))),
                        new IrTerminator.Branch(Value.int32(3), 10, 20)),
                new IrBasicBlock(10, List.of(new IrInstruction.StoreLocal(0, Value.int32(3))),
                        new IrTerminator.Jump(30)),
                new IrBasicBlock(20, List.of(new IrInstruction.StoreLocal(0, Value.int32(3))),
                        new IrTerminator.Jump(30)),
                new IrBasicBlock(30, List.of(new IrInstruction.LoadLocal(Value.int32(1), 0)),
                        new IrTerminator.Return(Optional.of(Value.int32(1))))));

        // When
        IrMethod optimized = apply(method);

        // Then
        assertThat(optimized.blocks()).extracting(block -> block.instructions().size()).startsWith(1, 1, 1);
    }

    @Test
    @DisplayName("Keeps a store that only one branch reads")
    void shouldKeepAStoreThatOnlyOneBranchReads() {
        // Given
        IrMethod method = new IrMethod(METHOD, 1, Value.int32Values(4), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(Value.int32(0), 1),
                        new IrInstruction.StoreLocal(0, Value.int32(0))),
                        new IrTerminator.Branch(Value.int32(3), 10, 20)),
                new IrBasicBlock(10, List.of(new IrInstruction.StoreLocal(0, Value.int32(3))),
                        new IrTerminator.Jump(30)),
                new IrBasicBlock(20, List.of(), new IrTerminator.Jump(30)),
                new IrBasicBlock(30, List.of(new IrInstruction.LoadLocal(Value.int32(1), 0)),
                        new IrTerminator.Return(Optional.of(Value.int32(1))))));

        // When
        IrMethod optimized = apply(method);

        // Then
        assertThat(optimized.blocks().getFirst().instructions())
                .contains(new IrInstruction.StoreLocal(0, Value.int32(0)));
    }

    private IrMethod apply(IrMethod method) {
        return elimination.apply(new IrProgram(METHOD, List.of(method))).methods().getFirst();
    }
}
