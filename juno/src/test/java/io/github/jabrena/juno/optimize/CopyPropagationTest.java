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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CopyPropagationTest {
    private final CopyPropagation propagation = new CopyPropagation();
    private final MethodRef method = new MethodRef("demo/Program", "main", "()V");

    @Test
    void replacesALoadWithTheValueStoredEarlierInTheBlock() {
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
                new IrBasicBlock(0, instructions, new IrTerminator.Return(Optional.of(sum))))))
                .methods().get(0);

        List<IrInstruction> rewritten = optimized.blocks().get(0).instructions();
        assertThat(rewritten.size()).isEqualTo(4);
        assertThat(rewritten.stream().noneMatch(IrInstruction.LoadLocal.class::isInstance)).isTrue();
        assertThat(rewritten.get(3)).isInstanceOf(IrInstruction.Binary.class);
        IrInstruction.Binary binary = (IrInstruction.Binary) rewritten.get(3);
        assertThat(binary.left()).isEqualTo(source);
        assertThat(binary.right()).isEqualTo(one);
    }

    @Test
    void usesTheMostRecentStoreAndRewritesTheTerminator() {
        Value first = Value.int32(0);
        Value second = Value.int32(1);
        Value loaded = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(first, 1),
                new IrInstruction.StoreLocal(0, first),
                new IrInstruction.Const(second, 2),
                new IrInstruction.StoreLocal(0, second),
                new IrInstruction.LoadLocal(loaded, 0));

        IrMethod optimized = propagation.apply(programOf(List.of(
                new IrBasicBlock(0, instructions, new IrTerminator.Return(Optional.of(loaded))))))
                .methods().get(0);

        assertThat(optimized.blocks().get(0).terminator()).isInstanceOf(IrTerminator.Return.class);
        IrTerminator.Return returned = (IrTerminator.Return) optimized.blocks().get(0).terminator();
        assertThat(returned.value()).isEqualTo(Optional.of(second));
    }

    @Test
    void doesNotPropagateAcrossABranchMerge() {
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

        IrMethod optimized = propagation.apply(programOf(List.of(header, left, right, merge))).methods().get(0);

        IrBasicBlock optimizedMerge = optimized.blocks().get(3);
        assertThat(optimizedMerge.instructions().size()).isEqualTo(1);
        assertThat(optimizedMerge.instructions().get(0)).isInstanceOf(IrInstruction.LoadLocal.class);
        assertThat(optimizedMerge.terminator()).isInstanceOf(IrTerminator.Return.class);
        IrTerminator.Return mergeReturn = (IrTerminator.Return) optimizedMerge.terminator();
        assertThat(mergeReturn.value()).isEqualTo(Optional.of(merged));
    }

    @Test
    void doesNotPropagateAcrossDifferentSlotTypes() {
        Value integer = Value.int32(0);
        Value floating = new Value(1, JunoType.FLOAT32);
        IrBasicBlock block = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(integer, 1),
                new IrInstruction.StoreLocal(0, integer),
                new IrInstruction.LoadLocal(floating, 0)),
                new IrTerminator.Return(Optional.empty()));
        IrMethod method = new IrMethod(this.method, 1, List.of(integer, floating), List.of(), List.of(block));
        IrProgram program = new IrProgram(this.method, List.of(method));

        IrMethod optimized = propagation.apply(program).methods().get(0);

        assertThat(optimized.blocks().get(0).instructions().stream()
                .anyMatch(IrInstruction.LoadLocal.class::isInstance)).isTrue();
    }

    @Test
    void rewritesCallIntrinsicArrayAndPairedLongOperands() {
        Value source = Value.int32(0);
        Value loaded = Value.int32(1);
        Value callResult = Value.int32(2);
        Value intrinsicResult = Value.int32(3);
        Value arrayResult = Value.int32(4);
        Value longLow = Value.int32(5);
        Value longHigh = Value.int32(6);
        Value shiftedLow = Value.int32(7);
        Value shiftedHigh = Value.int32(8);
        Value negatedLow = Value.int32(9);
        Value negatedHigh = Value.int32(10);
        Value compared = Value.int32(11);
        Value widenedLow = Value.int32(12);
        Value widenedHigh = Value.int32(13);
        Value narrowed = Value.int32(14);
        Value negatedInt = Value.int32(15);
        MethodRef callee = new MethodRef("demo/Program", "callee", "(I)I");
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(source, 7),
                new IrInstruction.StoreLocal(0, source),
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Unary(negatedInt, UnaryOp.NEGATE, loaded),
                new IrInstruction.Call(Optional.of(callResult), callee, List.of(loaded)),
                new IrInstruction.IntrinsicCall(Optional.of(intrinsicResult), Intrinsic.GPIO_DIGITAL_READ,
                        Optional.of(loaded), List.of(loaded), List.of()),
                new IrInstruction.ArrayLoad(arrayResult, ArrayElementType.INT, loaded, loaded),
                new IrInstruction.ArrayStore(ArrayElementType.INT, loaded, loaded, loaded),
                new IrInstruction.BoundsCheck(loaded, 4),
                new IrInstruction.LongBinary(longLow, longHigh, BinaryOp.ADD,
                        loaded, loaded, loaded, loaded),
                new IrInstruction.LongShift(shiftedLow, shiftedHigh, BinaryOp.SHIFT_LEFT,
                        loaded, loaded, loaded),
                new IrInstruction.LongNegate(negatedLow, negatedHigh, loaded, loaded),
                new IrInstruction.LongCompare(compared, loaded, loaded, loaded, loaded),
                new IrInstruction.IntToLong(widenedLow, widenedHigh, loaded),
                new IrInstruction.LongToInt(narrowed, loaded, loaded));

        List<IrInstruction> rewritten = propagation.apply(programOf(List.of(
                        new IrBasicBlock(0, instructions, new IrTerminator.Return(Optional.empty())))))
                .methods().get(0).blocks().get(0).instructions();

        assertThat(first(rewritten, IrInstruction.Unary.class).value()).isEqualTo(source);
        IrInstruction.Call call = first(rewritten, IrInstruction.Call.class);
        assertThat(call.arguments()).isEqualTo(List.of(source));
        IrInstruction.IntrinsicCall intrinsic = first(rewritten, IrInstruction.IntrinsicCall.class);
        assertThat(intrinsic.receiver()).isEqualTo(Optional.of(source));
        assertThat(intrinsic.arguments()).isEqualTo(List.of(source));
        IrInstruction.ArrayLoad arrayLoad = first(rewritten, IrInstruction.ArrayLoad.class);
        assertThat(arrayLoad.array()).isEqualTo(source);
        assertThat(arrayLoad.index()).isEqualTo(source);
        IrInstruction.ArrayStore arrayStore = first(rewritten, IrInstruction.ArrayStore.class);
        assertThat(arrayStore.array()).isEqualTo(source);
        assertThat(arrayStore.index()).isEqualTo(source);
        assertThat(arrayStore.value()).isEqualTo(source);
        assertThat(first(rewritten, IrInstruction.BoundsCheck.class).index()).isEqualTo(source);

        IrInstruction.LongBinary longBinary = first(rewritten, IrInstruction.LongBinary.class);
        assertThat(List.of(
                longBinary.leftLow(), longBinary.leftHigh(), longBinary.rightLow(), longBinary.rightHigh())).isEqualTo(List.of(source, source, source, source));
        IrInstruction.LongShift longShift = first(rewritten, IrInstruction.LongShift.class);
        assertThat(List.of(longShift.valueLow(), longShift.valueHigh(), longShift.shiftAmount())).isEqualTo(List.of(source, source, source));
        IrInstruction.LongNegate longNegate = first(rewritten, IrInstruction.LongNegate.class);
        assertThat(List.of(longNegate.valueLow(), longNegate.valueHigh())).isEqualTo(List.of(source, source));
        IrInstruction.LongCompare longCompare = first(rewritten, IrInstruction.LongCompare.class);
        assertThat(List.of(
                longCompare.leftLow(), longCompare.leftHigh(), longCompare.rightLow(), longCompare.rightHigh())).isEqualTo(List.of(source, source, source, source));
        assertThat(first(rewritten, IrInstruction.IntToLong.class).value()).isEqualTo(source);
        IrInstruction.LongToInt longToInt = first(rewritten, IrInstruction.LongToInt.class);
        assertThat(List.of(longToInt.valueLow(), longToInt.valueHigh())).isEqualTo(List.of(source, source));
    }

    @Test
    void unlocksConstantFoldingAndDeadBlockElimination() {
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

        IrMethod method = optimized.methods().get(0);
        assertThat(method.blocks().stream().map(IrBasicBlock::start).toList()).isEqualTo(List.of(0, 10));
        assertThat(method.blocks().get(0).terminator()).isInstanceOf(IrTerminator.Jump.class);
        IrTerminator.Jump jump = (IrTerminator.Jump) method.blocks().get(0).terminator();
        assertThat(jump.target()).isEqualTo(10);
        assertThat(method.blocks().get(0).instructions().get(3)).isInstanceOf(IrInstruction.Const.class);
        IrInstruction.Const foldedComparison = (IrInstruction.Const) method.blocks().get(0).instructions().get(3);
        assertThat(foldedComparison.value()).isEqualTo(1);
    }

    private IrProgram programOf(List<IrBasicBlock> blocks) {
        return new IrProgram(method, List.of(new IrMethod(method, 4, Value.int32Values(32), List.of(), blocks)));
    }

    private <T extends IrInstruction> T first(List<IrInstruction> instructions, Class<T> type) {
        return instructions.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
