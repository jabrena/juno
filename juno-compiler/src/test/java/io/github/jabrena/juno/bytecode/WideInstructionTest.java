package io.github.jabrena.juno.bytecode;

import io.github.jabrena.juno.CompilationResult;
import io.github.jabrena.juno.CompilerTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WideInstructionTest {
    @TempDir
    Path temporaryDirectory;

    /** javac compiles {@code local += 1000} to {@code wide iinc}, whose increment takes 16 bits. */
    @Test
    void aLargeIncrementCompiles() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.Delay;
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

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.WideIncrement");

        assertThat(result.assembly()).contains("1000");
    }
}
