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
        assertThat(shim).as("an overflow is caught by the stack pointer too, and reported from a stack of its own")
                .contains("bool below = reinterpret_cast<uintptr_t>(&marker) < reinterpret_cast<uintptr_t>(canary + 2);",
                        "__asm__ volatile(\"mov sp, %0\"");
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
    void avrContextSwitchesPreserveTheNativeHeapBoundary() throws Exception {
        String shim = compile("ArduinoUnoR3WiFiRev2", START_AND_JOIN).runtimeShim();

        assertThat(shim).contains("extern char* __malloc_heap_end;",
                "juno_main_malloc_heap_end = __malloc_heap_end;",
                "__malloc_heap_end = juno_main_malloc_heap_end;",
                "static void juno_port_switch(uint32_t from, uint32_t to) {\n"
                        + "  juno_port_check_stack(from);\n"
                        + "  juno_port_set_limit(to);\n"
                        + "  juno_port_malloc_switch(from, to, SP - JUNO_SWITCH_FRAME_BYTES - JUNO_RETURN_ADDRESS_BYTES);",
                "static void juno_port_exit(uint32_t from, uint32_t to) {\n"
                        + "  juno_port_set_limit(to);\n"
                        + "  juno_port_malloc_switch(from, to, SP - JUNO_SWITCH_FRAME_BYTES - JUNO_RETURN_ADDRESS_BYTES);");
    }

    @Test
    void structuredSubtasksAreCooperativeWhileVirtualThreadsArePreemptive() throws Exception {
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ", "ArduinoUnoR3WiFiRev2"}) {
            String shim = compile(board, START_AND_JOIN).runtimeShim();

            assertThat(shim).as(board).contains("static bool juno_slot_cooperative(uint32_t slot) {",
                    "return thread != nullptr && (thread->flags & JUNO_THREAD_TASK) != 0u;");
        }
        String arm = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();
        assertThat(arm).as("the tick never redirects a subtask; it switches at the subtask's next backedge")
                .contains("if (inJava && resumable && !juno_slot_cooperative(slot) && juno_preempt_resume[slot] == 0u) {");
        String avr = compile("ArduinoUnoR3WiFiRev2", START_AND_JOIN).runtimeShim();
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

        assertThat(assembly).contains("""
                            mrs r12, primask
                            cpsid i
                            ldr r1, [r0, #0]
                            ldr r2, [r0, #4]
                            msr primask, r12
                        """)
                .contains("""
                            mrs r12, primask
                            cpsid i
                            str r2, [r1]
                            str r3, [r1, #4]
                            msr primask, r12
                        """);

        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Wide",
                programWithLongField("ArduinoUnoR4WiFi", "total = total + 3_000_000_000L;\nSerial.println(total);\n"));
        assertThat(CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wide").assembly())
                .doesNotContain("cpsid", "primask", "juno_java_code_start");
    }

    private static String programWithLongField(String board, String body) {
        return program(board, body).replace("public final class Worker {",
                "public final class Wide {\n    static long total = 1L;").replace("demo.Worker", "demo.Wide");
    }

    private String httpProgramShim(String board, boolean threads) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.%s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.serial.Serial;
                import io.github.jabrena.juno.api.net.http.HttpClient;
                @Board(%s.class)
                public final class Fetch {
                    public static void main() throws Exception {
                        byte[] body = new byte[32];
                        byte[] headers = new byte[32];
                        int[] status = new int[2];
                        %s
                        Serial.println(HttpClient.get("http://example.com/", body, body.length, headers, headers.length,
                                status));
                    }
                }
                """.formatted(board, board, threads ? "Thread.startVirtualThread(() -> Serial.println(1)).join();" : "");
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Fetch", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Fetch").runtimeShim();
    }

    private String parallelHttpsProgramShim(String board) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.net.http.HttpClient;
                @Board(%1$s.class)
                public final class ParallelFetch {
                    static final class First {
                        private final byte[] body = new byte[32];
                        private final byte[] headers = new byte[32];
                        private final int[] status = new int[2];
                        First fetch() {
                            HttpClient.get("https://one.example/", body, body.length, headers, headers.length, status);
                            return this;
                        }
                    }
                    static final class Second {
                        private final byte[] body = new byte[32];
                        private final byte[] headers = new byte[32];
                        private final int[] status = new int[2];
                        Second fetch() {
                            HttpClient.get("https://two.example/", body, body.length, headers, headers.length, status);
                            return this;
                        }
                    }
                    public static void main() throws Exception {
                        First first = new First();
                        Second second = new Second();
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            var firstTask = scope.fork(() -> first.fetch());
                            var secondTask = scope.fork(() -> second.fetch());
                            scope.join();
                            firstTask.get();
                            secondTask.get();
                        }
                    }
                }
                """.formatted(board);
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.ParallelFetch", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.ParallelFetch").runtimeShim();
    }

    @Test
    void twoHttpsSubtasksRespectTheCoreTransportLimit() throws Exception {
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoR3WiFiRev2", "ArduinoUnoQ"}) {
            String shim = parallelHttpsProgramShim(board);

            int maxHttpRequests = board.equals("ArduinoUnoR3WiFiRev2") ? 1 : 2;
            assertThat(shim).as(board).contains("static constexpr uint32_t JUNO_HTTP_MAX_IN_FLIGHT = " + maxHttpRequests + "u;",
                            "extern \"C\" uint32_t juno_http_acquire(void* handle, void (*abort)(void*))",
                            "struct JunoHttpGuard {", "JunoHttpGuard guard(",
                            "extern \"C\" int32_t juno_task_entry(int32_t callable);")
                    .doesNotContain("JunoIoGuard guard(JUNO_IO_NETWORK, &client,");
            if (board.equals("ArduinoUnoQ")) {
                assertThat(shim).contains("static constexpr uint32_t JUNO_IO_WORKER_COUNT = 2u;",
                        "static bool juno_io_waiting(uint32_t slot)");
            }
        }
    }

    @Test
    void anHttpRequestWaitsWithoutHoldingUpTheOtherThreads() throws Exception {
        // The UNO Q has no plain-HTTP client; its HTTPS client shares the same request code.
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoR3WiFiRev2"}) {
            String shim = httpProgramShim(board, true);

            assertThat(shim).as(board)
                    .contains("juno_thread_sleep(static_cast<int64_t>(millisToWait));",
                            "JunoHttpGuard guard(&client,", "juno_io_wait(1);",
                            "extern \"C\" void juno_io_acquire(uint32_t device, void* handle, void (*abort)(void*)) {")
                    .doesNotContain("delay(1);");
        }
    }

    @Test
    void withoutThreadsAWaitIsThePlainDelayAndTheGuardIsFree() throws Exception {
        String shim = httpProgramShim("ArduinoUnoR4WiFi", false);

        assertThat(shim).contains("static void juno_io_wait(uint32_t millisToWait) {\n  delay(millisToWait);",
                "static void juno_io_acquire(uint32_t, void*, void (*)(void*)) {")
                .doesNotContain("juno_thread_sleep", "juno_io_owners");
    }

    @Test
    void cancellingASubtaskAbortsAndReleasesTheDevicesItHolds() throws Exception {
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        String cancel = shim.substring(shim.indexOf("static void juno_task_cancel(JunoThreadObject* task) {"));
        assertThat(cancel.substring(0, cancel.indexOf("\n}\n")))
                .containsSubsequence("juno_task_release_monitors(slot + 1u);", "juno_io_release_owned(slot + 1u);",
                        "juno_port_cancel(slot);");
        assertThat(shim).contains("if (entry.abort != nullptr) entry.abort(entry.handle);");
    }

    private String unoQNetworkShim(boolean threads) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.io.serial.Serial;
                import io.github.jabrena.juno.api.net.Wifi;
                import io.github.jabrena.juno.api.net.http.HttpClient;
                @Board(ArduinoUnoQ.class)
                public final class Fetch {
                    public static void main() throws Exception {
                        byte[] body = new byte[32];
                        byte[] headers = new byte[32];
                        int[] status = new int[2];
                        %s
                        Wifi.begin("juno", "secret");
                        Serial.println(HttpClient.get("https://example.com/", body, body.length, headers,
                                headers.length, status));
                    }
                }
                """.formatted(threads ? """
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(() -> Serial.println(1));
                            scope.join();
                        }""" : "");
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Fetch", source);
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Fetch").runtimeShim();
    }

    @Test
    void unoQRunsBridgeCallsOnTheIoWorkerWhileTheCallerIsParked() throws Exception {
        String shim = unoQNetworkShim(true);

        assertThat(shim).contains("K_THREAD_STACK_DEFINE(juno_io_worker_stack_first, JUNO_IO_WORKER_STACK_BYTES);",
                        "K_THREAD_STACK_DEFINE(juno_io_worker_stack_second, JUNO_IO_WORKER_STACK_BYTES);",
                        "static constexpr uint32_t JUNO_IO_WORKER_COUNT = 2u;",
                        "K_PRIO_PREEMPT(4)", "extern \"C\" void juno_io_offload(void (*job)(void*), void* argument) {",
                        "juno_slots[juno_current_slot].state = JUNO_SLOT_WAITING_IO;",
                        "case JUNO_SLOT_WAITING_IO:\n                      return !juno_io_waiting(index);",
                        "juno_io_offload(juno_wifi_bridge_begin, nullptr);",
                        "juno_io_offload(juno_http_job<Client>, &job);",
                        "using JunoHttpsClient = JunoBridgeSSLClient;")
                .as("short text the worker reads later is copied off the rotating string slots")
                .contains("job.host = juno_io_stable_text(host, hostCopy);",
                        "job.path = juno_io_stable_text(path, pathCopy);")
                .as("a caller cancelled during its call keeps its stack scanned until the worker is done")
                .contains("if (draining) juno_slots[slot].state = JUNO_SLOT_DRAINING;", "static void juno_io_reap() {")
                .as("on the worker, guards are its caller's").contains("if (juno_io_on_worker()) return;");
    }

    @Test
    void theIoWorkerExistsOnlyForAThreadedUnoQProgramWithNetworkCalls() throws Exception {
        assertThat(unoQNetworkShim(false)).doesNotContain("juno_io_worker", "juno_io_stable_text")
                .contains("static void juno_io_offload(void (*job)(void*), void* argument) {\n  job(argument);");
        assertThat(compile("ArduinoUnoQ", START_AND_JOIN).runtimeShim()).doesNotContain("juno_io_worker");
        assertThat(httpProgramShim("ArduinoUnoR4WiFi", true))
                .doesNotContain("juno_io_worker", "JUNO_SLOT_WAITING_IO\n");
    }

    @Test
    void theUnoQIoWorkerShimCompilesAsCpp() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        Path shim = temporaryDirectory.resolve("UnoQWorker.cpp");
        Files.writeString(shim, unoQNetworkShim(true), StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("UNO Q worker shim:%n%s", diagnostics).isZero();
    }

    @Test
    void delayBecomesASleepThatLetsOtherThreadsRun() throws Exception {
        String body = START_AND_JOIN + "io.github.jabrena.juno.api.util.Delay.millis(10);\n";

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
    void parallelHttpsShimsCompileAsCppOnArmBoards() throws Exception {
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        for (String board : new String[]{"ArduinoUnoR4WiFi", "ArduinoUnoQ"}) {
            Path shim = temporaryDirectory.resolve(board + ".cpp");
            Files.writeString(shim, parallelHttpsProgramShim(board), StandardCharsets.UTF_8);
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
