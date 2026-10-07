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

/** The {@code java.lang.ScopedValue} subset lowered onto Juno's binding-frame runtime. */
class ScopedValueTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void bindsRunsAndReadsValues() throws Exception {
        CompilationResult result = compile("""
                private static final ScopedValue<String> USER = ScopedValue.newInstance();
                private static final ScopedValue<String> ROLE = ScopedValue.newInstance();
                public static void main() throws Exception {
                    ScopedValue.where(USER, "alice").where(ROLE, "admin").run(() -> {
                        USER.get();
                        USER.isBound();
                        ROLE.orElse("none");
                    });
                    String name = ScopedValue.where(USER, "bob").call(() -> USER.get());
                }
                """);

        assertThat(result.assembly()).contains(
                ".global juno_thread_entry", ".global juno_scoped_call_entry",
                "bl juno_scoped_new", "bl juno_scoped_where", "bl juno_scoped_carrier_where",
                "bl juno_scoped_run", "bl juno_scoped_call", "bl juno_scoped_get",
                "bl juno_scoped_is_bound", "bl juno_scoped_or_else");
        assertThat(result.runtimeShim()).contains(
                "juno_scoped_top", "JunoBindingFrame", "extern \"C\" void juno_thread_entry(int32_t runnable);",
                "extern \"C\" int32_t juno_scoped_call_entry(int32_t operation);", "ScopedValue not bound");
        assertThat(result.runtimeShim()).doesNotContain("juno_thread_new");
    }

    @Test
    void forkedSubtasksInheritBindingsAndThreadsSwapThem() throws Exception {
        CompilationResult result = compile("""
                private static final ScopedValue<String> USER = ScopedValue.newInstance();
                public static void main() throws Exception {
                    ScopedValue.where(USER, "alice").run(() -> {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(() -> USER.get());
                            scope.join();
                        } catch (InterruptedException e) {
                            throw new IllegalStateException();
                        }
                    });
                }
                """, true);

        assertThat(result.runtimeShim()).contains(
                "task->bindings = juno_scoped_top;", "juno_slots[slot].bindings = thread->bindings;",
                "juno_slots[from].bindings = juno_scoped_top;", "juno_scoped_top = juno_slots[to].bindings;");
    }

    @Test
    void rejectsUnsupportedScopedValueMethods() throws Exception {
        String source = """
                package demo;
                public final class Tasks {
                    public static void main() throws Exception {
                        ScopedValue<String> key = ScopedValue.newInstance();
                        ScopedValue.where(key, "x").get(key);
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("ScopedValue subset");
    }

    @Test
    void generatedRuntimeCompilesForBothBoardPortsWithAndWithoutThreads() throws Exception {
        Assumptions.assumeTrue(availableCppCompiler() != null, "No C++ compiler available");
        String standalone = """
                private static final ScopedValue<String> USER = ScopedValue.newInstance();
                public static void main() throws Exception {
                    ScopedValue.where(USER, "alice").run(() -> USER.get());
                }
                """;
        String threaded = """
                private static final ScopedValue<String> USER = ScopedValue.newInstance();
                public static void main() throws Exception {
                    String r = ScopedValue.where(USER, "alice").call(() -> {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            var task = scope.fork(() -> USER.get());
                            scope.join();
                            return task.get();
                        }
                    });
                }
                """;
        int index = 0;
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            for (String members : new String[]{standalone, threaded}) {
                Path directory = Files.createDirectory(temporaryDirectory.resolve("case" + index));
                CompilationResult result = compileOnBoard(directory, members, board);
                Path shim = directory.resolve("shim" + index++ + ".cpp");
                Files.writeString(shim, result.runtimeShim(), StandardCharsets.UTF_8);
                Process process = new ProcessBuilder(availableCppCompiler(), "-std=c++17", "-fsyntax-only", "-x",
                        "c++", "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
                assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
                String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
            }
        }
    }

    private CompilationResult compileOnBoard(Path directory, String members, String board) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.Board;
                @Board(io.github.jabrena.juno.annotations.%s.class)
                public final class Tasks {
                %s
                }
                """.formatted(board, members);
        CompilerTestSupport.compileJavaWithPreview(directory, "demo.Tasks", source);
        return CompilerTestSupport.compileJuno(directory, "demo.Tasks");
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
        return compile(members, false);
    }

    private CompilationResult compile(String members, boolean preview) throws Exception {
        String source = """
                package demo;
                public final class Tasks {
                %s
                }
                """.formatted(members);
        if (preview) {
            CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Tasks", source);
        } else {
            CompilerTestSupport.compileJava(temporaryDirectory, "demo.Tasks", source, "25");
        }
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Tasks");
    }
}
