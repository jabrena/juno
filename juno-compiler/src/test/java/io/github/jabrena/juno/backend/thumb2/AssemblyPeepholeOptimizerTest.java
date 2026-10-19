package io.github.jabrena.juno.backend.thumb2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Thumb-2 assembly peephole optimizer")
class AssemblyPeepholeOptimizerTest {
    private final AssemblyPeepholeOptimizer assemblyPeepholeOptimizer = new AssemblyPeepholeOptimizer();

    @Test
    @DisplayName("Forwards resident stack words and removes redundant stack traffic")
    void shouldForwardResidentStackWordsAndRemoveRedundantTraffic() {
        // Given
        String assembly = """
                    movs r0, #7
                    str r0, [sp, #0]
                    ldr r0, [sp, #0]
                    str r0, [sp, #4]
                    str r0, [sp, #4]
                    ldr r1, [sp, #0]
                    str r1, [sp, #8]
                """;

        // When & Then
        assertThat(assemblyPeepholeOptimizer.optimize(assembly)).isEqualTo("""
                    movs r0, #7
                    str r0, [sp, #0]
                    str r0, [sp, #4]
                    mov r1, r0
                    str r1, [sp, #8]
                """);
    }

    @Test
    @DisplayName("Treats calls, instructions and control-flow boundaries as barriers")
    void shouldTreatCallsInstructionsAndControlFlowBoundariesAsBarriers() {
        // Given
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

        // When & Then
        assertThat(assemblyPeepholeOptimizer.optimize(assembly)).isEqualTo(assembly);
    }
}
