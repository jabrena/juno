package io.github.jabrena.juno;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
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

/** The JDK 25 preview {@code StructuredTaskScope} subset lowered onto Juno's task runtime. */
@DisplayName("Restricted StructuredTaskScope subset")
class StructuredTaskScopeTest {
    @TempDir
    Path temporaryDirectory;

    private static final String DEFAULT_OPEN = """
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
            """;

    private static final String BUILT_IN_JOINERS = """
            var allSuccessful = StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow();
            try (var scope = StructuredTaskScope.open(allSuccessful)) {
                scope.fork(() -> "all");
                scope.join();
            }
            try (var scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<String>anySuccessfulOrThrow())) {
                scope.fork(() -> "winner");
                scope.join();
            }
            try (var scope = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<String>awaitAllSuccessfulOrThrow())) {
                scope.fork(() -> "done");
                scope.join();
            }
            """;

    private static final String NESTED_CANCELLATION = """
            try (var outer = StructuredTaskScope.open(
                    StructuredTaskScope.Joiner.<String>anySuccessfulOrThrow())) {
                outer.fork(() -> {
                    synchronized (new Object()) {
                        try (var inner = StructuredTaskScope.open()) {
                            inner.fork(() -> "slow");
                            inner.join();
                        }
                    }
                    return "outer";
                });
                outer.fork(() -> "fast");
                outer.join();
            }
            """;

    private static final String TIMEOUT_CONFIGURATION = """
            try (var scope = StructuredTaskScope.open(
                    configuration -> configuration.withTimeout(java.time.Duration.ofMillis(100)))) {
                var task = scope.fork(() -> "done");
                scope.join();
                task.get();
            }
            """;

    static Stream<String> joinerFactories() {
        return Stream.of("allSuccessfulOrThrow", "anySuccessfulOrThrow", "awaitAllSuccessfulOrThrow");
    }

    @Test
    @DisplayName("Default open forks both task shapes, joins and exposes subtask state")
    void shouldForkBothTaskShapesJoinAndExposeSubtaskState() throws Exception {
        // Given
        CompilationResult result = compile(DEFAULT_OPEN);

        // When & Then
        assertThat(result.assembly()).contains(
                ".global juno_task_entry", "juno_task_entry:",
                "bl juno_task_scope_open_default", "bl juno_task_scope_fork_callable",
                "bl juno_task_scope_fork_runnable", "bl juno_task_scope_join",
                "bl juno_task_state", "bl juno_task_get", "bl juno_task_exception",
                "bl juno_task_scope_is_cancelled", "bl juno_task_scope_close");
    }

