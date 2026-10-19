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
 * The thread runtime behind preemptive virtual threads and cooperative {@code StructuredTaskScope} subtasks: the closed-world
 * {@code Runnable.run()} entry function, the runtime in the shim, each board's preemption tick, what a program
 * without subtasks does not pay for, and the rejection of {@code java.lang.Thread} itself. Behavior of the compiled
 * code is covered under QEMU and simavr ({@code src/test/qemu/programs}, notably {@code Preemption} and
 * {@code PreemptionStress}); this checks the generated text.
 */
@DisplayName("Thread runtime and cooperative subtasks")
class ThreadsTest {
    @TempDir
    Path temporaryDirectory;

    private static String program(String board, String body) {
        return """
                package demo;
                import io.github.jabrena.juno.annotations.%1$s;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.hardware.io.serial.Serial;
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

    private static final String VIRTUAL_THREAD = """
            Thread worker = Thread.startVirtualThread(new Job());
            Thread.yield();
            worker.join();
            Serial.println(worker.isAlive() ? 1 : 0);
            """;

    static Stream<String> boards() {
        return Stream.of("ArduinoUnoR4WiFi", "ArduinoUnoQ");
    }

    static Stream<String> cooperativeBoards() {
        return Stream.of("ArduinoUnoR4WiFi", "ArduinoUnoQ", "ArduinoUnoR3WiFiRev2");
    }

    static Stream<String> userLevelThreadBodies() {
        return Stream.of(
                "Thread job = new Thread(new Job());\njob.start();\njob.join();\n",
                "Thread job = new Thread(new Job());\njob.setDaemon(true);\n",
                "Thread job = Thread.ofVirtual().start(new Job());\njob.join();\n",
                "Thread job = Thread.currentThread();\njob.interrupt();\n");
    }

    static Stream<Arguments> httpSubtaskBoards() {
        return Stream.of(
                arguments("ArduinoUnoR4WiFi", 2),
                arguments("ArduinoUnoR3WiFiRev2", 1),
                arguments("ArduinoUnoQ", 2));
    }

    private CompilationResult compile(String board, String body) throws Exception {
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", program(board, body));
        return CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker");
    }

    @Test
    @DisplayName("A started thread enters its runnable through an exported dispatch function")
    void shouldEnterAStartedThreadsRunnableThroughAnExportedDispatchFunction() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", START_AND_JOIN);

        // When & Then
        assertThat(result.assembly()).contains(".global juno_thread_entry", "juno_thread_entry:")
                .as("loop backedges go through the thread runtime").doesNotContain("bl yield\n");
        assertThat(result.assembly()).contains("bl juno_task_scope_fork_runnable", "bl juno_thread_main_exit");
    }

    @Test
    @DisplayName("The runtime shim declares the thread entry and the stack scan")
    void shouldDeclareTheThreadEntryAndTheStackScanInTheShim() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", START_AND_JOIN);

        // When & Then
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_thread_entry(int32_t runnable);",
                "juno_thread_gc_scan(&stackMarker);");
    }

    @Test
    @DisplayName("The UNO R4 switches stacks with its own context switch")
    void shouldSwitchStacksOnTheUnoR4WithItsOwnContextSwitch() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        // When & Then
        assertThat(shim).contains("juno_context_switch:", "UNO R4 port").doesNotContain("zephyr/kernel.h");
    }

    @Test
    @DisplayName("The UNO R4 catches an overflow with the stack pointer and reports it from its own stack")
    void shouldCatchAnOverflowOnTheUnoR4WithTheStackPointer() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        // When & Then
        assertThat(shim).as("an overflow is caught by the stack pointer too, and reported from a stack of its own")
                .contains("bool below = reinterpret_cast<uintptr_t>(&marker) < reinterpret_cast<uintptr_t>(canary + 2);",
                        "__asm__ volatile(\"mov sp, %0\"");
    }

    @Test
    @DisplayName("The UNO Q runs each thread on a Zephyr thread passing a baton")
    void shouldRunEachThreadOnAZephyrThreadPassingABaton() throws Exception {
        // Given
        String shim = compile("ArduinoUnoQ", START_AND_JOIN).runtimeShim();

        // When & Then
        assertThat(shim).contains("#include <zephyr/kernel.h>", "UNO Q port", "k_thread_create", "k_sem_give")
                .doesNotContain("juno_context_switch");
    }

    @Test
    @DisplayName("Generated code is bracketed so the tick knows what it may interrupt")
    void shouldBracketGeneratedCodeSoTheTickKnowsWhatItMayInterrupt() throws Exception {
        // Given
        String assembly = compile("ArduinoUnoR4WiFi", START_AND_JOIN).assembly();

        // When
        int start = assembly.indexOf("juno_java_code_start:\n");
        int end = assembly.indexOf("juno_java_code_end:\n");

        // Then
        assertThat(assembly).contains(".global juno_java_code_start", ".global juno_java_code_end");
        assertThat(start).isPositive().isLessThan(assembly.indexOf("juno_thread_entry:\n"));
        assertThat(end).isGreaterThan(assembly.lastIndexOf("    .type "));
    }

    @Test
    @DisplayName("The UNO R4 preempts from SysTick through a trampoline")
    void shouldPreemptFromSysTickThroughATrampolineOnTheUnoR4() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        // When & Then
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
    @DisplayName("The UNO Q preempts from a Zephyr timer reading the process stack")
    void shouldPreemptFromAZephyrTimerReadingTheProcessStackOnTheUnoQ() throws Exception {
        // Given
        String shim = compile("ArduinoUnoQ", START_AND_JOIN).runtimeShim();

        // When & Then
        assertThat(shim).contains("k_timer_init(&juno_preempt_timer, juno_preempt_expiry, nullptr);",
                        "k_timer_start(&juno_preempt_timer, K_MSEC(JUNO_TIME_SLICE_MILLIS)",
                        "__asm__ volatile(\"mrs %0, psp\" : \"=r\"(frame));", "juno_preempt_trampoline:\\n")
                .doesNotContain("SysTick_Handler", "juno_thread_safepoint");
    }

    @Test
    @DisplayName("AVR context switches preserve the native heap boundary")
    void shouldPreserveTheNativeHeapBoundaryOnAvrContextSwitches() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR3WiFiRev2", START_AND_JOIN).runtimeShim();

        // When & Then
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

    @ParameterizedTest(name = "structured subtasks are cooperative on {0}")
    @MethodSource("cooperativeBoards")
    @DisplayName("Structured subtasks are cooperative while virtual threads are preemptive")
    void shouldKeepStructuredSubtasksCooperativeOnEveryBoard(String board) throws Exception {
        // Given
        String shim = compile(board, START_AND_JOIN).runtimeShim();

        // When & Then
        assertThat(shim).as(board).contains("static bool juno_slot_cooperative(uint32_t slot) {",
                "return thread != nullptr && (thread->flags & JUNO_THREAD_TASK) != 0u;");
    }

    @Test
    @DisplayName("The tick never redirects a subtask; it switches at the subtask's next backedge")
    void shouldNeverRedirectASubtaskFromTheTickOnTheUnoR4() throws Exception {
        // Given
        String arm = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        // When & Then
        assertThat(arm).contains("if (inJava && resumable && !juno_slot_cooperative(slot) && juno_preempt_resume[slot] == 0u) {");
    }

    @Test
    @DisplayName("A subtask skips the method-entry safepoint on AVR")
    void shouldSkipTheMethodEntrySafepointForASubtaskOnAvr() throws Exception {
        // Given
        String avr = compile("ArduinoUnoR3WiFiRev2", START_AND_JOIN).runtimeShim();
        String safepoint = avr.substring(avr.indexOf("extern \"C\" void juno_thread_safepoint() {"));

        // When & Then
        assertThat(safepoint.substring(0, safepoint.indexOf("\n}\n")))
                .contains("if (juno_slot_cooperative(juno_current_slot)) return;");
    }

    @Test
    @DisplayName("Every thread switches at a backedge once its slice is over on AVR")
    void shouldSwitchEveryThreadAtABackedgeOnceItsSliceIsOverOnAvr() throws Exception {
        // Given
        String avr = compile("ArduinoUnoR3WiFiRev2", START_AND_JOIN).runtimeShim();
        String backedge = avr.substring(avr.indexOf("extern \"C\" void juno_thread_backedge() {"));

        // When & Then
        assertThat(backedge.substring(0, backedge.indexOf("\n}\n")))
                .contains("juno_thread_slice_over();").doesNotContain("juno_slot_cooperative");
    }

    @Test
    @DisplayName("The tick starts with the first thread")
    void shouldStartTheTickWithTheFirstThread() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();
        String start = shim.substring(shim.indexOf("extern \"C\" void juno_thread_start(int32_t handle) {"));

        // When & Then
        assertThat(start.substring(0, start.indexOf("\n}\n"))).contains("juno_port_preempt_start();");
    }

    @Test
    @DisplayName("A backedge takes a deferred switch with no millisecond polling")
    void shouldTakeADeferredSwitchAtABackedgeWithNoMillisecondPolling() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();
        String backedge = shim.substring(shim.indexOf("extern \"C\" void juno_thread_backedge() {"));

        // When & Then
        assertThat(backedge.substring(0, backedge.indexOf("\n}\n")))
                .contains("yield();", "juno_preempt_due", "juno_sched_block();")
                .as("no millisecond polling: the tick decides when a slice is over").doesNotContain("millis()");
    }

    @Test
    @DisplayName("Wide fields are read and written with interrupts masked when threads are present")
    void shouldMaskInterruptsAroundWideFieldAccessWhenThreadsArePresent() throws Exception {
        // Given
        String body = START_AND_JOIN + "total = total + 3_000_000_000L;\nSerial.println(total);\n";
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Wide", programWithLongField("ArduinoUnoR4WiFi", body));

        // When
        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wide").assembly();

        // Then
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
    }

    @Test
    @DisplayName("Wide fields need no interrupt masking without threads")
    void shouldNotMaskInterruptsAroundWideFieldAccessWithoutThreads() throws Exception {
        // Given
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Wide",
                programWithLongField("ArduinoUnoR4WiFi", "total = total + 3_000_000_000L;\nSerial.println(total);\n"));

        // When
        String assembly = CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Wide").assembly();

        // Then
        assertThat(assembly).doesNotContain("cpsid", "primask", "juno_java_code_start");
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
                import io.github.jabrena.juno.api.hardware.io.serial.Serial;
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

    @ParameterizedTest(name = "two HTTPS subtasks respect the transport limit on {0}")
    @MethodSource("httpSubtaskBoards")
    @DisplayName("Two HTTPS subtasks respect the core transport limit")
    void shouldLimitTwoHttpsSubtasksToTheCoreTransportLimit(String board, int maxHttpRequests) throws Exception {
        // Given
        String shim = parallelHttpsProgramShim(board);

        // When & Then
        assertThat(shim).as(board).contains("static constexpr uint32_t JUNO_HTTP_MAX_IN_FLIGHT = " + maxHttpRequests + "u;",
                        "extern \"C\" uint32_t juno_http_acquire(void* handle, void (*abort)(void*))",
                        "struct JunoHttpGuard {", "JunoHttpGuard guard(",
                        "extern \"C\" int32_t juno_task_entry(int32_t callable);")
                .doesNotContain("JunoIoGuard guard(JUNO_IO_NETWORK, &client,");
    }

    @Test
    @DisplayName("The UNO Q HTTPS subtasks use two I/O workers and a waiting predicate")
    void shouldUseTwoIoWorkersAndAWaitingPredicateForHttpsSubtasksOnTheUnoQ() throws Exception {
        // Given
        String shim = parallelHttpsProgramShim("ArduinoUnoQ");

        // When & Then
        assertThat(shim).contains("static constexpr uint32_t JUNO_IO_WORKER_COUNT = 2u;",
                "static bool juno_io_waiting(uint32_t slot)");
    }

    @ParameterizedTest(name = "an HTTP request waits without holding up the other threads on {0}")
    @ValueSource(strings = {"ArduinoUnoR4WiFi", "ArduinoUnoR3WiFiRev2"})
    @DisplayName("An HTTP request waits without holding up the other threads")
    void shouldWaitForAnHttpRequestWithoutHoldingUpTheOtherThreads(String board) throws Exception {
        // Given
        String shim = httpProgramShim(board, true);

        // When & Then
        assertThat(shim).as(board)
                .contains("juno_thread_sleep(static_cast<int64_t>(millisToWait));",
                        "JunoHttpGuard guard(&client,", "juno_io_wait(1);",
                        "extern \"C\" void juno_io_acquire(uint32_t device, void* handle, void (*abort)(void*)) {")
                .doesNotContain("delay(1);");
    }

    @Test
    @DisplayName("Without threads a wait is the plain delay and the guard is free")
    void shouldMakeAWaitThePlainDelayAndTheGuardFreeWithoutThreads() throws Exception {
        // Given
        String shim = httpProgramShim("ArduinoUnoR4WiFi", false);

        // When & Then
        assertThat(shim).contains("static void juno_io_wait(uint32_t millisToWait) {\n  delay(millisToWait);",
                "static void juno_io_acquire(uint32_t, void*, void (*)(void*)) {")
                .doesNotContain("juno_thread_sleep", "juno_io_owners");
    }

    @Test
    @DisplayName("Cancelling a subtask aborts it and releases the devices it holds")
    void shouldAbortASubtaskAndReleaseItsDevicesWhenCancelled() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();
        String cancel = shim.substring(shim.indexOf("static void juno_task_cancel(JunoThreadObject* task) {"));

        // When & Then
        assertThat(cancel.substring(0, cancel.indexOf("\n}\n")))
                .containsSubsequence("juno_task_release_monitors(slot + 1u);", "juno_io_release_owned(slot + 1u);",
                        "juno_port_cancel(slot);");
    }

    @Test
    @DisplayName("The cancellation path calls the subtask's abort hook")
    void shouldCallTheSubtaskAbortHookOnCancellation() throws Exception {
        // Given
        String shim = compile("ArduinoUnoR4WiFi", START_AND_JOIN).runtimeShim();

        // When & Then
        assertThat(shim).contains("if (entry.abort != nullptr) entry.abort(entry.handle);");
    }

    private String unoQNetworkShim(boolean threads) throws Exception {
        String source = """
                package demo;
                import io.github.jabrena.juno.annotations.ArduinoUnoQ;
                import io.github.jabrena.juno.annotations.Board;
                import io.github.jabrena.juno.api.hardware.io.serial.Serial;
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
    @DisplayName("The UNO Q runs bridge calls on the I/O worker while the caller is parked")
    void shouldRunBridgeCallsOnTheIoWorkerWhileTheCallerIsParkedOnTheUnoQ() throws Exception {
        // Given
        String shim = unoQNetworkShim(true);

        // When & Then
        assertThat(shim).contains("K_THREAD_STACK_DEFINE(juno_io_worker_stack_first, JUNO_IO_WORKER_STACK_BYTES);",
                        "K_THREAD_STACK_DEFINE(juno_io_worker_stack_second, JUNO_IO_WORKER_STACK_BYTES);",
                        "static constexpr uint32_t JUNO_IO_WORKER_COUNT = 2u;",
                        "K_PRIO_PREEMPT(4)", "extern \"C\" void juno_io_offload(void (*job)(void*), void* argument) {",
                        "juno_slots[juno_current_slot].state = JUNO_SLOT_WAITING_IO;",
                        "case JUNO_SLOT_WAITING_IO:\n                      return !juno_io_waiting(index);",
                        "juno_io_offload(juno_wifi_bridge_begin, nullptr);",
                        "juno_io_offload(juno_http_job<Client>, &job);",
                        "using JunoHttpsClient = JunoBridgeSSLClient;");
    }

    @Test
    @DisplayName("Short text the worker reads later is copied off the rotating string slots")
    void shouldCopyShortTextOffTheRotatingStringSlotsForTheWorker() throws Exception {
        // Given
        String shim = unoQNetworkShim(true);

        // When & Then
        assertThat(shim).as("short text the worker reads later is copied off the rotating string slots")
                .contains("job.host = juno_io_stable_text(host, hostCopy);",
                        "job.path = juno_io_stable_text(path, pathCopy);");
    }

    @Test
    @DisplayName("A cancelled caller keeps its stack scanned until the worker is done")
    void shouldKeepACancelledCallersStackScannedUntilTheWorkerIsDone() throws Exception {
        // Given
        String shim = unoQNetworkShim(true);

        // When & Then
        assertThat(shim).as("a caller cancelled during its call keeps its stack scanned until the worker is done")
                .contains("if (draining) juno_slots[slot].state = JUNO_SLOT_DRAINING;", "static void juno_io_reap() {");
    }

    @Test
    @DisplayName("On the worker, guards belong to its caller")
    void shouldLeaveGuardsToTheCallerOnTheWorker() throws Exception {
        // Given
        String shim = unoQNetworkShim(true);

        // When & Then
        assertThat(shim).as("on the worker, guards are its caller's").contains("if (juno_io_on_worker()) return;");
    }

    @Test
    @DisplayName("The I/O worker exists only for a threaded UNO Q program with network calls")
    void shouldIncludeTheIoWorkerOnlyForAThreadedUnoQProgramWithNetworkCalls() throws Exception {
        // When & Then
        assertThat(unoQNetworkShim(false)).doesNotContain("juno_io_worker", "juno_io_stable_text")
                .contains("static void juno_io_offload(void (*job)(void*), void* argument) {\n  job(argument);");
    }

    @Test
    @DisplayName("A threaded UNO Q program without network calls has no I/O worker")
    void shouldOmitTheIoWorkerForAThreadedUnoQProgramWithoutNetworkCalls() throws Exception {
        // When & Then
        assertThat(compile("ArduinoUnoQ", START_AND_JOIN).runtimeShim()).doesNotContain("juno_io_worker");
    }

    @Test
    @DisplayName("A threaded R4 HTTP program has no I/O worker")
    void shouldOmitTheIoWorkerForAThreadedR4HttpProgram() throws Exception {
        // When & Then
        assertThat(httpProgramShim("ArduinoUnoR4WiFi", true)).doesNotContain("juno_io_worker", "JUNO_SLOT_WAITING_IO\n");
    }

    @Test
    @DisplayName("The UNO Q I/O worker shim compiles as C++")
    void shouldCompileTheUnoQIoWorkerShimAsCpp() throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        Path shim = temporaryDirectory.resolve("UnoQWorker.cpp");
        Files.writeString(shim, unoQNetworkShim(true), StandardCharsets.UTF_8);

        // When
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources", shim.toString()).redirectErrorStream(true).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();

        // Then
        String diagnostics = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("UNO Q worker shim:%n%s", diagnostics).isZero();
    }

