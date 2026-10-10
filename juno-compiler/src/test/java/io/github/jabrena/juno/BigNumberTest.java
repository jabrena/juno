package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The {@code java.math} subset ({@code BigInteger}, {@code BigDecimal}, {@code MathContext}) lowered onto the shim. */
class BigNumberTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void lowersEachCallToItsShimFunction() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    java.math.BigInteger a = new java.math.BigInteger("123456789012345678901234567890");
                    java.math.BigInteger b = java.math.BigInteger.valueOf(7).add(java.math.BigInteger.TEN);
                    java.math.BigInteger c = a.multiply(b).divide(java.math.BigInteger.TWO).pow(3);
                    java.math.MathContext context = new java.math.MathContext(30, java.math.RoundingMode.HALF_EVEN);
                    java.math.BigDecimal x = new java.math.BigDecimal("1.5").sqrt(context);
                    java.math.BigDecimal y = x.divide(java.math.BigDecimal.valueOf(3), 20, java.math.RoundingMode.DOWN);
                    String text = y.setScale(4, java.math.RoundingMode.UP).toPlainString() + c.toString();
                }
                """);

        assertThat(result.assembly()).contains(
                "bl juno_big_integer_parse", "bl juno_big_integer_value_of", "bl juno_big_integer_constant",
                "bl juno_big_integer_add", "bl juno_big_integer_multiply", "bl juno_big_integer_divide",
                "bl juno_big_integer_pow", "bl juno_big_math_context_new", "bl juno_big_decimal_parse",
                "bl juno_big_decimal_sqrt", "bl juno_big_decimal_value_of", "bl juno_big_decimal_divide_scale",
                "bl juno_big_decimal_set_scale", "bl juno_big_decimal_to_plain_string",
                "bl juno_big_integer_to_string");
        assertThat(result.runtimeShim()).contains(
                "#include <stdlib.h>", "juno_big_integer_add", "juno_big_decimal_sqrt", "juno_big_prepare",
                "static const char* juno_big_pointer_chars");
        assertThat(result.runtimeShim()).doesNotContain("juno_big_decimal_value_of_double");
    }

    @Test
    void exceptionsAreRaisedThroughTheThrowableRuntime() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    try {
                        java.math.BigInteger.ONE.divide(java.math.BigInteger.ZERO).signum();
                    } catch (ArithmeticException e) {
                        return;
                    }
                }
                """);

        assertThat(result.assembly()).contains("bl juno_big_integer_divide", "bl juno_throw_pending");
        assertThat(result.runtimeShim()).contains("java.lang.ArithmeticException", "juno_throw_raise");
    }

    @Test
    void doubleConversionPullsInTheStringHelpersOnlyWhenUsed() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    java.math.BigDecimal value = java.math.BigDecimal.valueOf(0.25);
                }
                """);

        assertThat(result.runtimeShim()).contains("juno_big_decimal_value_of_double", "juno_format_double");
    }

    @Test
    void programsWithoutBigNumbersGetNoBigNumberRuntime() throws Exception {
        CompilationResult result = compile("""
                public static void main() {
                    int total = 1 + 2;
                }
                """);

        assertThat(result.runtimeShim()).doesNotContain("juno_big_");
    }

    @Test
    void rejectsUnsupportedMethodsWithAHelpfulMessage() throws Exception {
        String source = """
                package demo;
                public final class Tasks {
                    public static void main() {
                        java.math.BigInteger.TEN.isProbablePrime(10);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("java.math subset")
                .hasMessageContaining("isProbablePrime");
    }

    @Test
    void generatedRuntimeCompilesForBothBoards() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String members = """
                static java.math.BigDecimal scale(java.math.BigDecimal value, java.math.MathContext context) {
                    return value.round(context).add(java.math.BigDecimal.valueOf(0.5));
                }
                public static void main() {
                    java.math.BigDecimal pi = scale(new java.math.BigDecimal("3.14159"), java.math.MathContext.DECIMAL32);
                    java.math.BigInteger big = java.math.BigInteger.valueOf(12).shiftLeft(80).sqrt().gcd(java.math.BigInteger.TEN);
                    double d = pi.doubleValue() + big.doubleValue();
                }
                """;
        int index = 0;
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            Path directory = Files.createDirectory(temporaryDirectory.resolve("case" + index));
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.Board;
                    @Board(io.github.jabrena.juno.annotations.%s.class)
                    public final class Tasks {
                    %s
                    }
                    """.formatted(board, members);
            CompilerTestSupport.compileJava(directory, "demo.Tasks", source, "25");
            CompilationResult result = CompilerTestSupport.compileJuno(directory, "demo.Tasks");
            Path shim = directory.resolve("shim" + index++ + ".cpp");
            Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                    "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
            String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
        }
    }

    private static String availableCppCompiler() {
        for (String candidate : new String[]{"clang++", "g++"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) return candidate;
            } catch (java.io.IOException | InterruptedException ignored) {
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
