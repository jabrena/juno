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
 * The blocking concurrency subset beside virtual threads: {@code CountDownLatch}, {@code Semaphore},
 * {@code Object.wait/notify/notifyAll} and {@code Thread.interrupt/isInterrupted}. Behavior of the compiled code is
 * covered on hardware and under QEMU; this checks the generated text and the link-time rejections.
 */
@DisplayName("CountDownLatch, Semaphore, wait/notify and interrupt")
class ConcurrencyPrimitivesTest {
    @TempDir
    Path temporaryDirectory;

    private static String program(String board, String body) {
        return """
                package demo;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.concurrent.CountDownLatch;
                import java.util.concurrent.Semaphore;
                @Board(%1$s.class)
                public final class Gate {
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

    private static final String ALL_PRIMITIVES = """
            CountDownLatch latch = new CountDownLatch(1);
            latch.countDown();
            latch.await();
            Serial.println(latch.getCount() == 0 ? 1 : 0);
            Semaphore permits = new Semaphore(2);
            permits.acquire();
            permits.release();
            Serial.println(permits.tryAcquire() ? 1 : 0);
            Serial.println(permits.availablePermits());
            Object lock = new Object();
            synchronized (lock) {
                lock.notifyAll();
                lock.notify();
                lock.wait();
            }
            Thread worker = Thread.startVirtualThread(new Job());
            worker.interrupt();
            Serial.println(worker.isInterrupted() ? 1 : 0);
            Thread.sleep(1);
            worker.join();
            """;

    static Stream<Arguments> unsupportedCalls() {
        return Stream.of(
                arguments("Semaphore fairness", "Semaphore fair = new Semaphore(1, true);\n"),
                arguments("CountDownLatch timed await", "new CountDownLatch(1).await(1, java.util.concurrent.TimeUnit.SECONDS);\n"),
                arguments("Semaphore.acquire(int)", "new Semaphore(3).acquire(2);\n"));
    }

    private CompilationResult compile(String board, String body) throws Exception {
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Gate", program(board, body));
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Gate");
    }

    @Test
    @DisplayName("Every blocking primitive becomes one shim call")
    void shouldBecomeOneShimCallForEachPrimitive() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFiBoard", ALL_PRIMITIVES);

        // When & Then
        assertThat(result.assembly()).contains("bl juno_latch_new", "bl juno_latch_count_down",
                "bl juno_latch_await", "bl juno_latch_get_count", "bl juno_semaphore_new",
                "bl juno_semaphore_acquire", "bl juno_semaphore_try_acquire", "bl juno_semaphore_available",
                "bl juno_monitor_notify_all", "bl juno_monitor_notify", "bl juno_monitor_wait",
                "bl juno_thread_interrupt", "bl juno_thread_is_interrupted", "bl juno_thread_sleep_interruptible");
    }

    @Test
    @DisplayName("The runtime shim declares the primitives' entry points")
    void shouldDeclareThePrimitiveEntryPointsInTheRuntimeShim() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFiBoard", ALL_PRIMITIVES);

        // When & Then
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_monitor_wait(int32_t handle) {",
                "extern \"C\" void juno_semaphore_acquire(int32_t handle) {",
                "extern \"C\" void juno_thread_interrupt(int32_t handle) {",
                "extern \"C\" int64_t juno_latch_get_count(int32_t handle) {");
    }

    @Test
    @DisplayName("A program without the primitives pays nothing for them in its runtime shim")
    void shouldPayNothingForThePrimitivesInTheShimOfAProgramWithoutThem() throws Exception {
        // Given
        CompilerTestSupport.compileJava(temporaryDirectory, "demo.Plain", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoUnoR4WiFiBoard.class)
                public final class Plain {
                    public static void main() {
                        Serial.println(3);
                    }
                }
                """);

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Plain");

        // Then
        assertThat(result.runtimeShim()).doesNotContain("juno_latch", "juno_semaphore", "juno_monitor_wait",
                "juno_thread_interrupt");
    }

    @Test
    @DisplayName("wait and notify called on this resolve to the Object methods")
    void shouldResolveWaitAndNotifyCalledOnThisToTheObjectMethods() throws Exception {
        // Given
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Mailbox", """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.hardware.serial.Serial;
                @Board(ArduinoUnoR4WiFiBoard.class)
                public final class Mailbox {
                    private int value;
                    synchronized void put(int next) {
                        value = next;
                        notifyAll();
                    }
                    synchronized int take() throws InterruptedException {
                        while (value == 0) {
                            wait();
                        }
                        return value;
                    }
                    public static void main() throws Exception {
                        Mailbox mailbox = new Mailbox();
                        mailbox.put(3);
                        Serial.println(mailbox.take());
                    }
                }
                """);

        // When
        CompilationResult result = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Mailbox");

        // Then
        assertThat(result.assembly()).contains("bl juno_monitor_wait", "bl juno_monitor_notify_all");
    }

    @ParameterizedTest(name = "rejects {0} at link time")
    @MethodSource("unsupportedCalls")
    @DisplayName("Concurrency API outside the subset fails at link time")
    void shouldFailAtLinkTimeForConcurrencyApiOutsideTheSubset(String name, String body) throws Exception {
        // Given
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Gate",
                program("ArduinoUnoR4WiFiBoard", body));

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Gate"))
                .isInstanceOf(CompileException.class).hasMessageContaining("concurrency");
    }

    @ParameterizedTest(name = "{0} shim compiles as C++")
    @ValueSource(strings = {"ArduinoUnoR4WiFiBoard", "ArduinoUnoQBoard"})
    @DisplayName("Both ports' runtime shims compile as C++")
    void shouldCompileBothPortsRuntimeShimsAsCpp(String board) throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        Path shim = temporaryDirectory.resolve(board + ".cpp");
        Files.writeString(shim, compile(board, ALL_PRIMITIVES).runtimeShim(), StandardCharsets.UTF_8);

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
