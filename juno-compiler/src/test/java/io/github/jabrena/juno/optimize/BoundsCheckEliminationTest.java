package io.github.jabrena.juno.optimize;

import io.github.jabrena.juno.CompilerTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Which bounds checks the range analysis proves redundant in real loops, and which it must keep. */
class BoundsCheckEliminationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void removesTheCheckOfACountedLoopBoundedByTheArrayLength() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[16];
                for (int i = 0; i < 16; i++) values[i] = i;
                for (int i = 0; i < values.length; i++) total += values[i];
                """)).isZero();
    }

    @Test
    void removesTheCheckOfALoopCountingDown() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[8];
                for (int i = 7; i >= 0; i--) values[i] = i;
                total = values[0];
                """)).isZero();
    }

    @Test
    void removesTheCheckOfAMaskedOrRemainderIndex() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[16];
                int seed = Gpio.analogRead(0);
                values[seed & 15] = 1;
                values[(seed & 0x7FFF) % 16] = 2;
                """)).isZero();
    }

    @Test
    void keepsOneCheckForTheSameIndexReadThenWritten() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[16];
                int k = Gpio.analogRead(0);
                values[k] = values[k] + 1;
                """)).isEqualTo(1);
    }

    @Test
    void keepsTheCheckWhenTheLoopRunsOnePastTheEnd() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[16];
                for (int i = 0; i <= 16; i++) values[i] = i;
                """)).isEqualTo(1);
    }

    @Test
    void keepsTheCheckWhenTheIndexCanBeNegative() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[16];
                for (int i = -1; i < 16; i++) values[i] = i;
                """)).isEqualTo(1);
    }

    @Test
    void keepsTheCheckWhenTheBoundIsUnknown() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[16];
                int count = Gpio.analogRead(0);
                for (int i = 0; i < count; i++) values[i] = i;
                """)).isEqualTo(1);
    }

    @Test
    void keepsTheCheckWhenTheIndexArithmeticCanOverflow() throws Exception {
        assertThat(runtimeChecks("""
                int[] values = new int[16];
                int big = Gpio.analogRead(0) | 0x7FFFFFF0;
                values[big + 20] = 1;
                """)).isEqualTo(1);
    }

    /** Compiles {@code body} inside a {@code main} with an {@code int total} in scope; counts the runtime checks left. */
    private int runtimeChecks(String body) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.Gpio;
                public final class Ranges {
                    public static void main(String[] args) {
                        int total = 0;
                %s
                        Gpio.pinMode(total, Gpio.OUTPUT);
                    }
                }
                """.formatted(body.indent(8));
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Ranges", source);
        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Ranges").assembly();
        return assembly.split("blo \\.Lbcok", -1).length - 1;
    }
}
