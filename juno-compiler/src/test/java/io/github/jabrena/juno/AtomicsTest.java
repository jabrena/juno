package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * {@code AtomicInteger}, {@code AtomicBoolean} and {@code AtomicLong}: the generated calls, what a program without
 * atomics does not pay for, and the rejection of everything outside the subset. Behavior of the compiled code is
 * covered under QEMU ({@code juno-examples}' {@code Atomics} program).
 */
@DisplayName("Atomic integer, boolean and long support")
class AtomicsTest {
    @TempDir
    Path temporaryDirectory;

    private static String program(String board, String body) {
        return """
                package demo;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.hardware.serial.Serial;
                import java.util.concurrent.atomic.AtomicBoolean;
                import java.util.concurrent.atomic.AtomicInteger;
                import java.util.concurrent.atomic.AtomicLong;
                import java.util.concurrent.atomic.AtomicReference;
                @Board(%1$s.class)
                public final class Counter {
                    static final AtomicInteger SHARED = new AtomicInteger(3);
                    static void bump(AtomicLong total) {
                        total.addAndGet(5_000_000_000L);
                    }
                    public static void main() {
                %2$s
                    }
                }
                """.formatted(board, body);
    }

    private static final String ALL_THREE = """
            AtomicLong total = new AtomicLong();
            AtomicBoolean done = new AtomicBoolean();
            bump(total);
            SHARED.incrementAndGet();
            done.compareAndSet(false, true);
            Serial.println(total.get());
            Serial.println(SHARED.get());
            Serial.println(done.get() ? 1 : 0);
            Serial.println(total.compareAndSet(5_000_000_000L, 1L) ? 1 : 0);
            """;

    static Stream<Arguments> unsupportedAtomicCalls() {
        return Stream.of(
                arguments("updateAndGet", "Serial.println(SHARED.updateAndGet(value -> value + 1));\n"),
                arguments("lazySet", "SHARED.lazySet(1);\n"),
                arguments("AtomicReference", "AtomicReference<String> reference = new AtomicReference<>(\"a\");\n"
                        + "reference.get();\n"),
                arguments("AtomicLong.toString", "Serial.println(new AtomicLong().toString().length());\n"));
    }

    private CompilationResult compile(String board, String body) throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", program(board, body));
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter");
    }

    @Test
    @DisplayName("Every atomic operation becomes one shim call")
    void shouldBecomeOneShimCallForEveryAtomicOperation() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", ALL_THREE);

        // When & Then
        assertThat(result.assembly()).contains("bl juno_atomic_int_new", "bl juno_atomic_long_new_default",
                "bl juno_atomic_int_new_default", "bl juno_atomic_int_increment_and_get",
                "bl juno_atomic_long_add_and_get", "bl juno_atomic_int_compare_and_set",
                "bl juno_atomic_long_compare_and_set", "bl juno_atomic_long_get", "bl juno_atomic_int_get");
    }

    @Test
    @DisplayName("The runtime shim declares the atomic entry points")
    void shouldDeclareTheAtomicEntryPointsInTheRuntimeShim() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", ALL_THREE);

        // When & Then
        assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_atomic_int_new(int32_t value)",
                "extern \"C\" int64_t juno_atomic_long_get(int32_t handle)");
    }

    @Test
    @DisplayName("A program without atomics pays nothing for them in its assembly")
    void shouldPayNothingForAtomicsInTheAssemblyOfAProgramWithoutThem() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.hardware.serial.Serial;
                @Board(ArduinoUnoR4WiFi.class)
                public final class Plain {
                    public static void main() {
                        Serial.println(3);
                    }
                }
                """);

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

        // Then
        assertThat(result.assembly()).doesNotContain("juno_atomic");
    }

    @Test
    @DisplayName("A program without atomics pays nothing for them in its runtime shim")
    void shouldPayNothingForAtomicsInTheShimOfAProgramWithoutThem() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.hardware.serial.Serial;
                @Board(ArduinoUnoR4WiFi.class)
                public final class Plain {
                    public static void main() {
                        Serial.println(3);
                    }
                }
                """);

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

        // Then
        assertThat(result.runtimeShim()).doesNotContain("juno_atomic");
    }

    @Test
    @DisplayName("An atomic needs no scheduler or monitor")
    void shouldNeedNoSchedulerOrMonitorForAnAtomic() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoQ", ALL_THREE);

        // When & Then
        assertThat(result.runtimeShim()).doesNotContain("juno_thread", "juno_monitor");
    }

    @ParameterizedTest(name = "rejects {0} at link time")
    @MethodSource("unsupportedAtomicCalls")
    @DisplayName("Atomic API outside the subset fails at link time")
    void shouldFailAtLinkTimeForAtomicApiOutsideTheSubset(String name, String body) throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", program("ArduinoUnoR4WiFi", body));

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter"))
                .isInstanceOf(CompileException.class).hasMessageContaining("atomic");
    }

    @ParameterizedTest(name = "{0} shim compiles as C++")
    @ValueSource(strings = {"ArduinoUnoR4WiFi", "ArduinoUnoQ"})
    @DisplayName("Both ports' runtime shims compile as C++")
    void shouldCompileBothPortsRuntimeShimsAsCpp(String board) throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        Path shim = temporaryDirectory.resolve(board + ".cpp");
        Files.writeString(shim, compile(board, ALL_THREE).runtimeShim(), StandardCharsets.UTF_8);

        // When
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();

        // Then
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
    }

    private static String availableCppCompiler() {
        for (String candidate : new String[]{"clang++", "g++"}) {
            try {
                Process process = new ProcessBuilder(candidate, "--version").start();
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (IOException | InterruptedException ignored) {
                // Try the next compiler.
            }
        }
        return null;
    }
}
