package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code AtomicInteger}, {@code AtomicBoolean} and {@code AtomicLong}: the generated calls, what a program without
 * atomics does not pay for, and the rejection of everything outside the subset. Behavior of the compiled code is
 * covered under QEMU ({@code juno-examples}' {@code Atomics} program).
 */
class AtomicsTest {
    @TempDir
    Path temporaryDirectory;

    private static String program(String board, String body) {
        return """
                package demo;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.serial.Serial;
                import java.util.concurrent.atomic.*;
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

    private CompilationResult compile(String board, String body) throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", program(board, body));
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter");
    }

    @Test
    void everyOperationBecomesOneShimCall() throws Exception {
        CompilationResult result = compile("ArduinoUnoR4WiFi", ALL_THREE);

        assertThat(result.assembly()).contains("bl juno_atomic_int_new", "bl juno_atomic_long_new_default",
                "bl juno_atomic_int_new_default", "bl juno_atomic_int_increment_and_get",
                "bl juno_atomic_long_add_and_get", "bl juno_atomic_int_compare_and_set",
                "bl juno_atomic_long_compare_and_set", "bl juno_atomic_long_get", "bl juno_atomic_int_get");
        assertThat(result.runtimeShim()).contains("extern \"C\" int32_t juno_atomic_int_new(int32_t value)",
                "extern \"C\" int64_t juno_atomic_long_get(int32_t handle)");
    }

    @Test
    void programsWithoutAtomicsPayNothingForThem() throws Exception {
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.serial.Serial;
                @Board(ArduinoUnoR4WiFi.class)
                public final class Plain {
                    public static void main() {
                        Serial.println(3);
                    }
                }
                """);
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

        assertThat(result.assembly()).doesNotContain("juno_atomic");
        assertThat(result.runtimeShim()).doesNotContain("juno_atomic");
    }

    @Test
    void anAtomicNeedsNoSchedulerOrMonitor() throws Exception {
        CompilationResult result = compile("ArduinoUnoQ", ALL_THREE);

        assertThat(result.runtimeShim()).doesNotContain("juno_thread", "juno_monitor");
    }

    @Test
    void atomicApiOutsideTheSubsetFailsAtLinkTime() throws Exception {
        for (String body : new String[]{
                "Serial.println(SHARED.updateAndGet(value -> value + 1));\n",
                "SHARED.lazySet(1);\n",
                "AtomicReference<String> reference = new AtomicReference<>(\"a\");\nreference.get();\n",
                "Serial.println(new AtomicBoolean().incrementAndGet());\n"}) {
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Counter", program("ArduinoUnoR4WiFi",
                    body.replace("new AtomicBoolean().incrementAndGet()", "new AtomicLong().toString().length()")));

            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Counter"))
                    .isInstanceOf(CompileException.class).hasMessageContaining("atomic");
        }
    }

    @Test
    void bothPortsCompileAsCpp() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            Path shim = temporaryDirectory.resolve(board + ".cpp");
            Files.writeString(shim, compile(board, ALL_THREE).runtimeShim(), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                    "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
        }
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
