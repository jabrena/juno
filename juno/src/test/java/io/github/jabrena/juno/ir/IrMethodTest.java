package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IrMethodTest {
    private final MethodRef method = new MethodRef("demo/Typed", "main", "()V");
    private final IrBasicBlock emptyBlock = new IrBasicBlock(
            0, List.of(), new IrTerminator.Return(Optional.empty()));

    @Test
    void recordsJvmSlotWidthsForEveryScalarType() {
        assertEquals(1, JunoType.INT32.jvmSlots());
        assertEquals(1, JunoType.FLOAT32.jvmSlots());
        assertEquals(2, JunoType.FLOAT64.jvmSlots());
    }

    @Test
    void rejectsANonContiguousValueTable() {
        List<Value> values = List.of(Value.int32(0), Value.int32(2));

        assertThrows(IllegalArgumentException.class,
                () -> new IrMethod(method, 0, values, List.of(), List.of(emptyBlock)));
    }

    @Test
    void rejectsAValueWhoseTypeDisagreesWithTheMethodTable() {
        Value floatValue = new Value(0, JunoType.FLOAT32);
        IrBasicBlock block = new IrBasicBlock(0, List.of(new IrInstruction.StoreLocal(0, floatValue)),
                new IrTerminator.Return(Optional.empty()));

        assertThrows(IllegalArgumentException.class,
                () -> new IrMethod(method, 1, Value.int32Values(1), List.of(), List.of(block)));
    }
}
