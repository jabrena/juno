package io.github.jabrena.juno.bytecode;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Wide bytecode instructions")
class WideInstructionTest {
    @TempDir
    Path temporaryDirectory;

    /** javac compiles {@code local += 1000} to {@code wide iinc}, whose increment takes 16 bits. */
    @Test
    @DisplayName("Compiles a large increment that javac emits as wide iinc")
    void shouldCompileALargeIncrement() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.util.Delay;
                public final class WideIncrement {
                    public static void main(String[] args) {
                        int next = 0;
                        for (int i = 0; i < 3; i++) {
                            next += 1000;
                            Delay.millis(next);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.WideIncrement", source);

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WideIncrement");

        // Then
        assertThat(result.assembly()).contains("1000");
    }
}
