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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Constant folding")
class ConstantFolderTest {
    private final ConstantFolder folder = new ConstantFolder();
    private final MethodRef method = new MethodRef("demo/Program", "main", "()V");

    @Test
    @DisplayName("Folds binary arithmetic over two constants")
    void shouldFoldBinaryArithmeticOverTwoConstants() {
        // Given
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value sum = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 2),
                new IrInstruction.Const(b, 3),
                new IrInstruction.Binary(sum, BinaryOp.ADD, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(sum)));

        // When
        IrMethod folded = folder.apply(program).methods().get(0);

        // Then
        var foldedSum = (IrInstruction.Const) folded.blocks().get(0).instructions().get(2);
        assertThat(foldedSum.value()).isEqualTo(5);
    }

    @Test
    @DisplayName("Keeps the folded sum in the same target value")
    void shouldKeepTheFoldedSumInTheSameTargetValue() {
        // Given
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value sum = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 2),
                new IrInstruction.Const(b, 3),
                new IrInstruction.Binary(sum, BinaryOp.ADD, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(sum)));

        // When
        IrMethod folded = folder.apply(program).methods().get(0);

        // Then
        var foldedSum = (IrInstruction.Const) folded.blocks().get(0).instructions().get(2);
        assertThat(foldedSum.target()).isEqualTo(sum);
    }

    @Test
    @DisplayName("Keeps all three instructions of the arithmetic block")
    void shouldKeepAllThreeInstructionsOfTheArithmeticBlock() {
        // Given
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value sum = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 2),
                new IrInstruction.Const(b, 3),
                new IrInstruction.Binary(sum, BinaryOp.ADD, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(sum)));

        // When
        IrMethod folded = folder.apply(program).methods().get(0);

        // Then
        assertThat(folded.blocks().get(0).instructions()).hasSize(3);
    }

    @Test
    @DisplayName("Does not fold division by zero")
    void shouldNotFoldDivisionByZero() {
        // Given
        Value a = Value.int32(0);
        Value zero = Value.int32(1);
        Value quotient = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 10),
                new IrInstruction.Const(zero, 0),
                new IrInstruction.Binary(quotient, BinaryOp.DIVIDE, a, zero));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(quotient)));

        // When
        IrMethod folded = folder.apply(program).methods().get(0);

        // Then
        IrInstruction last = folded.blocks().get(0).instructions().get(2);
        assertThat(last).isInstanceOf(IrInstruction.Binary.class);
    }

    @Test
    @DisplayName("Folds unary negation over a constant")
    void shouldFoldUnaryNegationOverAConstant() {
        // Given
        Value a = Value.int32(0);
        Value negated = Value.int32(1);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 7),
                new IrInstruction.Unary(negated, UnaryOp.NEGATE, a));
        IrProgram program = programOf(instructions, new IrTerminator.Return(Optional.of(negated)));

        // When
        IrMethod folded = folder.apply(program).methods().get(0);

        // Then
        var foldedNegate = (IrInstruction.Const) folded.blocks().get(0).instructions().get(1);
        assertThat(foldedNegate.value()).isEqualTo(-7);
    }

    @Test
    @DisplayName("Folds a compare over two constants")
    void shouldFoldACompareOverTwoConstants() {
        // Given
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value condition = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 5),
                new IrInstruction.Const(b, 5),
                new IrInstruction.Compare(condition, Condition.EQUAL, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Branch(condition, 10, 20));

        // When
        IrBasicBlock block = folder.apply(program).methods().get(0).blocks().get(0);

        // Then
        var foldedCompare = (IrInstruction.Const) block.instructions().get(2);
        assertThat(foldedCompare.value()).isEqualTo(1);
    }

    @Test
    @DisplayName("Turns a branch on a folded true condition into a jump")
    void shouldTurnABranchOnAFoldedTrueConditionIntoAJump() {
        // Given
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value condition = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 5),
                new IrInstruction.Const(b, 5),
                new IrInstruction.Compare(condition, Condition.EQUAL, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Branch(condition, 10, 20));

        // When
        IrBasicBlock block = folder.apply(program).methods().get(0).blocks().get(0);

        // Then
        assertThat(block.terminator()).isInstanceOf(IrTerminator.Jump.class);
    }

    @Test
    @DisplayName("Jumps to the true target because five equals five")
    void shouldJumpToTheTrueTargetBecauseFiveEqualsFive() {
        // Given
        Value a = Value.int32(0);
        Value b = Value.int32(1);
        Value condition = Value.int32(2);
        List<IrInstruction> instructions = List.of(
                new IrInstruction.Const(a, 5),
                new IrInstruction.Const(b, 5),
                new IrInstruction.Compare(condition, Condition.EQUAL, a, b));
        IrProgram program = programOf(instructions, new IrTerminator.Branch(condition, 10, 20));

        // When
        IrBasicBlock block = folder.apply(program).methods().get(0).blocks().get(0);

        // Then
        var jump = (IrTerminator.Jump) block.terminator();
        assertThat(jump.target()).as("condition folded to true (5 == 5), so the branch must become a jump to trueTarget")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("Leaves a value loaded from a local unfolded")
    void shouldLeaveAValueLoadedFromALocalUnfolded() {
        // Given
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

        // When
        IrMethod folded = folder.apply(program).methods().get(0);

        // Then
        IrInstruction last = folded.blocks().get(0).instructions().get(2);
        assertThat(last).isInstanceOf(IrInstruction.Binary.class);
    }

    @Test
    @DisplayName("Leaves non-constant terminators untouched")
    void shouldLeaveNonConstantTerminatorsUntouched() {
        // Given
        List<IrInstruction> instructions = List.of();
        IrTerminator returnTerminator = new IrTerminator.Return(Optional.empty());
        IrProgram program = programOf(instructions, returnTerminator);

        // When
        IrMethod folded = folder.apply(program).methods().get(0);

        // Then
        assertThat(folded.blocks().get(0).terminator().getClass()).isSameAs(returnTerminator.getClass());
    }

    @Test
    @DisplayName("Folds a constant defined in an earlier block")
    void shouldFoldAConstantDefinedInAnEarlierBlock() {
        // Given
        IrBasicBlock entry = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(Value.int32(0), 6)), new IrTerminator.Jump(10));
        IrBasicBlock test = new IrBasicBlock(10, List.of(
                new IrInstruction.Const(Value.int32(1), 7),
                new IrInstruction.Binary(Value.int32(2), BinaryOp.MULTIPLY, Value.int32(0), Value.int32(1)),
                new IrInstruction.Compare(Value.int32(3), Condition.EQUAL, Value.int32(2), Value.int32(2))),
                new IrTerminator.Branch(Value.int32(3), 20, 30));
        IrBasicBlock yes = new IrBasicBlock(20, List.of(), new IrTerminator.Return(Optional.of(Value.int32(2))));
        IrBasicBlock no = new IrBasicBlock(30, List.of(), new IrTerminator.Return(Optional.empty()));
        IrProgram program = new IrProgram(method, List.of(new IrMethod(method, 1, Value.int32Values(8), List.of(),
                List.of(entry, test, yes, no))));

        // When
        IrBasicBlock folded = new ConstantFolder().apply(program).methods().getFirst().blocks().get(1);

        // Then
        assertThat(folded.instructions().get(1)).isEqualTo(new IrInstruction.Const(Value.int32(2), 42));
    }

    @Test
    @DisplayName("Turns the branch decided by the folded condition into a jump")
    void shouldTurnTheBranchDecidedByTheFoldedConditionIntoAJump() {
        // Given
        IrBasicBlock entry = new IrBasicBlock(0, List.of(
                new IrInstruction.Const(Value.int32(0), 6)), new IrTerminator.Jump(10));
        IrBasicBlock test = new IrBasicBlock(10, List.of(
                new IrInstruction.Const(Value.int32(1), 7),
                new IrInstruction.Binary(Value.int32(2), BinaryOp.MULTIPLY, Value.int32(0), Value.int32(1)),
                new IrInstruction.Compare(Value.int32(3), Condition.EQUAL, Value.int32(2), Value.int32(2))),
                new IrTerminator.Branch(Value.int32(3), 20, 30));
        IrBasicBlock yes = new IrBasicBlock(20, List.of(), new IrTerminator.Return(Optional.of(Value.int32(2))));
        IrBasicBlock no = new IrBasicBlock(30, List.of(), new IrTerminator.Return(Optional.empty()));
        IrProgram program = new IrProgram(method, List.of(new IrMethod(method, 1, Value.int32Values(8), List.of(),
                List.of(entry, test, yes, no))));

        // When
        IrBasicBlock folded = new ConstantFolder().apply(program).methods().getFirst().blocks().get(1);

        // Then
        assertThat(folded.terminator()).isEqualTo(new IrTerminator.Jump(20));
    }

    private IrProgram programOf(List<IrInstruction> instructions, IrTerminator terminator) {
        IrBasicBlock block = new IrBasicBlock(0, instructions, terminator);
        IrMethod irMethod = new IrMethod(method, 1, Value.int32Values(8), List.of(), List.of(block));
        return new IrProgram(method, List.of(irMethod));
    }
}
