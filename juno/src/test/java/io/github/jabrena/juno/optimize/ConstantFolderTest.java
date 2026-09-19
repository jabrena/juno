package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.classfile.MethodRef;
import io.github.jabrena.juno.ir.BinaryOp;
import io.github.jabrena.juno.ir.Condition;
import io.github.jabrena.juno.ir.IrBasicBlock;
import io.github.jabrena.juno.ir.IrInstruction;
import io.github.jabrena.juno.ir.IrMethod;
import io.github.jabrena.juno.ir.IrProgram;
import io.github.jabrena.juno.ir.IrTerminator;
import io.github.jabrena.juno.ir.UnaryOp;
import io.github.jabrena.juno.ir.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ConstantFolderTest {
    private final ConstantFolder folder = new ConstantFolder();
    private final MethodRef method = new MethodRef("demo/Program", "main", "()V");

    @Test
    void foldsBinaryArithmeticOverTwoConstants() {
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value sum = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 2),
                new IrInstruction.Const(b, 3),
                new IrInstruction.Binary(sum, BinaryOp.ADD, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(sum)));

        IrMethod folded = folder.apply(program).methods().get(0);
        List<IrInstruction> foldedInstructions = folded.blocks().get(0).instructions();

        assertThat(foldedInstructions.size()).isEqualTo(3);
        assertThat(foldedInstructions.get(2)).isInstanceOf(IrInstruction.Const.class);
        var foldedSum = (IrInstruction.Const) foldedInstructions.get(2);
        assertThat(foldedSum.value()).isEqualTo(5);
        assertThat(foldedSum.target()).isEqualTo(sum);
    }

    @Test
    void doesNotFoldDivisionByZero() {
        Value a = Value.int32(0);
        Value zero = Value.int32(1);
        Value quotient = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 10),
                new IrInstruction.Const(zero, 0),
                new IrInstruction.Binary(quotient, BinaryOp.DIVIDE, a, zero));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(quotient)));

        IrMethod folded = folder.apply(program).methods().get(0);
        IrInstruction last = folded.blocks().get(0).instructions().get(2);

        assertThat(last).isInstanceOf(IrInstruction.Binary.class);
    }

    @Test
    void foldsUnaryNegationOverAConstant() {
        Value a = Value.int32(0);
        Value negated = Value.int32(1);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 7),
                new IrInstruction.Unary(negated, UnaryOp.NEGATE, a));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(negated)));

        IrMethod folded = folder.apply(program).methods().get(0);
        assertThat(folded.blocks().get(0).instructions().get(1)).isInstanceOf(IrInstruction.Const.class);
        var foldedNegate = (IrInstruction.Const) folded.blocks().get(0).instructions().get(1);
        assertThat(foldedNegate.value()).isEqualTo(-7);
    }

    @Test
    void foldsACompareOverTwoConstantsAndThenTheBranchIntoAJump() {
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value condition = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 5),
                new IrInstruction.Const(b, 5),
                new IrInstruction.Compare(condition, Condition.EQUAL, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Branch(condition, 10, 20));

        IrMethod folded = folder.apply(program).methods().get(0);
        IrBasicBlock block = folded.blocks().get(0);

        assertThat(block.instructions().get(2)).isInstanceOf(IrInstruction.Const.class);
        var foldedCompare = (IrInstruction.Const) block.instructions().get(2);
        assertThat(foldedCompare.value()).isEqualTo(1);
        assertThat(block.terminator()).isInstanceOf(IrTerminator.Jump.class);
        var jump = (IrTerminator.Jump) block.terminator();
        assertThat(jump.target()).as("condition folded to true (5 == 5), so the branch must become a jump to trueTarget").isEqualTo(10);
    }

    @Test
    void leavesAValueLoadedFromALocalUnfolded() {
        // LoadLocal never yields a known constant here: the slot may have been written by more
        // than one predecessor block (see BytecodeToIr), so folding through it would be unsound
        // without a separate copy-propagation pass.
        Value loaded = Value.int32(0);
        Value constant = Value.int32(1);
        Value sum = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.LoadLocal(loaded, 0),
                new IrInstruction.Const(constant, 1),
                new IrInstruction.Binary(sum, BinaryOp.ADD, loaded, constant));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(sum)));

        IrMethod folded = folder.apply(program).methods().get(0);
        IrInstruction last = folded.blocks().get(0).instructions().get(2);

        assertThat(last).isInstanceOf(IrInstruction.Binary.class);
    }

    @Test
    void leavesNonConstantTerminatorsUntouched() {
        List<IrInstruction> instructions = List.of();
        IrTerminator returnTerminator = new IrTerminator.Return(Optional.empty());
        IrProgram program = programOf(instructions, returnTerminator);

        IrMethod folded = folder.apply(program).methods().get(0);

        assertThat(folded.blocks().get(0).terminator().getClass()).isSameAs(returnTerminator.getClass());
    }

    private IrProgram programOf(List<IrInstruction> instructions, IrTerminator terminator) {
        IrBasicBlock block = new IrBasicBlock(0, instructions, terminator);
        IrMethod irMethod = new IrMethod(method, 1, Value.int32Values(8), List.of(), List.of(block));
        return new IrProgram(method, List.of(irMethod));
    }
}
