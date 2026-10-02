package io.github.jabrena.juno;

import io.github.jabrena.juno.classfile.ClassPath;
import io.github.jabrena.juno.classfile.JavaClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Restricted Java synchronization primitives over Juno's cooperative thread scheduler. */
class SynchronizationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void aVolatileFlagIsReloadedOnEverySpinLoopIteration() throws Exception {
        String source = """
                package demo;
                public final class VolatileFlag {
                    private static volatile boolean ready;
                    private static int result;
                    public static void main() throws InterruptedException {
                        Thread worker = new Thread(() -> {
                            result = 42;
                            ready = true;
                        });
                        worker.start();
                        while (!ready) {
                            // The loop backedge is a cooperative scheduler switch point.
                        }
                        worker.join();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.VolatileFlag", source);

        Map<String, JavaClass> classes = new ClassPath().load(List.of(temporaryDirectory));
        assertThat(classes.get("demo/VolatileFlag").findField("ready", "Z").isVolatile()).isTrue();

        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.VolatileFlag").assembly();
        assertThat(assembly).contains(
                "juno_static_demo_VolatileFlag_ready_Z",
                "bl juno_thread_backedge");
        assertThat(occurrences(assembly, "=juno_static_demo_VolatileFlag_ready_Z"))
                .as("the loop must execute a real load of the volatile flag")
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void aVolatileFieldIsReadAgainAfterACall() throws Exception {
        String source = """
                package demo;
                public final class VolatileAcrossCall {
                    private static volatile int state;
                    public static void main() {
                        int before = state;
                        Thread.yield();
                        int after = state;
                        if (before == after) state = after + 1;
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.VolatileAcrossCall", source);

        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.VolatileAcrossCall").assembly();

        assertThat(occurrences(assembly, "=juno_static_demo_VolatileAcrossCall_state_I"))
                .as("both source reads and the write must access the volatile field")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void synchronizedBlocksUseAReentrantCooperativeMonitor() throws Exception {
        String source = """
                package demo;
                public final class SynchronizedBlock {
                    static final class Guard { }
                    private static final Guard GUARD = new Guard();
                    private static int counter;
                    static void increment() {
                        synchronized (GUARD) {
                            synchronized (GUARD) {
                                counter++;
                            }
                        }
                    }
                    public static void main() throws InterruptedException {
                        Thread first = new Thread(SynchronizedBlock::increment);
                        Thread second = new Thread(SynchronizedBlock::increment);
                        first.start();
                        second.start();
                        first.join();
                        second.join();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.SynchronizedBlock", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SynchronizedBlock");

        assertThat(result.assembly()).contains("bl juno_monitor_enter", "bl juno_monitor_exit");
        assertThat(result.runtimeShim()).contains(
                "extern \"C\" void juno_monitor_enter(int32_t handle)",
                "extern \"C\" void juno_monitor_exit(int32_t handle)",
                "monitor->depth++");
    }

    @Test
    void reentrantLockUsesTheSameCooperativeMonitorRuntime() throws Exception {
        String source = """
                package demo;
                import java.util.concurrent.locks.ReentrantLock;
                public final class ExplicitLock {
                    private static final ReentrantLock LOCK = new ReentrantLock();
                    private static int counter;
                    static void increment() {
                        LOCK.lock();
                        try {
                            counter++;
                            if (LOCK.tryLock()) {
                                try {
                                    counter++;
                                } finally {
                                    LOCK.unlock();
                                }
                            }
                        } finally {
                            LOCK.unlock();
                        }
                    }
                    public static void main() throws InterruptedException {
                        Thread first = new Thread(ExplicitLock::increment);
                        Thread second = new Thread(ExplicitLock::increment);
                        first.start();
                        second.start();
                        first.join();
                        second.join();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.ExplicitLock", source);

        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ExplicitLock");

        assertThat(result.assembly()).contains(
                "bl juno_reentrant_lock_new",
                "bl juno_monitor_enter",
                "bl juno_monitor_try_enter",
                "bl juno_monitor_exit");
    }

    @Test
    void synchronizedMethodsAreRejectedInsteadOfSilentlyRunningUnlocked() throws Exception {
        String source = """
                package demo;
                public final class SynchronizedMethod {
                    private int counter;
                    synchronized void increment() {
                        counter++;
                    }
                    public static void main() {
                        new SynchronizedMethod().increment();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.SynchronizedMethod", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.SynchronizedMethod"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("synchronized methods")
                .hasMessageContaining("synchronized (lock)");
    }

    @Test
    void reentrantLockApisOutsideTheRestrictedSubsetAreRejectedExplicitly() throws Exception {
        String source = """
                package demo;
                import java.util.concurrent.locks.ReentrantLock;
                public final class UnsupportedLockApi {
                    public static void main() {
                        new ReentrantLock(true).lock();
                    }
                }
                """;
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.UnsupportedLockApi", source);

        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.UnsupportedLockApi"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("restricted ReentrantLock subset")
                .hasMessageContaining("new ReentrantLock(), lock(), tryLock(), and unlock()");
    }

    @Test
    void monitorRuntimeCompilesAsCppForBothBoards() throws Exception {
        String compiler = availableCppCompiler();
        org.junit.jupiter.api.Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        String body = """
                static final class Guard { }
                static final Guard GUARD = new Guard();
                public static void main() {
                    synchronized (GUARD) { }
                }
                """;
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            String source = """
                    package demo;
                    import io.github.jabrena.juno.annotations.%1$s;
                    import io.github.jabrena.juno.annotations.Board;
                    @Board(%1$s.class)
                    public final class %1$sMonitor {
                    %2$s
                    }
                    """.formatted(board, body);
            String mainClass = "demo." + board + "Monitor";
            CompilerTestSupport.compileJava(temporaryDirectory, mainClass, source);
            Path shim = temporaryDirectory.resolve(board + "Monitor.cpp");
            Files.writeString(shim, CompilerTestSupport.compileJuno(temporaryDirectory, mainClass).runtimeShim(),
                    StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                    "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as("%s shim:%n%s", board, diagnostics).isZero();
        }
    }

    private static int occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
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
