package io.github.jabrena.juno.ir;

import io.github.jabrena.juno.classfile.MethodRef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ControlFlowGraphTest {
    private static final MethodRef METHOD = new MethodRef("demo/Program", "main", "()V");

    @Test
    void aDiamondsJoinIsDominatedOnlyByItsFork() {
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Branch(Value.int32(0), 10, 20)),
                block(10, new IrTerminator.Jump(30)),
                block(20, new IrTerminator.Jump(30)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        assertThat(graph.predecessorsOf(30)).containsExactlyInAnyOrder(10, 20);
        assertThat(graph.successorsOf(0)).containsExactly(10, 20);
        assertThat(graph.reversePostorder().getFirst()).isZero();
        assertThat(graph.reversePostorder().getLast()).isEqualTo(30);
        assertThat(graph.dominates(0, 30)).isTrue();
        assertThat(graph.dominates(10, 30)).isFalse();
        assertThat(graph.dominates(20, 30)).isFalse();
        assertThat(graph.dominates(30, 30)).isTrue();
    }

    @Test
    void aLoopHeaderDominatesItsBodyAndExit() {
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Jump(10)),
                block(10, new IrTerminator.Branch(Value.int32(0), 20, 30)),
                block(20, new IrTerminator.Jump(10)),
                block(30, new IrTerminator.Return(Optional.empty()))));

        assertThat(graph.predecessorsOf(10)).containsExactlyInAnyOrder(0, 20);
        assertThat(graph.dominates(10, 20)).isTrue();
        assertThat(graph.dominates(10, 30)).isTrue();
        assertThat(graph.dominates(20, 30)).isFalse();
        assertThat(graph.reversePostorder()).containsExactly(0, 10, 30, 20);
    }

    @Test
    void anUnreachableBlockIsReachedByNothingAndDominatedByNothing() {
        ControlFlowGraph graph = ControlFlowGraph.of(method(
                block(0, new IrTerminator.Return(Optional.empty())),
                block(10, new IrTerminator.Jump(0))));

        assertThat(graph.isReachable(10)).isFalse();
        assertThat(graph.dominates(0, 10)).isFalse();
        assertThat(graph.predecessorsOf(0)).containsExactly(10);
        assertThat(graph.reversePostorder()).containsExactly(0);
    }

    private static IrMethod method(IrBasicBlock... blocks) {
        return new IrMethod(METHOD, 0, Value.int32Values(1), List.of(), List.of(blocks));
    }

    private static IrBasicBlock block(int start, IrTerminator terminator) {
        return new IrBasicBlock(start, List.of(), terminator);
    }
}
