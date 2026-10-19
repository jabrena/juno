package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@DisplayName("IR method invariants")
class IrMethodTest {
    private final MethodRef method = new MethodRef("demo/Typed", "main", "()V");
    private final IrBasicBlock emptyBlock = new IrBasicBlock(
            0, List.of(), new IrTerminator.Return(Optional.empty()));

    @Test
    @DisplayName("Records the JVM slot width of every scalar type")
    void shouldRecordJvmSlotWidthsForEveryScalarType() {
        // Given
        int[] slots = {JunoType.INT32.jvmSlots(), JunoType.FLOAT32.jvmSlots(), JunoType.FLOAT64.jvmSlots()};

        // When & Then
        assertThat(slots).containsExactly(1, 1, 2);
    }

    @Test
    @DisplayName("Rejects a non-contiguous value table")
    void shouldRejectANonContiguousValueTable() {
        // Given
        List<Value> values = List.of(Value.int32(0), Value.int32(2));

        // When & Then
        assertThatThrownBy(() -> new IrMethod(method, 0, values, List.of(), List.of(emptyBlock)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Rejects a value whose type disagrees with the method table")
    void shouldRejectAValueWhoseTypeDisagreesWithTheMethodTable() {
        // Given
        Value floatValue = new Value(0, JunoType.FLOAT32);
        IrBasicBlock block = new IrBasicBlock(0, List.of(new IrInstruction.StoreLocal(0, floatValue)),
                new IrTerminator.Return(Optional.empty()));

        // When & Then
        assertThatThrownBy(() -> new IrMethod(method, 1, Value.int32Values(1), List.of(), List.of(block)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
