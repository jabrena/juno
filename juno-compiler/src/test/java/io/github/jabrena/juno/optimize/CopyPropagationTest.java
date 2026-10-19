package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.intrinsic.Intrinsic;
import io.github.jabrena.juno.ir.ArrayElementType;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.JunoType;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Copy propagation")
class CopyPropagationTest {
    private final CopyPropagation propagation = new CopyPropagation();
    private final MethodRef method = new MethodRef("demo/Program", "main", "()V");

    @Test
    @DisplayName("Replaces a load with the value stored earlier in the block")
    void shouldReplaceALoadWithTheValueStoredEarlierInTheBlock() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value one = Value.int32(2);
        Value sum = Value.int32(3);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 41),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Const(one, 1),
                new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, one));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(
                new IrBasicBlock(0, instructions, new IrTerminator.Return(Optional.of(sum)))))).methods().get(0);

        // Then
        assertThat(optimized.blocks().get(0).instructions()).hasSize(4)
                .noneMatch(IrInstruction.LoadLocal.class::isInstance);
    }

    @Test
    @DisplayName("The rewritten sum reads the stored source and the constant one")
    void shouldRewriteTheSumToReadTheStoredSourceAndTheConstantOne() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value one = Value.int32(2);
        Value sum = Value.int32(3);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 41),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Const(one, 1),
                new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, one));
        IrMethod optimized = propagation.apply(programOf(List.of(
                new IrBasicBlock(0, instructions, new IrTerminator.Return(Optional.of(sum)))))).methods().get(0);

        // When
        IrInstruction.Binary binary = (IrInstruction.Binary) optimized.blocks().get(0).instructions().get(3);

        // Then
        assertThat(List.of(binary.left(), binary.right())).containsExactly(source, one);
    }

    @Test
    @DisplayName("Uses the most recent store and rewrites the terminator")
    void shouldUseTheMostRecentStoreAndRewriteTheTerminator() {
        // Given
        Value first = Value.int32(0);
        Value second = Value.int32(1);
        Value loaded = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(first, 1),
                new IrInstruction.StoreLocal(0, first),
                new IrInstruction.Const(second, 2),
                new IrInstruction.StoreLocal(0, second),
                new IrInstruction.LoadLocal(loaded, 0));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(
                new IrBasicBlock(0, instructions, new IrTerminator.Return(Optional.of(loaded)))))).methods().get(0);

        // Then
        assertThat(optimized.blocks().get(0).terminator())
                .isEqualTo(new IrTerminator.Return(Optional.of(second)));
    }

    @Test
    @DisplayName("Does not propagate across a branch merge")
    void shouldNotPropagateAcrossABranchMerge() {
        // Given
        Value first = Value.int32(0);
        Value second = Value.int32(1);
        Value condition = Value.int32(2);
        Value merged = Value.int32(3);
        IrBasicBlock header = new IrBasicBlock(0, List.of(new IrInstruction.Const(condition, 1)),
                new IrTerminator.Branch(condition, 10, 20));
        IrBasicBlock left = new IrBasicBlock(10, List.of(new IrInstruction.StoreLocal(0, first)),
                new IrTerminator.Jump(30));
        IrBasicBlock right = new IrBasicBlock(20, List.of(new IrInstruction.StoreLocal(0, second)),
                new IrTerminator.Jump(30));
        IrBasicBlock merge = new IrBasicBlock(30, List.of(new IrInstruction.LoadLocal(merged, 0)),
                new IrTerminator.Return(Optional.of(merged)));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(header, left, right, merge))).methods().get(0);

        // Then
        assertThat(optimized.blocks().get(3).instructions()).containsExactly(new IrInstruction.LoadLocal(merged, 0));
    }

    @Test
    @DisplayName("The merge keeps its return of the loaded value when the branches differ")
    void shouldKeepTheMergeReturnOfTheLoadedValueWhenTheBranchesDiffer() {
        // Given
        Value first = Value.int32(0);
        Value second = Value.int32(1);
        Value condition = Value.int32(2);
        Value merged = Value.int32(3);
        IrBasicBlock header = new IrBasicBlock(0, List.of(new IrInstruction.Const(condition, 1)),
                new IrTerminator.Branch(condition, 10, 20));
        IrBasicBlock left = new IrBasicBlock(10, List.of(new IrInstruction.StoreLocal(0, first)),
                new IrTerminator.Jump(30));
        IrBasicBlock right = new IrBasicBlock(20, List.of(new IrInstruction.StoreLocal(0, second)),
                new IrTerminator.Jump(30));
        IrBasicBlock merge = new IrBasicBlock(30, List.of(new IrInstruction.LoadLocal(merged, 0)),
                new IrTerminator.Return(Optional.of(merged)));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(header, left, right, merge))).methods().get(0);

        // Then
        assertThat(optimized.blocks().get(3).terminator())
                .isEqualTo(new IrTerminator.Return(Optional.of(merged)));
    }

    @Test
    @DisplayName("Propagates a value across a linear control-flow edge")
    void shouldPropagateAcrossALinearControlFlowEdge() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        IrBasicBlock producer = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source)), new IrTerminator.Jump(10));
        IrBasicBlock consumer = new IrBasicBlock(10, List.of(
                new IrInstruction.LoadLocal(loaded, 0)), new IrTerminator.Return(Optional.of(loaded)));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(producer, consumer))).methods().getFirst();

        // Then
        assertThat(optimized.blocks().get(1).instructions()).isEmpty();
    }

    @Test
    @DisplayName("The consumer returns the propagated source across a linear edge")
    void shouldReturnThePropagatedSourceAcrossALinearEdge() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        IrBasicBlock producer = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source)), new IrTerminator.Jump(10));
        IrBasicBlock consumer = new IrBasicBlock(10, List.of(
                new IrInstruction.LoadLocal(loaded, 0)), new IrTerminator.Return(Optional.of(loaded)));
        IrMethod optimized = propagation.apply(programOf(List.of(producer, consumer))).methods().getFirst();

        // When & Then
        assertThat(((IrTerminator.Return) optimized.blocks().get(1).terminator()).value()).contains(source);
    }

    @Test
    @DisplayName("Keeps a producing load whose SSA value escapes to another block")
    void shouldKeepAProducingLoadWhenItsSsaValueEscapesToAnotherBlock() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        IrBasicBlock producer = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0)), new IrTerminator.Jump(10));
        IrBasicBlock consumer = new IrBasicBlock(10, List.of(),
                new IrTerminator.Return(Optional.of(loaded)));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(producer, consumer))).methods().getFirst();

        // Then
        assertThat(optimized.blocks().getFirst().instructions())
                .contains(new IrInstruction.LoadLocal(loaded, 0));
    }

    @Test
    @DisplayName("The escaping SSA value is returned by the consumer block")
    void shouldReturnTheEscapingSsaValueFromTheConsumerBlock() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        IrBasicBlock producer = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0)), new IrTerminator.Jump(10));
        IrBasicBlock consumer = new IrBasicBlock(10, List.of(),
                new IrTerminator.Return(Optional.of(loaded)));
        IrMethod optimized = propagation.apply(programOf(List.of(producer, consumer))).methods().getFirst();

        // When & Then
        assertThat(((IrTerminator.Return) optimized.blocks().get(1).terminator()).value()).contains(loaded);
    }

    @Test
    @DisplayName("Carries the replacement of a removed load across an edge")
    void shouldCarryTheReplacementOfARemovedLoadAcrossAnEdge() {
        // Given
        // The JVM's "astore; aload; astore" round trip through an operand-stack slot: the second store keeps
        // the removed load's replacement, so the next block must read that, not the load nothing defines now.
        Value source = Value.int32(0);
        Value copied = Value.int32(1);
        Value loaded = Value.int32(2);
        IrBasicBlock producer = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(6, source),
                new IrInstruction.LoadLocal(copied, 6),
                new IrInstruction.StoreLocal(2, copied)), new IrTerminator.Jump(10));
        IrBasicBlock consumer = new IrBasicBlock(10, List.of(
                new IrInstruction.LoadLocal(loaded, 2)), new IrTerminator.Return(Optional.of(loaded)));
        IrMethod optimized = propagation.apply(programOf(List.of(producer, consumer))).methods().getFirst();

        // When & Then
        assertThat(optimized.blocks().getFirst().instructions())
                .doesNotContain(new IrInstruction.LoadLocal(copied, 6));
    }

    @Test
    @DisplayName("The consumer returns the source that replaced the removed load")
    void shouldReturnTheSourceThatReplacedTheRemovedLoad() {
        // Given
        Value source = Value.int32(0);
        Value copied = Value.int32(1);
        Value loaded = Value.int32(2);
        IrBasicBlock producer = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(6, source),
                new IrInstruction.LoadLocal(copied, 6),
                new IrInstruction.StoreLocal(2, copied)), new IrTerminator.Jump(10));
        IrBasicBlock consumer = new IrBasicBlock(10, List.of(
                new IrInstruction.LoadLocal(loaded, 2)), new IrTerminator.Return(Optional.of(loaded)));
        IrMethod optimized = propagation.apply(programOf(List.of(producer, consumer))).methods().getFirst();

        // When & Then
        assertThat(((IrTerminator.Return) optimized.blocks().get(1).terminator()).value()).contains(source);
    }

    @Test
    @DisplayName("Propagates the same value through both sides of a branch merge")
    void shouldPropagateTheSameValueThroughBothSidesOfABranchMerge() {
        // Given
        Value source = Value.int32(0);
        Value condition = Value.int32(1);
        Value loaded = Value.int32(2);
        IrBasicBlock header = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.Const(condition, 1),
                new IrInstruction.StoreLocal(0, source)),
                new IrTerminator.Branch(condition, 10, 20));
        IrBasicBlock left = new IrBasicBlock(10, List.of(), new IrTerminator.Jump(30));
        IrBasicBlock right = new IrBasicBlock(20, List.of(), new IrTerminator.Jump(30));
        IrBasicBlock merge = new IrBasicBlock(30, List.of(
                new IrInstruction.LoadLocal(loaded, 0)), new IrTerminator.Return(Optional.of(loaded)));
        IrMethod optimized = propagation.apply(programOf(List.of(header, left, right, merge))).methods().getFirst();

        // When & Then
        assertThat(optimized.blocks().get(3).instructions()).isEmpty();
    }

    @Test
    @DisplayName("The merge returns the source that both branches share")
    void shouldReturnTheSourceThatBothBranchesShareAtTheMerge() {
        // Given
        Value source = Value.int32(0);
        Value condition = Value.int32(1);
        Value loaded = Value.int32(2);
        IrBasicBlock header = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.Const(condition, 1),
                new IrInstruction.StoreLocal(0, source)),
                new IrTerminator.Branch(condition, 10, 20));
        IrBasicBlock left = new IrBasicBlock(10, List.of(), new IrTerminator.Jump(30));
        IrBasicBlock right = new IrBasicBlock(20, List.of(), new IrTerminator.Jump(30));
        IrBasicBlock merge = new IrBasicBlock(30, List.of(
                new IrInstruction.LoadLocal(loaded, 0)), new IrTerminator.Return(Optional.of(loaded)));
        IrMethod optimized = propagation.apply(programOf(List.of(header, left, right, merge))).methods().getFirst();

        // When & Then
        assertThat(((IrTerminator.Return) optimized.blocks().get(3).terminator()).value()).contains(source);
    }

    @Test
    @DisplayName("Propagates an unchanged loop invariant")
    void shouldPropagateAnUnchangedLoopInvariant() {
        // Given
        Value initial = Value.int32(0);
        Value condition = Value.int32(1);
        Value invariantLoad = Value.int32(2);
        IrBasicBlock entry = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(initial, 7),
                new IrInstruction.Const(condition, 1),
                new IrInstruction.StoreLocal(0, initial)), new IrTerminator.Jump(10));
        IrBasicBlock header = new IrBasicBlock(10, List.of(
                new IrInstruction.LoadLocal(invariantLoad, 0),
                new IrInstruction.StoreLocal(1, invariantLoad)),
                new IrTerminator.Branch(condition, 20, 30));
        IrBasicBlock body = new IrBasicBlock(20, List.of(), new IrTerminator.Jump(10));
        IrBasicBlock exit = new IrBasicBlock(30, List.of(), new IrTerminator.Return(Optional.empty()));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(entry, header, body, exit))).methods().getFirst();

        // Then
        assertThat(optimized.blocks().get(1).instructions())
                .containsExactly(new IrInstruction.StoreLocal(1, initial));
    }

    @Test
    @DisplayName("Keeps a load when the loop backedge replaces the local")
    void shouldKeepALoadWhenTheLoopBackedgeReplacesTheLocal() {
        // Given
        Value initial = Value.int32(0);
        Value replacement = Value.int32(1);
        Value condition = Value.int32(2);
        Value loaded = Value.int32(3);
        IrBasicBlock entry = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(initial, 7),
                new IrInstruction.Const(replacement, 8),
                new IrInstruction.Const(condition, 1),
                new IrInstruction.StoreLocal(0, initial)), new IrTerminator.Jump(10));
        IrBasicBlock header = new IrBasicBlock(10, List.of(
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.StoreLocal(1, loaded)),
                new IrTerminator.Branch(condition, 20, 30));
        IrBasicBlock body = new IrBasicBlock(20, List.of(
                new IrInstruction.StoreLocal(0, replacement)), new IrTerminator.Jump(10));
        IrBasicBlock exit = new IrBasicBlock(30, List.of(), new IrTerminator.Return(Optional.empty()));

        // When
        IrMethod optimized = propagation.apply(programOf(List.of(entry, header, body, exit))).methods().getFirst();

        // Then
        assertThat(optimized.blocks().get(1).instructions())
                .containsExactly(new IrInstruction.LoadLocal(loaded, 0),
                        new IrInstruction.StoreLocal(1, loaded));
    }

    @Test
    @DisplayName("Does not propagate across different slot types")
    void shouldNotPropagateAcrossDifferentSlotTypes() {
        // Given
        Value integer = Value.int32(0);
        Value floating = new Value(1, JunoType.FLOAT32);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(integer, 1),
                new IrInstruction.StoreLocal(0, integer),
                new IrInstruction.LoadLocal(floating, 0)),
                new IrTerminator.Return(Optional.empty()));
        IrMethod irMethod = new IrMethod(method, 1, List.of(integer, floating), List.of(), List.of(block));

        // When
        IrMethod optimized = propagation.apply(new IrProgram(method, List.of(irMethod))).methods().get(0);

        // Then
        assertThat(optimized.blocks().get(0).instructions()).anyMatch(IrInstruction.LoadLocal.class::isInstance);
    }

    @Test
    @DisplayName("Rewrites the unary and call operands to the stored source")
    void shouldRewriteTheUnaryAndCallOperandsToTheStoredSource() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value callResult = Value.int32(2);
        Value negatedInt = Value.int32(15);
        MethodRef callee = new MethodRef("demo/Program", "callee", "(I)I");
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Unary(negatedInt, UnaryOp.NEGATE, loaded),
                new IrInstruction.Call(Optional.of(callResult), callee, List.of(loaded)));
        List<IrInstruction> rewritten = rewrite(instructions);

        // When & Then
        assertThat(first(rewritten, IrInstruction.Unary.class).value()).isEqualTo(source);
        assertThat(first(rewritten, IrInstruction.Call.class).arguments()).isEqualTo(List.of(source));
    }

    @Test
    @DisplayName("Rewrites the intrinsic receiver and arguments to the stored source")
    void shouldRewriteTheIntrinsicReceiverAndArgumentsToTheStoredSource() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value intrinsicResult = Value.int32(3);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.IntrinsicCall(Optional.of(intrinsicResult), Intrinsic.GPIO_DIGITAL_READ,
                        Optional.of(loaded), List.of(loaded), List.of()));
        List<IrInstruction> rewritten = rewrite(instructions);

        // When
        IrInstruction.IntrinsicCall intrinsic = first(rewritten, IrInstruction.IntrinsicCall.class);

        // Then
        assertThat(intrinsic.receiver()).isEqualTo(Optional.of(source));
        assertThat(intrinsic.arguments()).isEqualTo(List.of(source));
    }

    @Test
    @DisplayName("Rewrites the array load, store, and bounds-check operands to the stored source")
    void shouldRewriteTheArrayLoadStoreAndBoundsCheckOperandsToTheStoredSource() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value arrayResult = Value.int32(4);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.ArrayLoad(arrayResult, ArrayElementType.INT, loaded, loaded),
                new IrInstruction.ArrayStore(ArrayElementType.INT, loaded, loaded, loaded),
                new IrInstruction.BoundsCheck(loaded, 4));
        List<IrInstruction> rewritten = rewrite(instructions);

        // When
        IrInstruction.ArrayLoad arrayLoad = first(rewritten, IrInstruction.ArrayLoad.class);
        IrInstruction.ArrayStore arrayStore = first(rewritten, IrInstruction.ArrayStore.class);

        // Then
        assertThat(List.of(arrayLoad.array(), arrayLoad.index())).containsOnly(source);
        assertThat(List.of(arrayStore.array(), arrayStore.index(), arrayStore.value())).containsOnly(source);
        assertThat(first(rewritten, IrInstruction.BoundsCheck.class).index()).isEqualTo(source);
    }

    @Test
    @DisplayName("Rewrites the paired long binary operands to the stored source")
    void shouldRewriteThePairedLongBinaryOperandsToTheStoredSource() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value longLow = Value.int32(5);
        Value longHigh = Value.int32(6);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.LongBinary(longLow, longHigh, BinaryOp.ADD, loaded, loaded, loaded, loaded));
        List<IrInstruction> rewritten = rewrite(instructions);

        // When
        IrInstruction.LongBinary longBinary = first(rewritten, IrInstruction.LongBinary.class);

        // Then
        assertThat(List.of(
                longBinary.leftLow(), longBinary.leftHigh(), longBinary.rightLow(), longBinary.rightHigh()))
                .containsOnly(source);
    }

    @Test
    @DisplayName("Rewrites the paired long shift, negate, and compare operands to the stored source")
    void shouldRewriteThePairedLongShiftNegateAndCompareOperandsToTheStoredSource() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value shiftedLow = Value.int32(7);
        Value shiftedHigh = Value.int32(8);
        Value negatedLow = Value.int32(9);
        Value negatedHigh = Value.int32(10);
        Value compared = Value.int32(11);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.LongShift(shiftedLow, shiftedHigh, BinaryOp.SHIFT_LEFT, loaded, loaded, loaded),
                new IrInstruction.LongNegate(negatedLow, negatedHigh, loaded, loaded),
                new IrInstruction.LongCompare(compared, loaded, loaded, loaded, loaded));
        List<IrInstruction> rewritten = rewrite(instructions);

        // When
        IrInstruction.LongShift longShift = first(rewritten, IrInstruction.LongShift.class);
        IrInstruction.LongNegate longNegate = first(rewritten, IrInstruction.LongNegate.class);
        IrInstruction.LongCompare longCompare = first(rewritten, IrInstruction.LongCompare.class);

        // Then
        assertThat(List.of(longShift.valueLow(), longShift.valueHigh(), longShift.shiftAmount())).containsOnly(source);
        assertThat(List.of(longNegate.valueLow(), longNegate.valueHigh())).containsOnly(source);
        assertThat(List.of(
                longCompare.leftLow(), longCompare.leftHigh(), longCompare.rightLow(), longCompare.rightHigh()))
                .containsOnly(source);
    }

    @Test
    @DisplayName("Rewrites the widening and narrowing long conversion operands to the stored source")
    void shouldRewriteTheWideningAndNarrowingLongConversionOperandsToTheStoredSource() {
        // Given
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value widenedLow = Value.int32(12);
        Value widenedHigh = Value.int32(13);
        Value narrowed = Value.int32(14);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.IntToLong(widenedLow, widenedHigh, loaded),
                new IrInstruction.LongToInt(narrowed, loaded, loaded));
        List<IrInstruction> rewritten = rewrite(instructions);

        // When & Then
        assertThat(first(rewritten, IrInstruction.IntToLong.class).value()).isEqualTo(source);
        IrInstruction.LongToInt longToInt = first(rewritten, IrInstruction.LongToInt.class);
        assertThat(List.of(longToInt.valueLow(), longToInt.valueHigh())).containsOnly(source);
    }

    @Test
    @DisplayName("Unlocks constant folding and dead-block elimination")
    void shouldUnlockConstantFoldingAndDeadBlockElimination() {
        // Given
        Value five = Value.int32(0);
        Value loaded = Value.int32(1);
        Value otherFive = Value.int32(2);
        Value condition = Value.int32(3);
        IrBasicBlock header = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(five, 5),
                new IrInstruction.StoreLocal(0, five),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Const(otherFive, 5),
                new IrInstruction.Compare(condition, Condition.EQUAL, loaded, otherFive)),
                new IrTerminator.Branch(condition, 10, 20));
        IrBasicBlock live = new IrBasicBlock(10, List.of(), new IrTerminator.Return(Optional.empty()));
        IrBasicBlock dead = new IrBasicBlock(20, List.of(), new IrTerminator.Return(Optional.empty()));
        IrProgram optimized = programOf(List.of(header, live, dead));

        // When
        optimized = propagation.apply(optimized);
        optimized = new ConstantFolder().apply(optimized);
        optimized = new DeadBlockElimination().apply(optimized);

        // Then
        IrMethod result = optimized.methods().get(0);
        assertThat(result.blocks().stream().map(IrBasicBlock::start).toList()).isEqualTo(List.of(0, 10));
    }

    @Test
    @DisplayName("The header of the unlocked program becomes a jump to the live block")
    void shouldTurnTheHeaderIntoAJumpToTheLiveBlockAfterUnlocking() {
        // Given
        Value five = Value.int32(0);
        Value loaded = Value.int32(1);
        Value otherFive = Value.int32(2);
        Value condition = Value.int32(3);
        IrBasicBlock header = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(five, 5),
                new IrInstruction.StoreLocal(0, five),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Const(otherFive, 5),
                new IrInstruction.Compare(condition, Condition.EQUAL, loaded, otherFive)),
                new IrTerminator.Branch(condition, 10, 20));
        IrBasicBlock live = new IrBasicBlock(10, List.of(), new IrTerminator.Return(Optional.empty()));
        IrBasicBlock dead = new IrBasicBlock(20, List.of(), new IrTerminator.Return(Optional.empty()));
        IrProgram optimized = programOf(List.of(header, live, dead));
        optimized = propagation.apply(optimized);
        optimized = new ConstantFolder().apply(optimized);
        optimized = new DeadBlockElimination().apply(optimized);

        // When & Then
        assertThat(optimized.methods().get(0).blocks().get(0).terminator()).isEqualTo(new IrTerminator.Jump(10));
    }

    @Test
    @DisplayName("The folded comparison of the unlocked program is the constant one")
    void shouldFoldTheComparisonToTheConstantOneAfterUnlocking() {
        // Given
        Value five = Value.int32(0);
        Value loaded = Value.int32(1);
        Value otherFive = Value.int32(2);
        Value condition = Value.int32(3);
        IrBasicBlock header = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(five, 5),
                new IrInstruction.StoreLocal(0, five),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Const(otherFive, 5),
                new IrInstruction.Compare(condition, Condition.EQUAL, loaded, otherFive)),
                new IrTerminator.Branch(condition, 10, 20));
        IrBasicBlock live = new IrBasicBlock(10, List.of(), new IrTerminator.Return(Optional.empty()));
        IrBasicBlock dead = new IrBasicBlock(20, List.of(), new IrTerminator.Return(Optional.empty()));
        IrProgram optimized = programOf(List.of(header, live, dead));
        optimized = propagation.apply(optimized);
        optimized = new ConstantFolder().apply(optimized);
        optimized = new DeadBlockElimination().apply(optimized);

        // When
        IrInstruction.Const foldedComparison = (IrInstruction.Const) optimized.methods().get(0).blocks().get(0)
                .instructions().get(3);

        // Then
        assertThat(foldedComparison.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("Turns a load into the constant both branches stored through different values")
    void shouldTurnALoadIntoTheConstantBothBranchesStoredThroughDifferentValues() {
        // When
        IrProgram optimized = new CopyPropagation().apply(programOf(List.of(
                new IrBasicBlock(0, List.of(), new IrTerminator.Branch(Value.int32(9), 10, 20)),
                new IrBasicBlock(10, List.of(
                        new IrInstruction.Const(Value.int32(1), 0),
                        new IrInstruction.StoreLocal(0, Value.int32(1))), new IrTerminator.Jump(30)),
                new IrBasicBlock(20, List.of(
                        new IrInstruction.Const(Value.int32(2), 0),
                        new IrInstruction.StoreLocal(0, Value.int32(2))), new IrTerminator.Jump(30)),
                new IrBasicBlock(30, List.of(
                        new IrInstruction.LoadLocal(Value.int32(3), 0)),
                        new IrTerminator.Return(Optional.of(Value.int32(3)))))));

        // Then
        IrBasicBlock join = optimized.methods().getFirst().blocks().get(3);
        assertThat(join.instructions()).containsExactly(new IrInstruction.Const(Value.int32(3), 0));
    }

    @Test
    @DisplayName("The join returns the constant when both branches stored the same constant")
    void shouldReturnTheSharedConstantFromTheJoin() {
        // When
        IrProgram optimized = new CopyPropagation().apply(programOf(List.of(
                new IrBasicBlock(0, List.of(), new IrTerminator.Branch(Value.int32(9), 10, 20)),
                new IrBasicBlock(10, List.of(
                        new IrInstruction.Const(Value.int32(1), 0),
                        new IrInstruction.StoreLocal(0, Value.int32(1))), new IrTerminator.Jump(30)),
                new IrBasicBlock(20, List.of(
                        new IrInstruction.Const(Value.int32(2), 0),
                        new IrInstruction.StoreLocal(0, Value.int32(2))), new IrTerminator.Jump(30)),
                new IrBasicBlock(30, List.of(
                        new IrInstruction.LoadLocal(Value.int32(3), 0)),
                        new IrTerminator.Return(Optional.of(Value.int32(3)))))));

        // When & Then
        IrBasicBlock join = optimized.methods().getFirst().blocks().get(3);
        assertThat(join.terminator()).isEqualTo(new IrTerminator.Return(Optional.of(Value.int32(3))));
    }

    @Test
    @DisplayName("Keeps the load when the branches stored different constants")
    void shouldKeepTheLoadWhenTheBranchesStoredDifferentConstants() {
        // When
        IrProgram optimized = new CopyPropagation().apply(programOf(List.of(
                new IrBasicBlock(0, List.of(), new IrTerminator.Branch(Value.int32(9), 10, 20)),
                new IrBasicBlock(10, List.of(
                        new IrInstruction.Const(Value.int32(1), 0),
                        new IrInstruction.StoreLocal(0, Value.int32(1))), new IrTerminator.Jump(30)),
                new IrBasicBlock(20, List.of(
                        new IrInstruction.Const(Value.int32(2), 1),
                        new IrInstruction.StoreLocal(0, Value.int32(2))), new IrTerminator.Jump(30)),
                new IrBasicBlock(30, List.of(
                        new IrInstruction.LoadLocal(Value.int32(3), 0)),
                        new IrTerminator.Return(Optional.of(Value.int32(3)))))));

        // When & Then
        assertThat(optimized.methods().getFirst().blocks().get(3).instructions())
                .containsExactly(new IrInstruction.LoadLocal(Value.int32(3), 0));
    }

    private List<IrInstruction> rewrite(List<IrInstruction> instructions) {
        return propagation.apply(programOf(List.of(
                        new IrBasicBlock(0, instructions, new IrTerminator.Return(Optional.empty())))))
                .methods().get(0).blocks().get(0).instructions();
    }

    private IrProgram programOf(List<IrBasicBlock> blocks) {
        return new IrProgram(method, List.of(new IrMethod(method, 4, Value.int32Values(32), List.of(), blocks)));
    }

    private <T extends IrInstruction> T first(List<IrInstruction> instructions, Class<T> type) {
        return instructions.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
