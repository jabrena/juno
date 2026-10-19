package io.github.jabrena.juno.backend.thumb2;

import io.github.jabrena.juno.backend.FrameLayout;
import io.github.jabrena.juno.backend.StackFrameSizing;
import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

@DisplayName("Thumb-2 stack frame layout")
class FrameLayoutTest {
    private final StackFrameSizing stackFrameSizing = new StackFrameSizing();

    private static final MethodRef METHOD = new MethodRef("demo.Frame", "work", "()I");

    @Test
    @DisplayName("Sequential values share one word")
    void shouldGiveSequentialValuesTheSameWord() {
        // Given
        Value stored = Value.int32(0);
        Value loaded = Value.int32(1);
        IrMethod method = sequentialMethod(stored, loaded);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(new int[] {frame.valueOffset(stored), frame.valueOffset(loaded)}).containsOnly(0);
    }

    @Test
    @DisplayName("Each used JVM local gets one word and unused locals get none")
    void shouldGiveOneWordPerUsedJvmLocal() {
        // Given
        Value stored = Value.int32(0);
        Value loaded = Value.int32(1);
        IrMethod method = sequentialMethod(stored, loaded);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        // Slots 0 and 2 are used; slot 1 is never loaded or stored, so it gets no word.
        assertThat(frame.localOffsets()).containsExactly(4, -1, 8);
    }

    @Test
    @DisplayName("The frame size follows its locals")
    void shouldSizeTheFrameFromItsLocals() {
        // Given
        Value stored = Value.int32(0);
        Value loaded = Value.int32(1);
        IrMethod method = sequentialMethod(stored, loaded);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(frame.frameSize()).isEqualTo(12);
    }

    @Test
    @DisplayName("The method stack is sized from its frame")
    void shouldSizeTheMethodStackFromItsFrame() {
        // Given
        Value stored = Value.int32(0);
        Value loaded = Value.int32(1);
        IrMethod method = sequentialMethod(stored, loaded);

        // When & Then
        assertThat(stackFrameSizing.methodStackBytes(method)).isEqualTo(48);
    }

