package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FrameLayoutTest {
    private static final MethodRef METHOD = new MethodRef("demo.Frame", "work", "()I");

    @Test
    void usesOneWordPerJvmLocalAndReusesSequentialValueStorage() {
        Value stored = Value.int32(0);
        Value loaded = Value.int32(1);
        IrMethod method = new IrMethod(METHOD, 3, List.of(stored, loaded), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.LoadLocal(stored, 2),
                        new IrInstruction.StoreLocal(0, stored),
                        new IrInstruction.LoadLocal(loaded, 0)),
                        new IrTerminator.Return(Optional.of(loaded)))));

        FrameLayout frame = FrameLayout.of(method);

        assertThat(frame.valueOffset(stored)).isZero();
        assertThat(frame.valueOffset(loaded)).isZero();
        // Slots 0 and 2 are used; slot 1 is never loaded or stored, so it gets no word.
        assertThat(frame.localOffsets()).containsExactly(4, -1, 8);
        assertThat(frame.frameSize()).isEqualTo(12);
        assertThat(StackFrameSizing.methodStackBytes(method)).isEqualTo(48);
    }

    @Test
    void keepsValuesThatMeetAtOneInstructionInDistinctWords() {
        Value wide = Value.float64(0);
        Value integer = Value.int32(1);
        Value narrowed = Value.int32(2);
        Value result = Value.int32(3);
        IrMethod method = new IrMethod(METHOD, 1, List.of(wide, integer, narrowed, result), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.DoubleConst(wide, 2.5),
                        new IrInstruction.LoadLocal(integer, 0),
                        new IrInstruction.DoubleToInt(narrowed, wide),
                        new IrInstruction.Binary(result, BinaryOp.ADD, integer, narrowed)),
                        new IrTerminator.Return(Optional.of(result)))));

        FrameLayout frame = FrameLayout.of(method);

        assertThat(frame.valueOffset(wide)).isZero();
        assertThat(frame.valueOffset(integer)).isEqualTo(8);
        assertThat(frame.valueOffset(narrowed)).isEqualTo(12);
        assertThat(frame.valueOffset(result)).isZero();
        assertThat(frame.frameSize()).isEqualTo(20);
    }

    @Test
    void givesCrossBlockValuesDedicatedStorage() {
        Value crossBlock = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        IrMethod method = new IrMethod(METHOD, 1, List.of(crossBlock, loaded, result), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.LoadLocal(crossBlock, 0),
                        new IrInstruction.StoreLocal(0, crossBlock)),
                        new IrTerminator.Jump(10)),
                new IrBasicBlock(10, List.of(
                        new IrInstruction.LoadLocal(loaded, 0),
                        new IrInstruction.Binary(result, BinaryOp.ADD, crossBlock, loaded)),
                        new IrTerminator.Return(Optional.of(result)))));

        FrameLayout frame = FrameLayout.of(method);

        assertThat(frame.valueOffset(crossBlock)).isZero();
        assertThat(frame.valueOffset(loaded)).isEqualTo(4);
        assertThat(frame.valueOffset(result)).isEqualTo(8);
        assertThat(frame.localOffset(0)).isEqualTo(12);
    }

    @Test
    void rematerializesAConstantInsteadOfGivingItASlot() {
        Value constant = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        IrMethod method = new IrMethod(METHOD, 1, List.of(constant, loaded, result), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(constant, 1000)),
                        new IrTerminator.Jump(10)),
                new IrBasicBlock(10, List.of(
                        new IrInstruction.LoadLocal(loaded, 0),
                        new IrInstruction.Binary(result, BinaryOp.ADD, loaded, constant)),
                        new IrTerminator.Return(Optional.of(result)))));

        FrameLayout frame = FrameLayout.of(method);

        assertThat(frame.constant(constant)).isEqualTo(1000);
        assertThat(frame.constant(loaded)).isNull();
        assertThat(frame.valueOffset(loaded)).isZero();
        assertThat(frame.valueOffset(result)).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThatIllegalStateException()
                .isThrownBy(() -> frame.valueOffset(constant));
    }

    @Test
    void spillsOnlyParametersWhoseLocalSlotsAreRead() {
        IrMethod method = new IrMethod(new MethodRef("demo.Frame", "work", "(II)I"), 2,
                List.of(Value.int32(0)), List.of(), List.of(
                new IrBasicBlock(0, List.of(new IrInstruction.LoadLocal(Value.int32(0), 1)),
                        new IrTerminator.Return(Optional.of(Value.int32(0))))));
        FrameLayout frame = FrameLayout.of(method);
        StringBuilder output = new StringBuilder();

        new CallConvention(new AsmEmitter(Map.of())).emitParameterSpill(output, frame,
                List.of("I", "I"), true, Set.of(1));

        assertThat(output.toString()).isEqualTo("    str r1, [sp, #4]\n");
    }
}
