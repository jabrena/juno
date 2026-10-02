package io.github.jabrena.juno;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Java 21 preview {@code StructuredTaskScope} policy subset lowered onto Juno's task runtime. */
class StructuredTaskScopeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void shutdownOnFailureForksJoinsPropagatesFailureAndExposesResults() throws Exception {
        CompilationResult result = compile("""
                try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                    var first = scope.fork(() -> "first");
                    var second = scope.fork(() -> "second");
                    scope.join().throwIfFailed();
                    first.get();
                    second.get();
                }
                """);

        assertThat(result.assembly()).contains(
                ".global juno_task_entry", "juno_task_entry:",
                "bl juno_task_scope_new_failure", "bl juno_task_scope_fork",
                "bl juno_task_scope_join", "bl juno_task_scope_throw_if_failed",
                "bl juno_task_get", "bl juno_task_scope_close");
        assertThat(result.runtimeShim()).contains(
                "extern \"C\" int32_t juno_task_entry(int32_t callable);",
                "JUNO_TASK_SCOPE_FAILURE", "juno_task_complete")
                .doesNotContain("juno_thread_entry");
    }

    @Test
    void shutdownOnSuccessReturnsTheFirstSuccessfulResultAndCancelsSiblings() throws Exception {
        CompilationResult result = compile("""
                try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
                    scope.fork(() -> { throw new IllegalStateException("first"); });
                    scope.fork(() -> "winner");
                    scope.join();
                    scope.result();
                }
                """);

        assertThat(result.assembly()).contains(
                "bl juno_task_scope_new_success", "bl juno_task_scope_fork",
                "bl juno_task_scope_join", "bl juno_task_scope_result");
        assertThat(result.runtimeShim()).contains(
                "JUNO_TASK_SCOPE_SUCCESS", "JUNO_SCOPE_HAS_RESULT",
                "juno_task_cancel_siblings(scope, task)", "juno_port_cancel(slot)");
    }

    @Test
    void generatedStructuredTaskRuntimeCompilesForBothBoardPorts() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            CompilationResult result = compile("""
                    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                        var task = scope.fork(() -> "done");
                        scope.join().throwIfFailed();
                        task.get();
                    }
                    """, board);
            Path shim = temporaryDirectory.resolve(board + ".cpp");
            Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                    "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
        }
    }

    @Test
    void rejectsStructuredTaskScopeOperationsOutsideTheRestrictedSubset() throws Exception {
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() throws Exception {
                        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                            scope.shutdown();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithStructuredTaskScope(temporaryDirectory, "demo.Tasks", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("restricted StructuredTaskScope subset supports only")
                .hasMessageContaining("shutdown()V");
    }

    private CompilationResult compile(String body) throws Exception {
        return compile(body, null);
    }

    private CompilationResult compile(String body, String board) throws Exception {
        String source = """
                package demo;
                %s
                import java.util.concurrent.StructuredTaskScope;
                %s
                public final class Tasks {
                    public static void main() throws Exception {
                %s
                    }
                }
                """.formatted(board == null ? "" : "import io.github.jabrena.juno.annotations.Board;",
                board == null ? "" : "@Board(io.github.jabrena.juno.annotations." + board + ".class)", body);
        CompilerTestSupport.compileJavaWithStructuredTaskScope(temporaryDirectory, "demo.Tasks", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
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
}