    @Test
    @DisplayName("Delay becomes a sleep that lets other threads run")
    void shouldTurnDelayIntoASleepThatLetsOtherThreadsRun() throws Exception {
        // Given
        String body = START_AND_JOIN + "io.github.jabrena.juno.api.util.Delay.millis(10);\n";
        CompilationResult result = compile("ArduinoUnoR4WiFi", body);

        // When & Then
        assertThat(result.assembly()).contains("bl juno_thread_delay").doesNotContain("bl delay\n");
    }

    @Test
    @DisplayName("Sleep and yield alone need no scheduler")
    void shouldNeedNoSchedulerForSleepAndYieldAlone() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", "Thread.sleep(5);\nThread.yield();\n");

        // When & Then
        assertThat(result.assembly()).contains("bl juno_thread_sleep", "bl juno_thread_yield")
                .doesNotContain("juno_thread_entry", "juno_thread_backedge");
    }

    @Test
    @DisplayName("Sleep and yield alone declare the sleep function without scheduler slots")
    void shouldDeclareTheSleepFunctionWithoutSchedulerSlots() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", "Thread.sleep(5);\nThread.yield();\n");

        // When & Then
        assertThat(result.runtimeShim()).contains("extern \"C\" void juno_thread_sleep(int64_t millis)")
                .doesNotContain("juno_slots", "juno_context_switch");
    }

    @Test
    @DisplayName("Programs without threads pay nothing for them")
    void shouldPayNothingForThreadsInAProgramWithoutThreads() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", "Serial.println(3);\n");

        // When & Then
        assertThat(result.assembly()).doesNotContain("juno_thread");
    }

    @Test
    @DisplayName("A program without threads has no thread runtime in its shim")
    void shouldOmitTheThreadRuntimeFromTheShimOfAProgramWithoutThreads() throws Exception {
        // Given
        CompilationResult result = compile("ArduinoUnoR4WiFi", "Serial.println(3);\n");

        // When & Then
        assertThat(result.runtimeShim()).doesNotContain("juno_thread", "juno_slots");
    }

    @Test
    @DisplayName("A fork with no runnable reachable anywhere is rejected")
    void shouldRejectAForkWithNoRunnableReachableAnywhere() throws Exception {
        // Given
        String source = """
                package demo;
                import io.github.jabrena.juno.api.hardware.io.serial.Serial;
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

        // When & Then
        // spawn() is unreachable, so nothing asks for a subtask
        assertThat(CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker").runtimeShim())
                .doesNotContain("juno_thread");
    }

    @Test
    @DisplayName("A reachable fork with no runnable is rejected at link time")
    void shouldRejectAReachableForkWithNoRunnableAtLinkTime() throws Exception {
        // Given
        String reachable = """
                package demo;
                import io.github.jabrena.juno.api.hardware.io.serial.Serial;
                public final class Worker {
                    static void spawn(Runnable work) throws Exception {
                        try (var scope = java.util.concurrent.StructuredTaskScope.open()) {
                            scope.fork(work);
                            scope.join();
                        }
                    }
                    public static void main() throws Exception {
                        spawn(null);
                    }
                }
                """;
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", reachable);

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker"))
                .isInstanceOf(CompileException.class).hasMessageContaining("no reachable class or lambda");
    }

    @ParameterizedTest(name = "virtual threads start and join on {0}")
    @MethodSource("boards")
    @DisplayName("Virtual threads start and join on both boards")
    void shouldStartAndJoinVirtualThreadsOnBothBoards(String board) throws Exception {
        // Given
        CompilationResult result = compile(board, VIRTUAL_THREAD);

        // When & Then
        assertThat(result.assembly()).as("%s: a virtual thread needs no structured-task runtime", board)
                .contains("bl juno_thread_start_virtual", "bl juno_thread_join", "bl juno_thread_is_alive",
                        ".global juno_thread_entry", "juno_thread_entry:")
                .doesNotContain("bl juno_task_scope");
    }

    @ParameterizedTest(name = "virtual thread shim on {0}")
    @MethodSource("boards")
    @DisplayName("The runtime shim declares the virtual-thread start and daemon functions")
    void shouldDeclareTheVirtualThreadStartAndDaemonFunctionsInTheShim(String board) throws Exception {
        // Given
        CompilationResult result = compile(board, VIRTUAL_THREAD);

        // When & Then
        assertThat(result.runtimeShim()).as(board)
                .contains("extern \"C\" int32_t juno_thread_start_virtual(int32_t runnable)",
                        "juno_thread_set_daemon(handle, 1);");
    }

    @Test
    @DisplayName("The UNO Q virtual-thread shim uses the Zephyr kernel")
    void shouldUseTheZephyrKernelForVirtualThreadsOnTheUnoQ() throws Exception {
        // When & Then
        assertThat(compile("ArduinoUnoQ", VIRTUAL_THREAD).runtimeShim())
                .contains("#include <zephyr/kernel.h>", "UNO Q port", "k_thread_create");
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("userLevelThreadBodies")
    @DisplayName("User-level threads are rejected at link time")
    void shouldRejectUserLevelThreadsAtLinkTime(String body) throws Exception {
        // Given
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Worker", program("ArduinoUnoR4WiFi", body));

        // When & Then
        assertThatThrownBy(() -> CompilerTestSupport.compileJuno(temporaryDirectory, "demo.Worker"))
                .isInstanceOf(CompileException.class)
                .hasMessageContaining("does not support java.lang.Thread")
                .hasMessageContaining("Thread.startVirtualThread");
    }

    @ParameterizedTest(name = "parallel HTTPS shim compiles as C++ for {0}")
    @ValueSource(strings = {"ArduinoUnoR4WiFi", "ArduinoUnoQ"})
    @DisplayName("Parallel HTTPS shims compile as C++ on ARM boards")
    void shouldCompileParallelHttpsShimsAsCppOnArmBoards(String board) throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        Path shim = temporaryDirectory.resolve(board + ".cpp");
        Files.writeString(shim, parallelHttpsProgramShim(board), StandardCharsets.UTF_8);

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
