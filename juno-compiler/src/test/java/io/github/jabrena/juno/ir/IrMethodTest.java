package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IrMethodTest {
    private final MethodRef method = new MethodRef("demo/Typed", "main", "()V");
    private final IrBasicBlock emptyBlock = new IrBasicBlock(
            0, List.of(), new IrTerminator.Return(Optional.empty()));

    @Test
    void recordsJvmSlotWidthsForEveryScalarType() {
        assertThat(JunoType.INT32.jvmSlots()).isEqualTo(1);
        assertThat(JunoType.FLOAT32.jvmSlots()).isEqualTo(1);
        assertThat(JunoType.FLOAT64.jvmSlots()).isEqualTo(2);
    }

    @Test
    void rejectsANonContiguousValueTable() {
        List<Value> values = List.of(Value.int32(0), Value.int32(2));

        assertThatThrownBy(() -> new IrMethod(method, 0, values, List.of(), List.of(emptyBlock)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAValueWhoseTypeDisagreesWithTheMethodTable() {
        Value floatValue = new Value(0, JunoType.FLOAT32);
        IrBasicBlock block = new IrBasicBlock(0, List.of(new IrInstruction.StoreLocal(0, floatValue)),
                new IrTerminator.Return(Optional.empty()));

        assertThatThrownBy(() -> new IrMethod(method, 1, Value.int32Values(1), List.of(), List.of(block)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
