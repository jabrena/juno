package io.github.jabrena.juno.optimize;


import io.github.jabrena.juno.CompilerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/** Which bounds checks the range analysis proves redundant in real loops, and which it must keep. */
@DisplayName("Bounds check elimination in real loops")
class BoundsCheckEliminationTest {
    @TempDir
    Path temporaryDirectory;

    static Stream<Arguments> boundsCases() {
        return Stream.of(
                arguments("a counted loop bounded by the array length removes its check", 0, """
                        int[] values = new int[16];
                        for (int i = 0; i < 16; i++) values[i] = i;
                        for (int i = 0; i < values.length; i++) total += values[i];
                        """),
                arguments("a loop counting down removes its check", 0, """
                        int[] values = new int[8];
                        for (int i = 7; i >= 0; i--) values[i] = i;
                        total = values[0];
                        """),
                arguments("a masked or remainder index removes its check", 0, """
                        int[] values = new int[16];
                        int seed = MCU.getGpio().analogRead(0);
                        values[seed & 15] = 1;
                        values[(seed & 0x7FFF) % 16] = 2;
                        """),
                arguments("the same index read then written keeps one check", 1, """
                        int[] values = new int[16];
                        int k = MCU.getGpio().analogRead(0);
                        values[k] = values[k] + 1;
                        """),
                arguments("a loop running one past the end keeps its check", 1, """
                        int[] values = new int[16];
                        for (int i = 0; i <= 16; i++) values[i] = i;
                        """),
                arguments("a possibly negative index keeps its check", 1, """
                        int[] values = new int[16];
                        for (int i = -1; i < 16; i++) values[i] = i;
                        """),
                arguments("an unknown loop bound keeps its check", 1, """
                        int[] values = new int[16];
                        int count = MCU.getGpio().analogRead(0);
                        for (int i = 0; i < count; i++) values[i] = i;
                        """),
                arguments("index arithmetic that can overflow keeps its check", 1, """
                        int[] values = new int[16];
                        int big = MCU.getGpio().analogRead(0) | 0x7FFFFFF0;
                        values[big + 20] = 1;
                        """));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundsCases")
    @DisplayName("Counts the bounds checks left after range analysis")
    void shouldCountTheBoundsChecksLeftAfterRangeAnalysis(String description, int expectedChecks, String body)
            throws Exception {
        // When
        int checks = runtimeChecks(body);

        // Then
        assertThat(checks).isEqualTo(expectedChecks);
    }

    /** Compiles {@code body} inside a {@code main} with an {@code int total} in scope; counts the runtime checks left. */
    private int runtimeChecks(String body) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.hardware.MCU;
                import io.github.jabrena.juno.api.io.gpio.Gpio;
                public final class Ranges {
                    public static void main(String[] args) {
                        int total = 0;
                %s
                        MCU.getGpio().pinMode(total, Gpio.getOutput());
                    }
                }
                """.formatted(body.indent(8));
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Ranges", source);
        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Ranges").assembly();
        return assembly.split("blo \\.Lbcok", -1).length - 1;
    }
}
