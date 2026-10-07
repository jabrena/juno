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
 * The cooperative thread runtime behind {@code StructuredTaskScope}: the closed-world {@code Runnable.run()} entry
 * function, the runtime in the shim, what a program without subtasks does not pay for, and the rejection of
 * {@code java.lang.Thread} itself. Behavior of the compiled code is covered under QEMU ({@code juno-examples}'
 * {@code TaskScheduling} program); this checks the generated text.
 */
class ThreadsTest {
    @TempDir
    Path temporaryDirectory;

    private static String program(String board, String body) {
        return """
                package demo;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.usb.Serial;
                @Board(%1$s.class)
                public final class Worker {
                    static final class Job implements Runnable {
                        public void run() {
                            Serial.println(1);
                        }
                    }
                    public static void main() throws Exception {
                %2$s
                    }
                }
                """.formatted(board, body);
    }

    private static final String START_AND_JOIN = """
            try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                scope.fork(new Job());
                scope.fork(() -> Serial.println(2));
                scope.join();
            }
            """;

    private CompilationResult compile(String board, String body) throws Exception {
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", program(board, body));
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker");
    }

    @Test
    void aStartedThreadEntersItsRunnableThroughAnExportedDispatchFunction() throws Exception {
        CompilationResult result = compile("ArduinoUnoR4WiFi", START_AND_JOIN);

        assertThat(result.assembly()).contains(".global juno_thread_entry", "juno_thread_entry:")
                .as("loop backedges go through the thread runtime").doesNotContain("bl yield\n");
        assertThat(result.assembly()).contains("bl juno_task_scope_fork_runnable", "bl juno_thread_main_exit");
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_thread_entry(int32_t runnable);",
                "juno_thread_gc_scan(&stackMarker);");
    }

    @Test
    void unoR4SwitchesStacksWithItsOwnContextSwitch() throws Exception {
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        assertThat(shim).contains("juno_context_switch:", "UNO R4 port").doesNotContain("zephyr/kernel.h");
    }

    @Test
    void unoQRunsEachThreadOnAZephyrThreadPassingABaton() throws Exception {
        String shim = compile("ArduinoUnoQ", START_AND_JOIN).runtimeShim();

        assertThat(shim).contains("#include <zephyr/kernel.h>", "UNO Q port", "k_thread_create", "k_sem_give")
                .doesNotContain("juno_context_switch");
    }

    @Test
    void delayBecomesASleepThatLetsOtherThreadsRun() throws Exception {
        String body = START_AND_JOIN + "io.github.jabrena.juno.api.Delay.millis(10);\n";

        CompilationResult result = compile("ArduinoUnoR4WiFi", body);

        assertThat(result.assembly()).contains("bl juno_thread_delay").doesNotContain("bl delay\n");
    }

    @Test
    void sleepAndYieldAloneNeedNoScheduler() throws Exception {
        CompilationResult result = compile("ArduinoUnoR4WiFi", "Thread.sleep(5);\nThread.yield();\n");

        assertThat(result.assembly()).contains("bl juno_thread_sleep", "bl juno_thread_yield")
                .doesNotContain("juno_thread_entry", "juno_thread_backedge");
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_thread_sleep(int64_t millis)")
                .doesNotContain("juno_slots", "juno_context_switch");
    }

    @Test
    void programsWithoutThreadsPayNothingForThem() throws Exception {
        CompilationResult result = compile("ArduinoUnoR4WiFi", "Serial.println(3);\n");

        assertThat(result.assembly()).doesNotContain("juno_thread");
        assertThat(result.runtimeShim()).doesNotContain("juno_thread", "juno_slots");
    }

    @Test
    void aForkWithNoRunnableAnywhereIsRejected() throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.api.io.usb.Serial;
                public final class Worker {
                    static void spawn(Runnable work) throws Exception {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(work);
                            scope.join();
                        }
                    }
                    public static void main() throws Exception {
                        Serial.println(1);
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", source);

        // spawn() is unreachable, so nothing asks for a subtask
        assertThat(CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker").runtimeShim())
                .doesNotContain("juno_thread");

        String reachable = source.replace("Serial.println(1);", "spawn(null);");
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", reachable);
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker"))
                .isInstanceOf(CompileException.class).hasMessageContaining("no reachable class or lambda");
    }

    @Test
    void userLevelThreadsAreRejectedAtLinkTime() throws Exception {
        for (String body : new String[]{
                "Thread job = new Thread(new Job());\njob.start();\njob.join();\n",
                "Thread job = new Thread(new Job());\njob.setDaemon(true);\n",
                "Thread job = Thread.currentThread();\njob.interrupt();\n"}) {
            CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", program("ArduinoUnoR4WiFi", body));

            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("does not support java.lang.Thread")
                    .hasMessageContaining("StructuredTaskScope");
        }
    }

    @Test
    void bothPortsCompileAsCpp() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            Path shim = temporaryDirectory.resolve(board + ".cpp");
            Files.writeString(shim, compile(board, START_AND_JOIN).runtimeShim(), StandardCharsets.UTF_8);
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
