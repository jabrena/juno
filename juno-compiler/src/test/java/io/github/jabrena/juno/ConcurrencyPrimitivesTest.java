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
import java.util.Optional;
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
                import io.github.jabrena.juno.hardware.Psram;
                import io.github.jabrena.juno.hardware.serial.Serial;
                import java.util.concurrent.CountDownLatch;
                import java.util.concurrent.CyclicBarrier;
                import java.util.concurrent.Phaser;
                import java.util.concurrent.Semaphore;
                import java.util.concurrent.locks.Condition;
                import java.util.concurrent.locks.Lock;
                import java.util.concurrent.locks.ReadWriteLock;
                import java.util.concurrent.locks.ReentrantLock;
                import java.util.concurrent.locks.ReentrantReadWriteLock;
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
            CyclicBarrier barrier = new CyclicBarrier(1);
            barrier.await();
            Serial.println(barrier.getParties());
            ReentrantLock guard = new ReentrantLock();
            Condition ready = guard.newCondition();
            guard.lock();
            try {
                ready.signal();
                ready.signalAll();
                if (barrier.getParties() < 0) {
                    ready.await();
                }
            } finally {
                guard.unlock();
            }
            ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
            ReadWriteLock view = rw;
            view.readLock().lock();
            view.readLock().unlock();
            Lock writer = rw.writeLock();
            writer.lock();
            writer.unlock();
            Serial.println(rw.writeLock().tryLock() ? 1 : 0);
            Phaser phaser = new Phaser(1);
            phaser.register();
            phaser.arrive();
            phaser.arriveAndDeregister();
            phaser.arriveAndAwaitAdvance();
            phaser.awaitAdvance(0);
            phaser.awaitAdvanceInterruptibly(phaser.getPhase());
            Serial.println(phaser.bulkRegister(2) + phaser.getRegisteredParties() + phaser.getUnarrivedParties());
            """;

    static Stream<Arguments> unsupportedCalls() {
        return Stream.of(
                arguments("Semaphore.acquire(int)", "new Semaphore(3).acquire(2);\n"),
                arguments("Phaser timed awaitAdvanceInterruptibly",
                        "new Phaser(1).awaitAdvanceInterruptibly(0, 1, java.util.concurrent.TimeUnit.SECONDS);\n"),
                arguments("Phaser tiered constructor", "new Phaser(new Phaser(1));\n"),
                arguments("ReentrantReadWriteLock fair", "new ReentrantReadWriteLock(true);\n"));
    }

    private CompilationResult compile(String board, String body) throws Exception {
        return compile(board, body, RuntimeConfig.DEFAULT);
    }

    private CompilationResult compile(String board, String body, RuntimeConfig runtimeConfig) throws Exception {
        CompilerTestSupport.compileJavaWithPreview(temporaryDirectory, "demo.Gate", program(board, body));
        return new JunoCompiler().compile(new CompilationRequest(CompilerTestSupport.classpath(temporaryDirectory),
                "demo.Gate", false, Optional.empty(), runtimeConfig));
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
                "bl juno_thread_interrupt", "bl juno_thread_is_interrupted", "bl juno_thread_sleep_interruptible",
                "bl juno_barrier_new", "bl juno_barrier_await", "bl juno_barrier_parties",
                "bl juno_lock_new_condition", "bl juno_condition_signal", "bl juno_condition_signal_all",
                "bl juno_phaser_new", "bl juno_phaser_register", "bl juno_phaser_arrive", "bl juno_phaser_arrive_and_deregister",
                "bl juno_phaser_arrive_and_await", "bl juno_phaser_await_advance", "bl juno_phaser_await_advance_interruptibly",
                "bl juno_phaser_bulk_register", "bl juno_phaser_get_phase",
                "bl juno_rw_lock_new", "bl juno_rw_read_lock", "bl juno_rw_write_lock", "bl juno_lock_lock",
                "bl juno_lock_unlock", "bl juno_lock_try_lock",
                "bl juno_condition_await");
    }

    @Test
    @DisplayName("Dual-carrier ESP32 programs emit carrier-local state and runtime locking")
    void shouldEmitDualCarrierRuntimeState() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();");

        assertThat(result.runtimeShim()).contains("juno_current_slots[2]", "juno_pending_exceptions[2]",
                "juno_runtime_owner", "juno_port_carrier_id");
        assertThat(result.assembly()).contains("call8 juno_java_leave", "call8 juno_java_return");
    }

    @Test
    @DisplayName("Dual-carrier PSRAM calls take and release the shared runtime lock")
    void shouldSerializePsramCallsAcrossEsp32Carriers() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board",
                "Thread.startVirtualThread(() -> Psram.allocate(32)).join();");

        assertThat(result.assembly()).contains("call8 juno_java_leave", "call8 juno_psram_allocate",
                "call8 juno_java_return");
    }

    @Test
    @DisplayName("Dual-carrier ESP32 collections stop the other carrier and allocation takes the runtime lock")
    void shouldStopTheOtherCarrierWhileCollecting() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();");

        assertThat(result.runtimeShim()).contains(
                "static void juno_gc_collect() {\n  juno_gc_stop_world();",
                "juno_gc_sweep();\n  juno_gc_resume_world();",
                "juno_runtime_enter();\n  void* memory = juno_alloc_unlocked(size, alignment);",
                "xthal_window_spill();",
                "juno_gc_lock_wait();",
                "juno_gc_safepoint();",
                "juno_carrier_load(slot);\n  juno_java_resume();",
                "juno_java_pause();\n  juno_runtime_enter();");
    }

    @Test
    @DisplayName("Single-carrier collections keep the original allocator")
    void shouldKeepTheSingleCarrierAllocator() throws Exception {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        CompilationResult result = compile("ArduinoNanoEsp32Board",
                "Thread.startVirtualThread(new Job()).join();", config);

        assertThat(result.runtimeShim()).doesNotContain("juno_gc_stop_world", "juno_alloc_unlocked",
                "juno_java_resume", "juno_carrier_in_java");
    }

    @Test
    @DisplayName("A dual-carrier ESP32 thread gives its core one tick per breathe interval, outside the runtime lock")
    void shouldBreatheOutsideTheRuntimeLockOnTwoCarriers() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();");

        String shim = result.runtimeShim();
        assertThat(shim).contains("static uint32_t juno_breath_starts[2] = {};", "vTaskDelay(1);");
        assertThat(shim).contains("""
                  juno_sched_block();
                  juno_runtime_exit();
                  // Outside the runtime lock, so the other carrier never waits on a breath.
                  juno_port_breathe();
                }""");
    }

    @Test
    @DisplayName("A dual-carrier ESP32 safepoint asks its carrier's slice clock before taking the runtime lock")
    void shouldAskTheSliceClockBeforeTheLockOnTwoCarriers() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();");

        String shim = result.runtimeShim();
        assertThat(shim).contains("static uint32_t juno_slice_marks[2] = {};",
                "if (now - juno_slice_marks[carrier] < juno_slice_length()) return false;", "return xthal_get_ccount();");
        String sliceOver = shim.substring(shim.indexOf("static void juno_thread_slice_over() {"));
        sliceOver = sliceOver.substring(0, sliceOver.indexOf("\n}\n"));
        assertThat(sliceOver.indexOf("if (!juno_port_slice_due()) return;"))
                .isPositive()
                .isLessThan(sliceOver.indexOf("juno_runtime_enter();"));
        assertThat(shim).contains("""
                extern "C" void juno_thread_backedge() {
                  juno_thread_slice_over();
                }""");
    }

    @Test
    @DisplayName("An ESP32 thread program includes xtensa/hal.h exactly once, on one carrier and on two")
    void shouldIncludeTheXtensaHalOnce() throws Exception {
        RuntimeConfig oneCarrier = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        String dual = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();").runtimeShim();
        String single = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();", oneCarrier)
                .runtimeShim();

        assertThat(dual.split("#include <xtensa/hal.h>", -1)).hasSize(2);
        assertThat(single.split("#include <xtensa/hal.h>", -1)).hasSize(2);
    }

    @Test
    @DisplayName("A single-carrier ESP32 safepoint asks the slice clock and never yields on its own")
    void shouldAskTheSliceClockOnOneCarrier() throws Exception {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        CompilationResult result = compile("ArduinoNanoEsp32Board",
                "Thread.startVirtualThread(new Job()).join();", config);

        String shim = result.runtimeShim();
        assertThat(shim).contains("static uint32_t juno_slice_mark = 0u;");
        assertThat(shim).contains("""
                extern "C" void juno_thread_backedge() {
                  juno_thread_slice_over();
                }""");
        assertThat(shim).contains("""
                static void juno_thread_slice_over() {
                  if (!juno_port_slice_due()) return;
                  if (juno_live_threads == 0u) return;""");
        assertThat(shim).doesNotContain("juno_runtime_enter");
    }

    @Test
    @DisplayName("A single-carrier ESP32 thread gives its core one tick per breathe interval")
    void shouldBreatheOnOneCarrier() throws Exception {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        CompilationResult result = compile("ArduinoNanoEsp32Board",
                "Thread.startVirtualThread(new Job()).join();", config);

        String shim = result.runtimeShim();
        assertThat(shim).contains("static uint32_t juno_breath_start = 0u;", "vTaskDelay(1);");
        assertThat(shim).contains("""
                  juno_slice_start = now;
                  juno_sched_block();
                  juno_port_breathe();
                }""");
    }

    @Test
    @DisplayName("A dual-carrier ESP32 thread is placed before its slot is chosen, by the load of runnable threads")
    void shouldPlaceANewThreadBeforeChoosingItsSlot() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();");

        String shim = result.runtimeShim();
        String start = shim.substring(shim.indexOf("extern \"C\" void juno_thread_start(int32_t handle) {"));
        start = start.substring(0, start.indexOf("\n}\n"));
        assertThat(start.indexOf("juno_place_new_thread();")).isPositive()
                .isLessThan(start.indexOf("uint32_t slot = 1u;"));
        assertThat(start).contains("!juno_port_reusable(slot) || !juno_slot_suits(slot)",
                "juno_slots[slot].carrier = juno_new_thread_carrier(slot);");
        // A joining owner leaves its carrier to what it forked; a tie stays on the starter's carrier.
        assertThat(shim).contains("if (juno_slots[index].state != JUNO_SLOT_FREE && !juno_slot_waits(index)) {",
                "uint32_t selected = juno_current_carrier();",
                "if (loads[carrier] < loads[selected]) selected = carrier;")
                .doesNotContain("juno_placement_hint", "juno_choose_carrier");
    }

    @Test
    @DisplayName("A single-carrier ESP32 thread start has no placement step")
    void shouldNotPlaceThreadsOnOneCarrier() throws Exception {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        CompilationResult result = compile("ArduinoNanoEsp32Board",
                "Thread.startVirtualThread(new Job()).join();", config);

        assertThat(result.runtimeShim()).doesNotContain("juno_place_new_thread", "juno_slot_suits");
    }

    @Test
    @DisplayName("Dual-carrier ESP32 programs place main on its real core and start threads on an idle carrier")
    void shouldStartThreadsOnAnIdleCarrier() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", "Thread.startVirtualThread(new Job()).join();");

        assertThat(result.runtimeShim()).contains(
                        "juno_slots[0].carrier = mainCarrier;",
                        "juno_current_slots[carrier] = carrier == mainCarrier ? 0u : JUNO_NO_SLOT;",
                        "if (juno_current_slots[juno_slots[slot].carrier] == JUNO_NO_SLOT) {",
                        "juno_current_slot = JUNO_NO_SLOT;",
                        "static void juno_carrier_vacate(uint32_t slot) {",
                        "static constexpr uint32_t JUNO_SLOT_LIMIT = JUNO_INITIAL_SLOTS;",
                        "static constexpr uint32_t JUNO_INITIAL_SLOTS = 16u;",
                        "xTaskCreatePinnedToCore(juno_esp32_entry, \"juno\", JUNO_THREAD_STACK_BYTES,",
                        "pxTaskGetStackStart(juno_dual_tasks[slot])")
                .doesNotContain("juno_port_finished", "juno_thread_alloc(sizeof(JunoEsp32Task)");
    }

    @Test
    @DisplayName("Single-carrier programs keep the scheduler free of carrier hand-over code")
    void shouldKeepCarrierHandOverOutOfTheSingleCarrierScheduler() throws Exception {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        CompilationResult result = compile("ArduinoNanoEsp32Board",
                "Thread.startVirtualThread(new Job()).join();", config);

        assertThat(result.runtimeShim()).doesNotContain("juno_carriers_init", "juno_carrier_vacate",
                "juno_carrier_load", "juno_port_reusable", "juno_port_check_killed", "${JUNO_");
    }

    @Test
    @DisplayName("Dual-carrier ScopedValue.run gives up the runtime lock while its Java body runs")
    void shouldReleaseTheRuntimeLockAroundJavaCallbacks() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", """
                ScopedValue<String> name = ScopedValue.newInstance();
                ScopedValue.where(name, "juno").run(new Job());
                Thread.startVirtualThread(new Job()).join();
                """);

        assertThat(result.runtimeShim()).contains(
                "uint32_t juno_callback_depth = juno_java_callback_begin();\n  juno_thread_entry(runnable);\n"
                        + "  juno_java_callback_end(juno_callback_depth);",
                "extern \"C\" uint32_t juno_java_callback_begin() {");
    }

    @Test
    @DisplayName("Dual-carrier Pico 2 W programs build on FreeRTOS SMP with main on a task of its own")
    void shouldBuildDualCarrierPicoProgramsOnFreeRtos() throws Exception {
        CompilationResult result = compile("RaspberryPico2WifiBoard", "Thread.startVirtualThread(new Job()).join();");

        assertThat(result.fqbn()).isEqualTo("rp2040:rp2040:rpipico2w:ipbtstack=ipv4btcble,os=freertos");
        assertThat(result.sketchWrapper().source()).contains("juno_launch_main(" + result.entryPointSymbol() + ");");
        assertThat(result.runtimeShim()).contains(
                        "#include <FreeRTOS.h>",
                        "xTaskCreateAffinitySet(juno_rp2350_entry",
                        "hardware_alarm_set_callback(",
                        "__atomic_compare_exchange_n(&juno_runtime_owner",
                        "juno_carrier_vacate(uint32_t slot) {")
                .doesNotContain("SysTick_Handler", "__atomic_exchange_n(&juno_runtime_owner",
                        "extern \"C\" uint32_t get_core_num();");
        assertThat(result.assembly()).contains("bl juno_java_leave", "bl juno_java_return");
    }

    @Test
    @DisplayName("Single-carrier Pico 2 W programs keep the board's FQBN and a direct entry call")
    void shouldKeepSingleCarrierPicoProgramsOffFreeRtos() throws Exception {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        CompilationResult result = compile("RaspberryPico2WifiBoard",
                "Thread.startVirtualThread(new Job()).join();", config);

        assertThat(result.fqbn()).isEqualTo("rp2040:rp2040:rpipico2w:ipbtstack=ipv4btcble");
        assertThat(result.sketchWrapper().source()).contains("  " + result.entryPointSymbol() + "();")
                .doesNotContain("juno_launch_main");
        assertThat(result.runtimeShim()).doesNotContain("FreeRTOS.h");
    }

    @Test
    @DisplayName("The pipeline can force the ESP32 runtime to one carrier")
    void shouldGenerateTheSingleCarrierRuntimeWhenOverridden() throws Exception {
        RuntimeConfig config = RuntimeConfig.of(Optional.empty(), Optional.empty(), Optional.of("1"));

        CompilationResult result = compile("ArduinoNanoEsp32Board",
                "Thread.startVirtualThread(new Job()).join();", config);

        assertThat(result.runtimeShim()).contains("static uint32_t juno_current_slot = 0u;")
                .doesNotContain("juno_current_slots[2]", "juno_runtime_owner", "juno_port_carrier_id");
        assertThat(result.assembly()).doesNotContain("call8 juno_java_leave", "call8 juno_java_return");
        assertThat(result.report().runtimeRisks().carrierCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Dual-carrier Xtensa fields emit a barrier and atomic wide helpers")
    void shouldEmitXtensaMemoryModelInstructions() throws Exception {
        CompilationResult result = compile("ArduinoNanoEsp32Board", """
                class Cell {
                    volatile int flag;
                    volatile long value;
                }
                Cell cell = new Cell();
                cell.value = -1L;
                cell.flag = 1;
                Serial.println(cell.flag);
                Serial.println(cell.value);
                Thread.startVirtualThread(new Job()).join();
                """);

        assertThat(result.assembly()).contains("memw", "call8 juno_wide_store", "call8 juno_wide_load");
        // The long sits after the int: both helpers get the field's own address, not the object's.
        assertThat(result.assembly()).contains("    addi a10, a8, 4\n    call8 juno_wide_store\n",
                "    addi a10, a8, 4\n    call8 juno_wide_load\n");
        assertThat(result.runtimeShim()).contains("juno_wide_load", "juno_wide_store");
    }

    @Test
    @DisplayName("Dual-carrier Thumb-2 wide field stores pass the field's own address")
    void shouldStoreWideFieldsAtTheirOffsetOnTwoThumb2Carriers() throws Exception {
        CompilationResult result = compile("RaspberryPico2WifiBoard", """
                class Cell {
                    volatile int flag;
                    volatile long value;
                }
                Cell cell = new Cell();
                cell.value = -1L;
                cell.flag = 1;
                Serial.println(cell.flag);
                Serial.println(cell.value);
                Thread.startVirtualThread(new Job()).join();
                """);

        assertThat(result.assembly()).contains("    add r0, r0, #4\n    bl juno_wide_store\n",
                "    add r0, r0, #4\n    bl juno_wide_load\n");
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
                "extern \"C\" int64_t juno_latch_get_count(int32_t handle) {",
                "extern \"C\" int32_t juno_barrier_await(int32_t handle) {",
                "extern \"C\" void juno_condition_await(int32_t handle) {");
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
    @ValueSource(strings = {"ArduinoUnoR4WiFiBoard"})
    @DisplayName("Both ports' runtime shims compile as C++")
    void shouldCompileBothPortsRuntimeShimsAsCpp(String board) throws Exception {
        // Given
        String compiler = availableCppCompiler();
        Assumptions.assumeTrue(compiler != null, "No C++ compiler available");
        Path shim = temporaryDirectory.resolve(board + ".cpp");
        Files.writeString(shim, compile(board, ALL_PRIMITIVES).runtimeShim(), StandardCharsets.UTF_8);

        // When
        Process process = new ProcessBuilder(compiler, "-std=c++17", "-fsyntax-only", "-x", "c++",
                "-Isrc/test/resources/include", shim.toString()).redirectErrorStream(true).start();
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
