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

/** The JDK 25 preview {@code StructuredTaskScope} subset lowered onto Juno's task runtime. */
class StructuredTaskScopeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultOpenForksBothTaskShapesJoinsAndExposesSubtaskState() throws Exception {
        CompilationResult result = compile("""
                try (var scope = StructuredTaskScope.open()) {
                    var first = scope.fork(() -> "first");
                    var second = scope.fork(() -> { });
                    scope.join();
                    if (first.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
                        first.get();
                    }
                    second.exception();
                    scope.isCancelled();
                }
                """);

        assertThat(result.assembly()).contains(
                ".global juno_task_entry", "juno_task_entry:",
                "bl juno_task_scope_open_default", "bl juno_task_scope_fork_callable",
                "bl juno_task_scope_fork_runnable", "bl juno_task_scope_join",
                "bl juno_task_state", "bl juno_task_get", "bl juno_task_exception",
                "bl juno_task_scope_is_cancelled", "bl juno_task_scope_close");
        assertThat(result.runtimeShim()).contains(
                "extern \"C\" int32_t juno_task_entry(int32_t callable);",
                "extern \"C\" void juno_thread_entry(int32_t runnable);",
                "JUNO_JOINER_AWAIT_ALL_SUCCESSFUL", "juno_task_complete",
                "JUNO_SCOPE_CLOSED | JUNO_SCOPE_SHUTDOWN | JUNO_SCOPE_JOINED",
                "join() has already been attempted");
    }

    @Test
    void builtInJoinerFactoriesSelectTheirJdk25ResultPolicies() throws Exception {
        CompilationResult result = compile("""
                var allSuccessful = StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow();
                try (var scope = StructuredTaskScope.open(allSuccessful)) {
                    scope.fork(() -> "all");
                    scope.join();
                }
                try (var scope = StructuredTaskScope.open(
                        StructuredTaskScope.Joiner.<String>anySuccessfulResultOrThrow())) {
                    scope.fork(() -> "winner");
                    scope.join();
                }
                try (var scope = StructuredTaskScope.open(
                        StructuredTaskScope.Joiner.<String>awaitAllSuccessfulOrThrow())) {
                    scope.fork(() -> "done");
                    scope.join();
                }
                try (var scope = StructuredTaskScope.open(
                        StructuredTaskScope.Joiner.<String>awaitAll())) {
                    scope.fork(() -> { throw new IllegalStateException("ignored"); });
                    scope.join();
                }
                """);

        assertThat(result.assembly()).contains(
                "bl juno_task_scope_open", "bl juno_task_scope_fork_callable",
                "bl juno_task_scope_join");
        assertThat(result.runtimeShim()).contains(
                "JUNO_JOINER_ALL_SUCCESSFUL", "JUNO_JOINER_ANY_SUCCESSFUL",
                "JUNO_JOINER_AWAIT_ALL_SUCCESSFUL", "JUNO_JOINER_AWAIT_ALL",
                "JUNO_SCOPE_HAS_RESULT",
                "juno_task_cancel_siblings(scope, task)", "juno_port_cancel(slot)");
    }

    @Test
    void generatedStructuredTaskRuntimeCompilesForBothBoardPorts() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            CompilationResult result = compile("""
                    try (var scope = StructuredTaskScope.open()) {
                        var task = scope.fork(() -> "done");
                        scope.join();
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
                        StructuredTaskScope.open(StructuredTaskScope.Joiner.allUntil(null));
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JDK 25 StructuredTaskScope subset")
                .hasMessageContaining("allUntil");
    }

    @Test
    void rejectsCustomJoinerImplementationsAtCompileTime() throws Exception {
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    static final class CustomJoiner implements StructuredTaskScope.Joiner<Object, Void> {
                        public Void result() { return null; }
                    }
                    public static void main() throws Exception {
                        var joiner = new CustomJoiner();
                        try (var scope = StructuredTaskScope.open(joiner)) {
                            scope.join();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("custom Joiner implementations")
                .hasMessageContaining("demo.Tasks$CustomJoiner");
    }

    @Test
    void rejectsCustomJoinerLambdasAtCompileTime() throws Exception {
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() throws Exception {
                        StructuredTaskScope.Joiner<Object, Void> joiner = () -> null;
                        try (var scope = StructuredTaskScope.open(joiner)) {
                            scope.join();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("custom Joiner implementations");
    }

    @Test
    void rejectsConfigurationOverloadAtCompileTime() throws Exception {
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() {
                        StructuredTaskScope.open(StructuredTaskScope.Joiner.awaitAll(), null);
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JDK 25 StructuredTaskScope subset")
                .hasMessageContaining("Configuration");
    }

    @Test
    void javacRequiresEnablePreviewForTheJdk25Api() {
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() {
                        StructuredTaskScope.open().close();
                    }
                }
                """;

        assertThatThrownBy(() -> CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Fixture javac failed");
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
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);
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
