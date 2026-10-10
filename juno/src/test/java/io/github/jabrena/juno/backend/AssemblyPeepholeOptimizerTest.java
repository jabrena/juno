package io.github.jabrena.juno.backend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssemblyPeepholeOptimizerTest {
    @Test
    void forwardsResidentStackWordsAndRemovesRedundantTraffic() {
        String assembly = """
                    movs r0, #7
                    str r0, [sp, #0]
                    ldr r0, [sp, #0]
                    str r0, [sp, #4]
                    str r0, [sp, #4]
                    ldr r1, [sp, #0]
                    str r1, [sp, #8]
                """;

        assertThat(AssemblyPeepholeOptimizer.optimize(assembly)).isEqualTo("""
                    movs r0, #7
                    str r0, [sp, #0]
                    str r0, [sp, #4]
                    mov r1, r0
                    str r1, [sp, #8]
                """);
    }

    @Test
    void treatsCallsInstructionsAndControlFlowBoundariesAsBarriers() {
        String assembly = """
                    str r0, [sp, #0]
                    bl consume
                    ldr r0, [sp, #0]
                    str r0, [sp, #4]
                    adds r0, r0, #1
                    ldr r0, [sp, #4]
                    str r0, [sp, #8]
                .Ljoin:
                    ldr r0, [sp, #8]
                """;

        assertThat(AssemblyPeepholeOptimizer.optimize(assembly)).isEqualTo(assembly);
    }
}
