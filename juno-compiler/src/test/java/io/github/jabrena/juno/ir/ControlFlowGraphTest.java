package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Control-flow graph of IR methods")
class ControlFlowGraphTest {
    private static final MethodRef METHOD = new MethodRef("demo/Program", "main", "()V");

    @Test
    @DisplayName("A diamond's join has both branch arms as predecessors")
    void shouldRecordBothBranchArmsAsPredecessorsOfTheJoin() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Branch(Value.int32(0), 10, 20)),
                block(10, new IrTerminator.Jump(30)),
                block(20, new IrTerminator.Jump(30)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        // When & Then
        assertThat(graph.predecessorsOf(30)).containsExactlyInAnyOrder(10, 20);
    }

    @Test
    @DisplayName("A fork's successors are its two branch targets")
    void shouldRecordTheTwoBranchTargetsAsSuccessorsOfTheFork() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Branch(Value.int32(0), 10, 20)),
                block(10, new IrTerminator.Jump(30)),
                block(20, new IrTerminator.Jump(30)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        // When & Then
        assertThat(graph.successorsOf(0)).containsExactly(10, 20);
    }

    @Test
    @DisplayName("A diamond's join is dominated only by its fork")
    void shouldDominateTheJoinOnlyThroughItsFork() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Branch(Value.int32(0), 10, 20)),
                block(10, new IrTerminator.Jump(30)),
                block(20, new IrTerminator.Jump(30)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        // When & Then
        assertThat(new boolean[] {
                graph.dominates(0, 30), graph.dominates(10, 30), graph.dominates(20, 30), graph.dominates(30, 30)})
                .containsExactly(true, false, false, true);
    }

    @Test
    @DisplayName("A diamond's reverse postorder runs from the entry to the exit")
    void shouldOrderADiamondsReversePostorderFromEntryToExit() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Branch(Value.int32(0), 10, 20)),
                block(10, new IrTerminator.Jump(30)),
                block(20, new IrTerminator.Jump(30)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        // When & Then
        assertThat(graph.reversePostorder()).startsWith(0).endsWith(30);
    }

    @Test
    @DisplayName("A loop's header has the back edge as a predecessor")
    void shouldRecordTheBackEdgeAsAPredecessorOfTheLoopHeader() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Jump(10)),
                block(10, new IrTerminator.Branch(Value.int32(0), 20, 30)),
                block(20, new IrTerminator.Jump(10)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        // When & Then
        assertThat(graph.predecessorsOf(10)).containsExactlyInAnyOrder(0, 20);
    }

    @Test
    @DisplayName("A loop header dominates its body and its exit")
    void shouldDominateTheLoopBodyAndExitFromTheHeader() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Jump(10)),
                block(10, new IrTerminator.Branch(Value.int32(0), 20, 30)),
                block(20, new IrTerminator.Jump(10)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        // When & Then
        assertThat(new boolean[] {graph.dominates(10, 20), graph.dominates(10, 30), graph.dominates(20, 30)})
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("A loop's reverse postorder visits the header before the body")
    void shouldOrderALoopsReversePostorderWithTheHeaderBeforeTheBody() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Jump(10)),
                block(10, new IrTerminator.Branch(Value.int32(0), 20, 30)),
                block(20, new IrTerminator.Jump(10)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        // When & Then
        assertThat(graph.reversePostorder()).containsExactly(0, 10, 30, 20);
    }

    @Test
    @DisplayName("An unreachable block is marked as unreachable")
    void shouldMarkAnUnreachableBlockAsUnreachable() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Return(Optional.empty())),
                block(10, new IrTerminator.Jump(0))));

        // When & Then
        assertThat(graph.isReachable(10)).isFalse();
    }

    @Test
    @DisplayName("An unreachable block dominates nothing")
    void shouldNotDominateAnUnreachableBlock() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Return(Optional.empty())),
                block(10, new IrTerminator.Jump(0))));

        // When & Then
        assertThat(graph.dominates(0, 10)).isFalse();
    }

    @Test
    @DisplayName("An unreachable block still counts as a predecessor of the entry")
    void shouldRecordAnUnreachablePredecessorOfTheEntry() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Return(Optional.empty())),
                block(10, new IrTerminator.Jump(0))));

        // When & Then
        assertThat(graph.predecessorsOf(0)).containsExactly(10);
    }

    @Test
    @DisplayName("The reverse postorder leaves out an unreachable block")
    void shouldExcludeAnUnreachableBlockFromTheReversePostorder() {
        // Given
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Return(Optional.empty())),
                block(10, new IrTerminator.Jump(0))));

        // When & Then
        assertThat(graph.reversePostorder()).containsExactly(0);
    }

    private static IrMethod method(IrBasicBlock... blocks) {
        return new IrMethod(METHOD, 0, Value.int32Values(1), List.of(), List.of(blocks));
    }

    private static IrBasicBlock block(int start, IrTerminator terminator) {
        return new IrBasicBlock(start, List.of(), terminator);
    }
}