    @Test
    @DisplayName("The runtime shim declares the task entry points and the join-once guard")
    void shouldDeclareTheTaskEntryPointsAndTheJoinOnceGuardInTheShim() throws Exception {
        // Given
        CompilationResult result = compile(DEFAULT_OPEN);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "extern \"C\" int32_t juno_task_entry(int32_t callable);",
                "extern \"C\" void juno_thread_entry(int32_t runnable);",
                "JUNO_JOINER_AWAIT_ALL_SUCCESSFUL", "juno_task_complete",
                "JUNO_SCOPE_CLOSED | JUNO_SCOPE_SHUTDOWN | JUNO_SCOPE_JOINED",
                "join() has already been attempted");
    }

    @Test
    @DisplayName("Built-in joiner factories select their JDK 25 result policies in the assembly")
    void shouldSelectTheBuiltInJoinerResultPoliciesInTheAssembly() throws Exception {
        // Given
        CompilationResult result = compile(BUILT_IN_JOINERS);

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_task_scope_open", "bl juno_task_scope_fork_callable",
                "bl juno_task_scope_join");
    }

    @Test
    @DisplayName("The runtime shim carries the joiner policies and sibling cancellation")
    void shouldCarryTheJoinerPoliciesAndSiblingCancellationInTheShim() throws Exception {
        // Given
        CompilationResult result = compile(BUILT_IN_JOINERS);

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "JUNO_JOINER_ALL_SUCCESSFUL", "JUNO_JOINER_ANY_SUCCESSFUL",
                "JUNO_JOINER_AWAIT_ALL_SUCCESSFUL",
                "JUNO_SCOPE_HAS_RESULT",
                "juno_task_cancel_siblings(scope, task)", "juno_port_cancel(slot)",
                "juno_task_cancel_owned(slot + 1u)", "juno_task_release_monitors(slot + 1u)");
    }

    @ParameterizedTest(name = "cancelling nested scopes compiles as C++ for {0}")
    @ValueSource(strings = {"ArduinoUnoR4WiFiBoard", "ArduinoUnoQBoard"})
    @DisplayName("Cancelling a task cancels its inner scopes and releases its monitors")
    void shouldCancelATaskAndItsInnerScopesAndReleaseItsMonitors(String board) throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        CompilationResult result = compile(NESTED_CANCELLATION, board);
        Path shim = temporaryDirectory.resolve("nested-" + board + ".cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        // When
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();

        // Then
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
    }

    @ParameterizedTest(name = "generated runtime compiles as C++ for {0}")
    @ValueSource(strings = {"ArduinoUnoR4WiFiBoard", "ArduinoUnoQBoard"})
    @DisplayName("The generated structured-task runtime compiles for both board ports")
    void shouldCompileTheGeneratedStructuredTaskRuntimeForBothBoardPorts(String board) throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        CompilationResult result = compile(TIMEOUT_CONFIGURATION, board);
        Path shim = temporaryDirectory.resolve(board + ".cpp");
        Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);

        // When
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();

        // Then
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
    }

    @Test
    @DisplayName("Rejects StructuredTaskScope operations outside the restricted subset")
    void shouldRejectStructuredTaskScopeOperationsOutsideTheRestrictedSubset() throws Exception {
        // Given
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

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("JDK 27 StructuredTaskScope subset")
                .hasMessageContaining("allUntil");
    }

    @Test
    @DisplayName("Rejects custom Joiner implementations at compile time")
    void shouldRejectCustomJoinerImplementationsAtCompileTime() throws Exception {
        // Given
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    static final class CustomJoiner implements StructuredTaskScope.Joiner<Object, Void, RuntimeException> {
                        public Void result() { return null; }
                        public Void timeout() { return null; }
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

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("custom Joiner implementations")
                .hasMessageContaining("demo.Tasks$CustomJoiner");
    }

    @ParameterizedTest(name = "rejects the {0} overload that chooses the exception type")
    @MethodSource("joinerFactories")
    @DisplayName("Rejects joiner factory overloads that choose the exception type")
    void shouldRejectJoinerFactoryOverloadsThatChooseTheExceptionType(String factory) throws Exception {
        // Given
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() throws Exception {
                        StructuredTaskScope.open(StructuredTaskScope.Joiner.<String, Exception>%s(null));
                    }
                }
                """.formatted(factory);
        Path directory = Files.createDirectory(temporaryDirectory.resolve(factory));
        CompilerTestSupport.compileJavaWithPreview(directory, "demo.Tasks", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(directory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("StructuredTaskScope subset")
                .hasMessageContaining(factory);
    }

    @Test
    @DisplayName("Rejects reading the result list of allSuccessfulOrThrow")
    void shouldRejectReadingTheResultListOfAllSuccessfulOrThrow() throws Exception {
        // Given
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() throws Exception {
                        try (var scope = StructuredTaskScope.open(
                                StructuredTaskScope.Joiner.<String>allSuccessfulOrThrow())) {
                            scope.fork(() -> "a");
                            java.util.List<String> results = scope.join();
                            results.get(0);
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class);
    }

    @Test
    @DisplayName("Supports the timeout configuration overload in the assembly")
    void shouldSupportTheTimeoutConfigurationOverloadInTheAssembly() throws Exception {
        // Given
        CompilationResult result = compileTimeout();

        // When & Then
        assertThat(result.assembly()).contains(
                "bl juno_task_scope_open_with_config", "juno_task_configuration_entry",
                "bl juno_task_configuration_with_timeout", "bl juno_duration_of_millis");
    }

    @Test
    @DisplayName("The runtime shim carries the timeout handling")
    void shouldCarryTheTimeoutHandlingInTheShim() throws Exception {
        // Given
        CompilationResult result = compileTimeout();

        // When & Then
        assertThat(result.runtimeShim()).contains(
                "JUNO_SCOPE_TIMED_OUT", "juno_task_scope_timeout_expired",
                "structured task scope timed out");
    }

    @Test
    @DisplayName("Javac requires --enable-preview for the JDK 25 API")
    void shouldRequireEnablePreviewForTheJdk25Api() {
        // Given
        String source = """
                package demo;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() {
                        StructuredTaskScope.open().close();
                    }
                }
                """;

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Fixture javac failed");
    }

    private CompilationResult compileTimeout() throws Exception {
        String source = """
                package demo;
                import java.time.Duration;
                import java.util.concurrent.StructuredTaskScope;
                public final class Tasks {
                    public static void main() throws Exception {
                        try (var scope = StructuredTaskScope.open(
                                StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow(),
                                configuration -> configuration.withTimeout(Duration.ofMillis(20)))) {
                            scope.fork(() -> "done");
                            scope.join();
                        }
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
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
