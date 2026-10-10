package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlFlowGraphBuilderTest {
    private final ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder();

    @Test
    void straightLineCodeIsOneBlockEndingInReturn() {
        List<Instruction> instructions = List.of(
                new Instruction(0, 3, 0, 0),   // iconst_0
                new Instruction(1, 59, 0, 0),  // istore_0
                new Instruction(2, 177, 0, 0)); // return

        ControlFlowGraph cfg = builder.build("demo.Straight.main", instructions);

        assertThat(cfg.blocks().size()).isEqualTo(1);
        assertThat(cfg.entry().start()).isEqualTo(0);
        assertThat(cfg.entry().instructions().size()).isEqualTo(3);
        assertThat(cfg.entry().terminator()).isInstanceOf(Terminator.Return.class);
    }

    @Test
    void ifElseSplitsIntoThreeBlocksJoinedAtTheMerge() {
        // if (arg == 0) goto 8; iconst_1; goto 9; [8] iconst_2; [9] return
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),   // iload_0
                new Instruction(1, 153, 7, 0),  // ifeq -> offset 8
                new Instruction(4, 4, 0, 0),    // iconst_1
                new Instruction(5, 167, 4, 0),  // goto -> offset 9
                new Instruction(8, 5, 0, 0),    // iconst_2
                new Instruction(9, 177, 0, 0)); // return

        ControlFlowGraph cfg = builder.build("demo.IfElse.main", instructions);

        assertThat(cfg.blocks().size()).isEqualTo(4);
        BasicBlock header = cfg.blockAt(0).orElseThrow();
        assertThat(header.terminator()).isInstanceOf(Terminator.Branch.class);
        Terminator.Branch branch = (Terminator.Branch) header.terminator();
        assertThat(branch.trueTarget()).isEqualTo(8);
        assertThat(branch.falseTarget()).isEqualTo(4);

        BasicBlock thenBlock = cfg.blockAt(4).orElseThrow();
        assertThat(thenBlock.terminator()).isInstanceOf(Terminator.Jump.class);
        Terminator.Jump jump = (Terminator.Jump) thenBlock.terminator();
        assertThat(jump.target()).isEqualTo(9);

        BasicBlock elseBlock = cfg.blockAt(8).orElseThrow();
        assertThat(elseBlock.terminator()).isInstanceOf(Terminator.Fallthrough.class);

        BasicBlock merge = cfg.blockAt(9).orElseThrow();
        assertThat(merge.terminator()).isInstanceOf(Terminator.Return.class);
    }

    @Test
    void backwardGotoFormsALoop() {
        // [0] iload_0; [1] ifeq -> 8; [4] iinc; [7] goto -> 0; [8] return
        List<Instruction> instructions = List.of(
                new Instruction(0, 26, 0, 0),
                new Instruction(1, 153, 7, 0),   // ifeq -> 8
                new Instruction(4, 132, 0, 1),   // iinc
                new Instruction(7, 167, -7, 0),  // goto -> 0
                new Instruction(8, 177, 0, 0));

        ControlFlowGraph cfg = builder.build("demo.Loop.main", instructions);

        assertThat(cfg.blocks().size()).isEqualTo(3);
        BasicBlock loopBody = cfg.blockAt(4).orElseThrow();
        assertThat(loopBody.terminator()).isInstanceOf(Terminator.Jump.class);
        Terminator.Jump backEdge = (Terminator.Jump) loopBody.terminator();
        assertThat(backEdge.target()).isEqualTo(0);
        assertThat(cfg.blockAt(0).isPresent()).isTrue();
    }

    @Test
    void floatReturnEndsABasicBlock() {
        List<Instruction> instructions = List.of(
                new Instruction(0, 11, 0, 0),  // fconst_0
                new Instruction(1, 174, 0, 0), // freturn
                new Instruction(2, 12, 0, 0),  // fconst_1
                new Instruction(3, 174, 0, 0)); // freturn

        ControlFlowGraph cfg = builder.build("demo.FloatReturns.pick", instructions);

        assertThat(cfg.blocks().size()).isEqualTo(2);
        assertThat(cfg.blockAt(0).orElseThrow().terminator()).isInstanceOf(Terminator.Return.class);
        assertThat(cfg.blockAt(2).orElseThrow().terminator()).isInstanceOf(Terminator.Return.class);
    }

    @Test
    void invalidBranchTargetIsReported() {
        List<Instruction> instructions = List.of(
                new Instruction(0, 153, 99, 0), // ifeq -> offset 99, which does not exist
                new Instruction(3, 177, 0, 0));

        assertThatThrownBy(() -> builder.build("demo.Bad.main", instructions))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("invalid branch target 99");
    }
}
