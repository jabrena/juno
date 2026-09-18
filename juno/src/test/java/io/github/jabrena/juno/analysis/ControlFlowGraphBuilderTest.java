package io.github.jabrena.juno.analysis;

import io.github.jabrena.juno.CompileException;
import io.github.jabrena.juno.bytecode.Instruction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlFlowGraphBuilderTest {
    private final ControlFlowGraphBuilder builder = new ControlFlowGraphBuilder();

    @Test
    void straightLineCodeIsOneBlockEndingInReturn() {
        List<Instruction> instructions = List.of(
                new Instruction(0, 3, 0, 0),   // iconst_0
                new Instruction(1, 59, 0, 0),  // istore_0
                new Instruction(2, 177, 0, 0)); // return

        ControlFlowGraph cfg = builder.build("demo.Straight.main", instructions);

        assertEquals(1, cfg.blocks().size());
        assertEquals(0, cfg.entry().start());
        assertEquals(3, cfg.entry().instructions().size());
        assertInstanceOf(Terminator.Return.class, cfg.entry().terminator());
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

        assertEquals(4, cfg.blocks().size());
        BasicBlock header = cfg.blockAt(0).orElseThrow();
        Terminator.Branch branch = assertInstanceOf(Terminator.Branch.class, header.terminator());
        assertEquals(8, branch.trueTarget());
        assertEquals(4, branch.falseTarget());

        BasicBlock thenBlock = cfg.blockAt(4).orElseThrow();
        Terminator.Jump jump = assertInstanceOf(Terminator.Jump.class, thenBlock.terminator());
        assertEquals(9, jump.target());

        BasicBlock elseBlock = cfg.blockAt(8).orElseThrow();
        assertInstanceOf(Terminator.Fallthrough.class, elseBlock.terminator());

        BasicBlock merge = cfg.blockAt(9).orElseThrow();
        assertInstanceOf(Terminator.Return.class, merge.terminator());
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

        assertEquals(3, cfg.blocks().size());
        BasicBlock loopBody = cfg.blockAt(4).orElseThrow();
        Terminator.Jump backEdge = assertInstanceOf(Terminator.Jump.class, loopBody.terminator());
        assertEquals(0, backEdge.target());
        assertTrue(cfg.blockAt(0).isPresent());
    }

    @Test
    void floatReturnEndsABasicBlock() {
        List<Instruction> instructions = List.of(
                new Instruction(0, 11, 0, 0),  // fconst_0
                new Instruction(1, 174, 0, 0), // freturn
                new Instruction(2, 12, 0, 0),  // fconst_1
                new Instruction(3, 174, 0, 0)); // freturn

        ControlFlowGraph cfg = builder.build("demo.FloatReturns.pick", instructions);

        assertEquals(2, cfg.blocks().size());
        assertInstanceOf(Terminator.Return.class, cfg.blockAt(0).orElseThrow().terminator());
        assertInstanceOf(Terminator.Return.class, cfg.blockAt(2).orElseThrow().terminator());
    }

    @Test
    void invalidBranchTargetIsReported() {
        List<Instruction> instructions = List.of(
                new Instruction(0, 153, 99, 0), // ifeq -> offset 99, which does not exist
                new Instruction(3, 177, 0, 0));

        CompileException exception = assertThrows(CompileException.class,
                () -> builder.build("demo.Bad.main", instructions));

        assertTrue(exception.getMessage().contains("invalid branch target 99"));
    }
}