    @Test
    @DisplayName("Values that meet at one instruction get distinct words")
    void shouldPlaceValuesThatMeetAtOneInstructionInDistinctWords() {
        // Given
        Value wide = Value.float64(0);
        Value integer = Value.int32(1);
        Value narrowed = Value.int32(2);
        Value result = Value.int32(3);
        IrMethod method = meetingMethod(wide, integer, narrowed, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(new int[] {frame.valueOffset(integer), frame.valueOffset(narrowed)}).containsExactly(8, 12);
    }

    @Test
    @DisplayName("Values that do not overlap reuse word zero")
    void shouldReuseWordZeroForNonOverlappingValues() {
        // Given
        Value wide = Value.float64(0);
        Value integer = Value.int32(1);
        Value narrowed = Value.int32(2);
        Value result = Value.int32(3);
        IrMethod method = meetingMethod(wide, integer, narrowed, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(new int[] {frame.valueOffset(wide), frame.valueOffset(result)}).containsOnly(0);
    }

    @Test
    @DisplayName("The frame for values that meet at one instruction is twenty bytes")
    void shouldSizeTheFrameForValuesThatMeetAtOneInstruction() {
        // Given
        Value wide = Value.float64(0);
        Value integer = Value.int32(1);
        Value narrowed = Value.int32(2);
        Value result = Value.int32(3);
        IrMethod method = meetingMethod(wide, integer, narrowed, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(frame.frameSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("A value that crosses blocks gets dedicated storage")
    void shouldGiveACrossBlockValueDedicatedStorage() {
        // Given
        Value crossBlock = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        IrMethod method = crossBlockMethod(crossBlock, loaded, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(new int[] {frame.valueOffset(crossBlock), frame.valueOffset(loaded), frame.valueOffset(result)})
                .containsExactly(0, 4, 8);
    }

    @Test
    @DisplayName("A local read across blocks follows the cross-block values")
    void shouldPlaceTheLocalAfterTheCrossBlockValues() {
        // Given
        Value crossBlock = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        IrMethod method = crossBlockMethod(crossBlock, loaded, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(frame.localOffset(0)).isEqualTo(12);
    }

    @Test
    @DisplayName("A constant is rematerialized instead of given a slot")
    void shouldRematerializeAConstantInsteadOfGivingItASlot() {
        // Given
        Value constant = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        IrMethod method = constantMethod(constant, loaded, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(frame.constant(constant)).isEqualTo(1000);
    }

    @Test
    @DisplayName("A loaded value has no constant")
    void shouldLeaveALoadedValueWithoutAConstant() {
        // Given
        Value constant = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        IrMethod method = constantMethod(constant, loaded, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(frame.constant(loaded)).isNull();
    }

    @Test
    @DisplayName("A loaded value and the result each get their own word")
    void shouldPlaceTheLoadedAndResultValuesInTheirOwnWords() {
        // Given
        Value constant = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        IrMethod method = constantMethod(constant, loaded, result);

        // When
        FrameLayout frame = FrameLayout.of(method);

        // Then
        assertThat(new int[] {frame.valueOffset(loaded), frame.valueOffset(result)}).containsExactly(0, 4);
    }

    @Test
    @DisplayName("A rematerialized constant has no stack offset")
    void shouldRejectTheStackOffsetOfAConstant() {
        // Given
        Value constant = Value.int32(0);
        Value loaded = Value.int32(1);
        Value result = Value.int32(2);
        FrameLayout frame = FrameLayout.of(constantMethod(constant, loaded, result));

        // When & Then
        assertThatIllegalStateException().isThrownBy(() -> frame.valueOffset(constant));
    }

    @Test
    @DisplayName("Only parameters whose local slots are read are spilled")
    void shouldSpillOnlyParametersWhoseLocalSlotsAreRead() {
        // Given
        IrMethod method = new IrMethod(new MethodRef("demo.Frame", "work", "(II)I"), 2,
                List.of(Value.int32(0)), List.of(), List.of(
                new IrBasicBlock(0, List.of(new IrInstruction.LoadLocal(Value.int32(0), 1)),
                        new IrTerminator.Return(Optional.of(Value.int32(0))))));
        FrameLayout frame = FrameLayout.of(method);
        StringBuilder output = new StringBuilder();

        // When
        new CallConvention(new AsmEmitter(Map.of())).emitParameterSpill(output, frame,
                List.of("I", "I"), true, Set.of(1));

        // Then
        assertThat(output.toString()).isEqualTo("    str r1, [sp, #4]\n");
    }

    private static IrMethod sequentialMethod(Value stored, Value loaded) {
        return new IrMethod(METHOD, 3, List.of(stored, loaded), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.LoadLocal(stored, 2),
                        new IrInstruction.StoreLocal(0, stored),
                        new IrInstruction.LoadLocal(loaded, 0)),
                        new IrTerminator.Return(Optional.of(loaded)))));
    }

    private static IrMethod meetingMethod(Value wide, Value integer, Value narrowed, Value result) {
        return new IrMethod(METHOD, 1, List.of(wide, integer, narrowed, result), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.DoubleConst(wide, 2.5),
                        new IrInstruction.LoadLocal(integer, 0),
                        new IrInstruction.DoubleToInt(narrowed, wide),
                        new IrInstruction.Binary(result, BinaryOp.ADD, integer, narrowed)),
                        new IrTerminator.Return(Optional.of(result)))));
    }

    private static IrMethod crossBlockMethod(Value crossBlock, Value loaded, Value result) {
        return new IrMethod(METHOD, 1, List.of(crossBlock, loaded, result), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.LoadLocal(crossBlock, 0),
                        new IrInstruction.StoreLocal(0, crossBlock)),
                        new IrTerminator.Jump(10)),
                new IrBasicBlock(10, List.of(
                        new IrInstruction.LoadLocal(loaded, 0),
                        new IrInstruction.Binary(result, BinaryOp.ADD, crossBlock, loaded)),
                        new IrTerminator.Return(Optional.of(result)))));
    }

    private static IrMethod constantMethod(Value constant, Value loaded, Value result) {
        return new IrMethod(METHOD, 1, List.of(constant, loaded, result), List.of(), List.of(
                new IrBasicBlock(0, List.of(
                        new IrInstruction.Const(constant, 1000)),
                        new IrTerminator.Jump(10)),
                new IrBasicBlock(10, List.of(
                        new IrInstruction.LoadLocal(loaded, 0),
                        new IrInstruction.Binary(result, BinaryOp.ADD, loaded, constant)),
                        new IrTerminator.Return(Optional.of(result)))));
    }
}
