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
 * The thread runtime behind preemptive virtual threads and cooperative {@code StructuredTaskScope} subtasks: the closed-world
 * {@code Runnable.run()} entry function, the runtime in the shim, each board's preemption tick, what a program
 * without subtasks does not pay for, and the rejection of {@code java.lang.Thread} itself. Behavior of the compiled
 * code is covered under QEMU and simavr ({@code src/test/qemu/programs}, notably {@code Preemption} and
 * {@code PreemptionStress}); this checks the generated text.
 */
class ThreadsTest {
    @TempDir
    Path temporaryDirectory;

    private static String program(String board, String body) {
        return """
                package demo;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.serial.Serial;
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
    void generatedCodeIsBracketedSoTheTickKnowsWhatItMayInterrupt() throws Exception {
        String assembly = compile("ArduinoUnoR4WiFi", START_AND_JOIN).assembly();

        int start = assembly.indexOf("juno_java_code_start:\n");
        int end = assembly.indexOf("juno_java_code_end:\n");
        assertThat(assembly).contains(".global juno_java_code_start", ".global juno_java_code_end");
        assertThat(start).isPositive().isLessThan(assembly.indexOf("juno_thread_entry:\n"));
        assertThat(end).isGreaterThan(assembly.lastIndexOf("    .type "));
    }

    @Test
    void unoR4PreemptsFromSysTickThroughATrampoline() throws Exception {
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        assertThat(shim).contains("preemptive scheduler", "SysTick_Handler:\\n", "\"  b juno_preempt_tick\\n\"",
                        "static void juno_port_preempt_start()", "SystemCoreClock / 1000u * JUNO_TIME_SLICE_MILLIS",
                        "juno_preempt_trampoline:\\n", "\"  vpush {s0-s15}\\n\"", "\"  pop {pc}\\n\"",
                        "extern \"C\" void juno_preempt_tick(uint32_t* frame) {",
                        "extern \"C\" uint32_t juno_thread_preempted() {")
                // The tick redirects only generated code outside an IT block / continuable LDM-STM.
                .contains("pc >= static_cast<uint32_t>(reinterpret_cast<uintptr_t>(juno_java_code_start))",
                        "(frame[7] & 0x0600FC00u) == 0u")
                .doesNotContain("k_timer", "juno_thread_safepoint");
    }

    @Test
    void unoQPreemptsFromAZephyrTimerReadingTheProcessStack() throws Exception {
        String shim = compile("ArduinoUnoQ", START_AND_JOIN).runtimeShim();

        assertThat(shim).contains("k_timer_init(&juno_preempt_timer, juno_preempt_expiry, nullptr);",
                        "k_timer_start(&juno_preempt_timer, K_MSEC(JUNO_TIME_SLICE_MILLIS)",
                        "__asm__ volatile(\"mrs %0, psp\" : \"=r\"(frame));", "juno_preempt_trampoline:\\n")
                .doesNotContain("SysTick_Handler", "juno_thread_safepoint");
    }

    @Test
    void structuredSubtasksAreCooperativeWhileVirtualThreadsArePreemptive() throws Exception {
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ", "ArduinoUnoWiFiRev2"}) {
            String shim = compile(board, START_AND_JOIN).runtimeShim();

            assertThat(shim).as(board).contains("static bool juno_slot_cooperative(uint32_t slot) {",
                    "return thread != nullptr && (thread->flags & JUNO_THREAD_TASK) != 0u;");
        }
        String arm = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();
        assertThat(arm).as("the tick never redirects a subtask; it switches at the subtask's next backedge")
                .contains("if (inJava && resumable && !juno_slot_cooperative(slot) && juno_preempt_resume[slot] == 0u) {");
        String avr = compile("ArduinoUnoWiFiRev2", START_AND_JOIN).runtimeShim();
        String safepoint = avr.substring(avr.indexOf("extern \"C\" void juno_thread_safepoint() {"));
        assertThat(safepoint.substring(0, safepoint.indexOf("\n}\n")))
                .as("a subtask skips the method-entry safepoint").contains("if (juno_slot_cooperative(juno_current_slot)) return;");
        String backedge = avr.substring(avr.indexOf("extern \"C\" void juno_thread_backedge() {"));
        assertThat(backedge.substring(0, backedge.indexOf("\n}\n")))
                .as("but every thread switches at a backedge once its slice is over")
                .contains("juno_thread_slice_over();").doesNotContain("juno_slot_cooperative");
    }

    @Test
    void theTickStartsWithTheFirstThreadAndABackedgeTakesADeferredSwitch() throws Exception {
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        String start = shim.substring(shim.indexOf("extern \"C\" void juno_thread_start(int32_t handle) {"));
        assertThat(start.substring(0, start.indexOf("\n}\n"))).contains("juno_port_preempt_start();");
        String backedge = shim.substring(shim.indexOf("extern \"C\" void juno_thread_backedge() {"));
        assertThat(backedge.substring(0, backedge.indexOf("\n}\n")))
                .contains("yield();", "juno_preempt_due", "juno_sched_block();")
                .as("no millisecond polling: the tick decides when a slice is over").doesNotContain("millis()");
    }

    @Test
    void wideFieldsAreReadAndWrittenWithInterruptsMaskedOnlyWithThreads() throws Exception {
        String body = START_AND_JOIN + "total = total + 3_000_000_000L;\nSerial.println(total);\n";
        String threaded = programWithLongField("ArduinoUnoR4WiFi", body);
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Wide", threaded);
        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wide").assembly();

        assertThat(assembly).contains("    mrs r12, primask\n    cpsid i\n    ldr r1, [r0, #0]\n    ldr r2, [r0, #4]\n"
                        + "    msr primask, r12\n")
                .contains("    mrs r12, primask\n    cpsid i\n    str r2, [r1]\n    str r3, [r1, #4]\n"
                        + "    msr primask, r12\n");

        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Wide",
                programWithLongField("ArduinoUnoR4WiFi", "total = total + 3_000_000_000L;\nSerial.println(total);\n"));
        assertThat(CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wide").assembly())
                .doesNotContain("cpsid", "primask", "juno_java_code_start");
    }

    private static String programWithLongField(String board, String body) {
        return program(board, body).replace("public final class Worker {",
                "public final class Wide {\n    static long total = 1L;").replace("demo.Worker", "demo.Wide");
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
                import io.github.jabrena.juno.api.io.serial.Serial;
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

    private static final String VIRTUAL_THREAD = """
            Thread worker = Thread.startVirtualThread(new Job());
            Thread.yield();
            worker.join();
            Serial.println(worker.isAlive() ? 1 : 0);
            """;

    @Test
    void virtualThreadsStartAndJoinOnBothBoards() throws Exception {
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            CompilationResult result = compile(board, VIRTUAL_THREAD);

            assertThat(result.assembly()).as("%s: a virtual thread needs no structured-task runtime", board)
                    .contains("bl juno_thread_start_virtual", "bl juno_thread_join", "bl juno_thread_is_alive",
                            ".global juno_thread_entry", "juno_thread_entry:")
                    .doesNotContain("bl juno_task_scope");
            assertThat(result.runtimeShim()).as(board)
                    .contains("extern \"C\" int32_t juno_thread_start_virtual(int32_t runnable)",
                            "juno_thread_set_daemon(handle, 1);");
        }
        assertThat(compile("ArduinoUnoQ", VIRTUAL_THREAD).runtimeShim())
                .contains("#include <zephyr/kernel.h>", "UNO Q port", "k_thread_create");
    }

    @Test
    void userLevelThreadsAreRejectedAtLinkTime() throws Exception {
        for (String body : new String[]{
                "Thread job = new Thread(new Job());\njob.start();\njob.join();\n",
                "Thread job = new Thread(new Job());\njob.setDaemon(true);\n",
                "Thread job = Thread.ofVirtual().start(new Job());\njob.join();\n",
                "Thread job = Thread.currentThread();\njob.interrupt();\n"}) {
            CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", program("ArduinoUnoR4WiFi", body));

            assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker"))
                    .isInstanceOf(CompileException.class)
                    .hasMessageContaining("does not support java.lang.Thread")
                    .hasMessageContaining("Thread.startVirtualThread");
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
