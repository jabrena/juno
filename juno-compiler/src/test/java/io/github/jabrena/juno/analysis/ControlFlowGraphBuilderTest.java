package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
@DisplayName("Control-flow graph builder")
class ControlFlowGraphBuilderTest {
    private final ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder();

    @Test
    @DisplayName("Straight-line code is one block")
    void shouldBuildOneBlockForStraightLineCode() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 3, 0, 0),   // iconst_0
                new Instruction(1, 59, 0, 0),  // istore_0
                new Instruction(2, 177, 0, 0)); // return

        // When
        ControlFlowGraph cfg = builder.build("demo.Straight.main", instructions);

        // Then
        assertThat(cfg.blocks()).hasSize(1);
    }

    @Test
    @DisplayName("The entry block keeps every instruction of straight-line code")
    void shouldKeepEveryInstructionInTheEntryBlock() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 3, 0, 0),   // iconst_0
                new Instruction(1, 59, 0, 0),  // istore_0
                new Instruction(2, 177, 0, 0)); // return

        // When
        ControlFlowGraph cfg = builder.build("demo.Straight.main", instructions);

        // Then
        assertThat(cfg.entry().instructions()).hasSize(3);
    }

    @Test
    @DisplayName("Straight-line code starts at offset zero and ends in a return")
    void shouldStartAtOffsetZeroAndEndInAReturn() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 3, 0, 0),   // iconst_0
                new Instruction(1, 59, 0, 0),  // istore_0
                new Instruction(2, 177, 0, 0)); // return

        // When
        ControlFlowGraph cfg = builder.build("demo.Straight.main", instructions);

        // Then
        assertThat(cfg.entry().start()).isEqualTo(0);
        assertThat(cfg.entry().terminator()).isInstanceOf(Terminator.Return.class);
    }

    @Test
    @DisplayName("An if-else splits into four blocks")
    void shouldSplitAnIfElseIntoFourBlocks() {
        // Given
        // if (arg == 0) goto 8; iconst_1; goto 9; [8] iconst_2; [9] return
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),   // iload_0
                new Instruction(1, 153, 7, 0),  // ifeq -> offset 8
                new Instruction(4, 4, 0, 0),    // iconst_1
                new Instruction(5, 167, 4, 0),  // goto -> offset 9
                new Instruction(8, 5, 0, 0),    // iconst_2
                new Instruction(9, 177, 0, 0)); // return

        // When
        ControlFlowGraph cfg = builder.build("demo.IfElse.main", instructions);

        // Then
        assertThat(cfg.blocks()).hasSize(4);
    }

    @Test
    @DisplayName("The if header branches to the else block or the then block")
    void shouldBranchFromTheHeaderToTheElseOrTheThenBlock() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),   // iload_0
                new Instruction(1, 153, 7, 0),  // ifeq -> offset 8
                new Instruction(4, 4, 0, 0),    // iconst_1
                new Instruction(5, 167, 4, 0),  // goto -> offset 9
                new Instruction(8, 5, 0, 0),    // iconst_2
                new Instruction(9, 177, 0, 0)); // return
        ControlFlowGraph cfg = builder.build("demo.IfElse.main", instructions);

        // When
        BasicBlock header = cfg.blockAt(0).orElseThrow();

        // Then
        assertThat(header.terminator()).isInstanceOf(Terminator.Branch.class);
        Terminator.Branch branch = (Terminator.Branch) header.terminator();
        assertThat(new int[] {branch.trueTarget(), branch.falseTarget()}).containsExactly(8, 4);
    }

    @Test
    @DisplayName("The then block jumps to the merge")
    void shouldJumpFromTheThenBlockToTheMerge() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),   // iload_0
                new Instruction(1, 153, 7, 0),  // ifeq -> offset 8
                new Instruction(4, 4, 0, 0),    // iconst_1
                new Instruction(5, 167, 4, 0),  // goto -> offset 9
                new Instruction(8, 5, 0, 0),    // iconst_2
                new Instruction(9, 177, 0, 0)); // return
        ControlFlowGraph cfg = builder.build("demo.IfElse.main", instructions);

        // When
        BasicBlock thenBlock = cfg.blockAt(4).orElseThrow();

        // Then
        assertThat(thenBlock.terminator()).isInstanceOf(Terminator.Jump.class);
        Terminator.Jump jump = (Terminator.Jump) thenBlock.terminator();
        assertThat(jump.target()).isEqualTo(9);
    }

    @Test
    @DisplayName("The else block falls through into the merge")
    void shouldFallThroughFromTheElseBlock() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),   // iload_0
                new Instruction(1, 153, 7, 0),  // ifeq -> offset 8
                new Instruction(4, 4, 0, 0),    // iconst_1
                new Instruction(5, 167, 4, 0),  // goto -> offset 9
                new Instruction(8, 5, 0, 0),    // iconst_2
                new Instruction(9, 177, 0, 0)); // return
        ControlFlowGraph cfg = builder.build("demo.IfElse.main", instructions);

        // When
        BasicBlock elseBlock = cfg.blockAt(8).orElseThrow();

        // Then
        assertThat(elseBlock.terminator()).isInstanceOf(Terminator.Fallthrough.class);
    }

    @Test
    @DisplayName("The merge block returns")
    void shouldReturnAtTheMerge() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),   // iload_0
                new Instruction(1, 153, 7, 0),  // ifeq -> offset 8
                new Instruction(4, 4, 0, 0),    // iconst_1
                new Instruction(5, 167, 4, 0),  // goto -> offset 9
                new Instruction(8, 5, 0, 0),    // iconst_2
                new Instruction(9, 177, 0, 0)); // return
        ControlFlowGraph cfg = builder.build("demo.IfElse.main", instructions);

        // When
        BasicBlock merge = cfg.blockAt(9).orElseThrow();

        // Then
        assertThat(merge.terminator()).isInstanceOf(Terminator.Return.class);
    }

    @Test
    @DisplayName("A backward goto forms a loop")
    void shouldFormALoopFromABackwardGoto() {
        // Given
        // [0] iload_0; [1] ifeq -> 8; [4] iinc; [7] goto -> 0; [8] return
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),
                new Instruction(1, 153, 7, 0),   // ifeq -> 8
                new Instruction(4, 132, 0, 1),   // iinc
                new Instruction(7, 167, -7, 0),  // goto -> 0
                new Instruction(8, 177, 0, 0));

        // When
        ControlFlowGraph cfg = builder.build("demo.Loop.main", instructions);

        // Then
        assertThat(cfg.blocks()).hasSize(3);
    }

    @Test
    @DisplayName("The loop body jumps back to the header")
    void shouldJumpBackToTheHeaderFromTheLoopBody() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),
                new Instruction(1, 153, 7, 0),   // ifeq -> 8
                new Instruction(4, 132, 0, 1),   // iinc
                new Instruction(7, 167, -7, 0),  // goto -> 0
                new Instruction(8, 177, 0, 0));
        ControlFlowGraph cfg = builder.build("demo.Loop.main", instructions);

        // When
        BasicBlock loopBody = cfg.blockAt(4).orElseThrow();

        // Then
        assertThat(loopBody.terminator()).isInstanceOf(Terminator.Jump.class);
        Terminator.Jump backEdge = (Terminator.Jump) loopBody.terminator();
        assertThat(backEdge.target()).isEqualTo(0);
    }

    @Test
    @DisplayName("A float return ends a basic block")
    void shouldSplitEachFloatReturnIntoItsOwnBlock() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 11, 0, 0),  // fconst_0
                new Instruction(1, 174, 0, 0), // freturn
                new Instruction(2, 12, 0, 0),  // fconst_1
                new Instruction(3, 174, 0, 0)); // freturn

        // When
        ControlFlowGraph cfg = builder.build("demo.FloatReturns.pick", instructions);

        // Then
        assertThat(cfg.blocks()).hasSize(2);
    }

    @Test
    @DisplayName("The first float block ends in a return")
    void shouldEndTheFirstFloatBlockWithAReturn() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 11, 0, 0),  // fconst_0
                new Instruction(1, 174, 0, 0), // freturn
                new Instruction(2, 12, 0, 0),  // fconst_1
                new Instruction(3, 174, 0, 0)); // freturn
        ControlFlowGraph cfg = builder.build("demo.FloatReturns.pick", instructions);

        // When & Then
        assertThat(cfg.blockAt(0).orElseThrow().terminator()).isInstanceOf(Terminator.Return.class);
    }

    @Test
    @DisplayName("The second float block ends in a return")
    void shouldEndTheSecondFloatBlockWithAReturn() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 11, 0, 0),  // fconst_0
                new Instruction(1, 174, 0, 0), // freturn
                new Instruction(2, 12, 0, 0),  // fconst_1
                new Instruction(3, 174, 0, 0)); // freturn
        ControlFlowGraph cfg = builder.build("demo.FloatReturns.pick", instructions);

        // When & Then
        assertThat(cfg.blockAt(2).orElseThrow().terminator()).isInstanceOf(Terminator.Return.class);
    }

    @Test
    @DisplayName("An invalid branch target is reported")
    void shouldReportAnInvalidBranchTarget() {
        // Given
        List<Instruction> instructions = List.of(
                new Instruction(0, 153, 99, 0), // ifeq -> offset 99, which does not exist
                new Instruction(3, 177, 0, 0));

        // When & Then
        assertThatThrownBy(() -> builder.build("demo.Bad.main", instructions))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("invalid branch target 99");
    }
}
