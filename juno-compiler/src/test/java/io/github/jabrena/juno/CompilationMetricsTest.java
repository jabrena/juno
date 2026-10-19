package io.github.jabrena.juno;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Compilation metrics of generated assembly")
class CompilationMetricsTest {
    @Test
    @DisplayName("Measures instructions, sources and fixed frames of generated assembly")
    void shouldMeasureInstructionsSourcesAndFixedFrames() {
        // Given
        String assembly = """
                @ generated fixture
                    .text
                    .type first, %function
                first:
                    push {r4-r11, lr}
                    movs r12, #32
                    sub sp, sp, r12
                    ldr r0, [sp, #0]
                    str r0, [sp, #4]
                    beq .Ldone
                    bl juno_alloc
                .Ldone:
                    bx lr
                    .type second, %function
                second:
                    push {r4-r11, lr}
                    movw r12, #4464
                    movt r12, #1
                    sub sp, sp, r12
                    cbz r0, .Lreturn
                .Lreturn:
                    bx lr
                """;

        // When
        CompilationMetrics metrics = CompilationMetrics.from(assembly, "// ñ\n");

        // Then
        assertThat(metrics).isEqualTo(new CompilationMetrics(
                assembly.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, 6,
                2, 14, 1, 1, 4, 1, 1, 70_036, 70_104));
    }

    @Test
    @DisplayName("Measures empty generated sources as all zeros")
    void shouldMeasureEmptyGeneratedSourcesAsAllZeros() {
        // When
        CompilationMetrics metrics = CompilationMetrics.from("", "");

        // Then
        assertThat(metrics).isEqualTo(new CompilationMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
    }
}
