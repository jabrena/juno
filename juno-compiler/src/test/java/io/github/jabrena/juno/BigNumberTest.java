package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The {@code java.math} subset ({@code BigInteger}, {@code BigDecimal}, {@code MathContext}) lowered onto the shim. */
@DisplayName("Bounded java.math subset")
class BigNumberTest {
    @TempDir
    Path temporaryDirectory;

    private static final String BIG_CALLS = """
            public static void main() {
                java.math.BigInteger a = new java.math.BigInteger("123456789012345678901234567890");
                java.math.BigInteger b = java.math.BigInteger.valueOf(7).add(java.math.BigInteger.TEN);
                java.math.BigInteger c = a.multiply(b).divide(java.math.BigInteger.TWO).pow(3);
                java.math.MathContext context = new java.math.MathContext(30, java.math.RoundingMode.HALF_EVEN);
                java.math.BigDecimal x = new java.math.BigDecimal("1.5").sqrt(context);
                java.math.BigDecimal y = x.divide(java.math.BigDecimal.valueOf(3), 20, java.math.RoundingMode.DOWN);
                String text = y.setScale(4, java.math.RoundingMode.UP).toPlainString() + c.toString();
            }
            """;

    @Test
    @DisplayName("Lowers each call to its shim function")
    void shouldLowerEachCallToItsShimFunction() throws Exception {
        // Given
        CompilationResult result = compile(BIG_CALLS);

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_big_integer_parse", "bl juno_big_integer_value_of", "bl juno_big_integer_constant",
                "bl juno_big_integer_add", "bl juno_big_integer_multiply", "bl juno_big_integer_divide",
                "bl juno_big_integer_pow", "bl juno_big_math_context_new", "bl juno_big_decimal_parse",
                "bl juno_big_decimal_sqrt", "bl juno_big_decimal_value_of", "bl juno_big_decimal_divide_scale",
                "bl juno_big_decimal_set_scale", "bl juno_big_decimal_to_plain_string",
                "bl juno_big_integer_to_string");
    }

    @Test
    @DisplayName("The runtime shim includes the big-number helpers that the calls use")
    void shouldIncludeTheBigNumberHelpersTheCallsUse() throws Exception {
        // Given
        CompilationResult result = compile(BIG_CALLS);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "#include <stdlib.h>", "juno_big_integer_add", "juno_big_decimal_sqrt", "juno_big_prepare",
                "static const char* juno_big_pointer_chars");
    }

    @Test
    @DisplayName("The runtime shim omits the double conversion when it is not used")
    void shouldOmitTheDoubleConversionWhenItIsNotUsed() throws Exception {
        // Given
        CompilationResult result = compile(BIG_CALLS);

        // When & Then
        assertThat(result.runtimeShim()).doesNotContain("juno_big_decimal_value_of_double");
    }

    @Test
    @DisplayName("Exceptions are raised through the throwable runtime")
    void shouldRaiseExceptionsThroughTheThrowableRuntime() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    try {
                        java.math.BigInteger.ONE.divide(java.math.BigInteger.ZERO).signum();
                    } catch (ArithmeticException e) {
                        return;
                    }
                }
                """);

        // When & Then
        assertThat(result.assembly()).contains("bl juno_big_integer_divide", "bl juno_throw_pending");
    }

    @Test
    @DisplayName("The shim declares ArithmeticException and the raise helper for thrown exceptions")
    void shouldDeclareArithmeticExceptionAndTheRaiseHelper() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    try {
                        java.math.BigInteger.ONE.divide(java.math.BigInteger.ZERO).signum();
                    } catch (ArithmeticException e) {
                        return;
                    }
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).contains("java.lang.ArithmeticException", "juno_throw_raise");
    }

    @Test
    @DisplayName("Double conversion pulls in the string helpers only when used")
    void shouldPullInTheStringHelpersForDoubleConversionOnlyWhenUsed() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    java.math.BigDecimal value = java.math.BigDecimal.valueOf(0.25);
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).contains("juno_big_decimal_value_of_double", "juno_format_double");
    }

    @Test
    @DisplayName("A program without big numbers gets no big-number runtime")
    void shouldGiveAProgramWithoutBigNumbersNoBigNumberRuntime() throws Exception {
        // Given
        CompilationResult result = compile("""
                public static void main() {
                    int total = 1 + 2;
                }
                """);

        // When & Then
        assertThat(result.runtimeShim()).doesNotContain("juno_big_");
    }

    @Test
    @DisplayName("Rejects unsupported methods with a helpful message")
    void shouldRejectUnsupportedMethodsWithAHelpfulMessage() throws Exception {
        // Given
        String source = """
                package demo;
                public final class Tasks {
                    public static void main() {
                        java.math.BigInteger.TEN.isProbablePrime(10);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("java.math subset")
                .hasMessageContaining("isProbablePrime");
    }

    @ParameterizedTest(name = "{0} runtime compiles as C++")
    @ValueSource(strings = {"ArduinoOneR4WiFiBoard", "ArduinoOneQBoard"})
    @DisplayName("The generated big-number runtime compiles for both boards")
    void shouldCompileTheGeneratedRuntimeForBothBoards(String board) throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                @Board(io.github.jabrena.juno.annotations.%s.class)
                public final class Tasks {
                %s
                }
                """.formatted(board, """
                static java.math.BigDecimal scale(java.math.BigDecimal value, java.math.MathContext context) {
                    return value.round(context).add(java.math.BigDecimal.valueOf(0.5));
                }
                public static void main() {
                    java.math.BigDecimal pi = scale(new java.math.BigDecimal("3.14159"), java.math.MathContext.DECIMAL32);
                    java.math.BigInteger big = java.math.BigInteger.valueOf(12).shiftLeft(80).sqrt().gcd(java.math.BigInteger.TEN);
                    double d = pi.doubleValue() + big.doubleValue();
                }
                """);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
        Path shim = temporaryDirectory.resolve(board + "-shim.cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        // When
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();

        // Then
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
    }

    private static String availableCppCompiler() {
        for (String candidate : new String[]{"clang++", "g++"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) return candidate;
            } catch (IOException | InterruptedException ignored) {
                // Try the next compiler.
            }
        }
        return null;
    }

    private CompilationResult compile(String members) throws Exception {
        String source = """
                package demo;
                public final class Tasks {
                %s
                }
                """.formatted(members);
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
    }
}
