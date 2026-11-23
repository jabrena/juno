package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;

/**
 * The C++ source of the {@code java.lang.Thread} runtime in the generated shim: threads, each on a stack of its own,
 * time-sliced by the scheduler ({@link #scheduler}) every {@code JUNO_TIME_SLICE_MILLIS}. Virtual threads,
 * {@code StructuredTaskScope} subtasks and {@code main} share that one scheduler and are all preemptive, as described
 * below: a subtask is an ordinary thread with a scope, not a second scheduling policy.
 *
 * <p>On the Thumb-2 boards preemption is asynchronous: a periodic tick ({@code SysTick} on the UNO R4) interrupts
 * whatever runs. When the interrupted instruction belongs to the generated
 * Java code (between {@code juno_java_code_start} and {@code juno_java_code_end}) the tick rewrites the exception
 * frame's return address so that, the moment the interrupt returns, the thread enters
 * {@code juno_preempt_trampoline}: it saves every register the interrupted code may be using (the caller-saved
 * core registers, the flags, {@code s0}-{@code s15} and {@code FPSCR}), switches to the next runnable thread like
 * {@code Thread.yield}, and later restores all of it and resumes exactly where the tick struck. A thread is
 * therefore preempted between any two instructions of generated code, inside a loop or not.
 *
 * <p>The runtime itself (the arena, the collector, the pending-exception slot, monitors, atomics, the scheduler and
 * every core library the shim calls) is never preempted part-way: a tick that lands outside generated code only
 * marks a switch as due, and the thread takes it at its next loop backedge or when the next tick finds it back in
 * Java code. So none of that state needs locking; the collector scans every live thread's stack, which for a
 * preempted thread includes the registers the trampoline saved, and the pending exception and scoped-value bindings
 * are swapped on each switch.
 *
 * <p>An AVR board cannot be interrupted at an arbitrary instruction: a reference or {@code int} is
 * loaded and stored a byte at a time, and generated calls pass arguments through one global area. Its threads are
 * preempted at safepoints instead: on every loop backedge and every method entry the running thread hands over the
 * CPU once its time slice has expired, so neither a spinning loop nor a loop-free recursion can starve the others.
 *
 * <p>Only how a stack is created, how control moves between two of them and where the tick comes from differ per
 * board; that lives in the {@link CoreRuntime}'s port ({@link #renesasPort}, {@link #mbedPort},
 * {@link #esp32Port}, {@link #avrPort}): the UNO R4 has no RTOS, so a few lines of assembly swap stack pointers; the
 * Portenta H7 already runs on Mbed OS and the Nano ESP32 on FreeRTOS, whose own threads carry the stacks while a
 * baton semaphore keeps exactly one of them running Java code. The Nano ESP32 is preempted at safepoints, like AVR.
 */
public final class ThreadRuntime {

    /** How generated code is preempted once its time slice is over. */
    public enum Preemption {
        /** The port's tick preempts at any instruction ({@code juno_preempt_tick}, Thumb-2). */
        TICK,
        /** Safepoints read the clock (AVR, Mbed). */
        SAFEPOINT_CLOCK,
        /** Safepoints ask the port, per carrier and without a lock, whether the slice is over (ESP32). */
        SAFEPOINT_PORT
    }
    private final IoRuntime ioRuntime = new IoRuntime();
    private final RuntimeSource runtimeSource = new RuntimeSource();


    /**
     * {@code Thread.sleep}/{@code Thread.yield} in a program that never creates a thread: plain delay and yield,
     * with none of the scheduler.
     */
    String basics(String delayFunction) {
        return runtimeSource.load("common/thread-basics.cpp").replace("${JUNO_CORE_DELAY}", delayFunction);
    }

    /**
     * The UNO R4's (and Pico 2 W's) port: main keeps its own stack and every other thread is a continuation on one
     * carrier stack of {@code stackBytes}. A context switch saves the AAPCS callee-saved registers (and, with an FPU,
     * {@code s16}-{@code s31}) on the outgoing stack before swapping {@code sp}; a thread that loses the carrier to
     * another one is frozen (its frames copied into an arena chunk) and thawed back to the same addresses later, by a
     * switcher running on a small stack of its own. The SysTick reload comes from the Renesas core's
     * {@code SystemCoreClock}.
     */
    public String renesasPort(int stackBytes) {
        return continuationPort(stackBytes, "extern \"C\" uint32_t SystemCoreClock;\n", "SystemCoreClock");
    }

    /**
     * The same continuation port on one carrier with the core clock taken from {@code clockHz}: arduino-pico has no
     * {@code SystemCoreClock}, so the RP2350 asks the SDK for {@code clk_sys}, which also follows an overclock.
     */
    private String continuationPort(int stackBytes, String clockDeclaration, String clockHz) {
        return runtimeSource.load("renesas/thread-port.cpp")
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes))
                .replace("${JUNO_CORE_CLOCK_DECLARATION}", clockDeclaration)
                .replace("${JUNO_CORE_CLOCK_HZ}", clockHz);
    }

    /**
     * The AVR port: like the UNO R4's, its own stacks and a context switch, here saving avr-gcc's
     * call-saved registers ({@code r2}-{@code r17} and the frame pointer {@code Y}) before swapping {@code SP}. The
     * return address a fresh stack starts from is two bytes on a part with up to 128 KB of flash, such as the ATmega328P, and
     * three on a larger one.
     *
     * <p>A task's stack is small and sits next to the scheduler's own state, so a deep recursion must stop before it
     * writes past the stack: every generated function compares its new SP with {@code juno_stack_limit}, which keeps
     * {@code JUNO_STACK_GUARD_BYTES} free for the C calls of the deepest frame and for reporting the overflow.
     */
    public String avrPort(int stackBytes) {
        return runtimeSource.load("avr/thread-heap.cpp")
                + runtimeSource.load("avr/thread-port.cpp")
                .replace("${JUNO_MAX_THREADS}", Integer.toString(RuntimeLimits.MAX_THREADS))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The Portenta H7's Mbed OS port: every Juno thread is an Mbed RTOS thread with a caller-owned stack from the
     * Juno arena. A semaphore baton keeps exactly one Mbed thread inside the Juno scheduler at a time, while the
     * stack ranges remain available to the collector even when a thread is parked by the RTOS.
     */
    public String mbedPort(int stackBytes) {
        return runtimeSource.load("mbed/thread-port.cpp")
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The Portenta H7 port selected for a single or dual carrier runtime. With two carriers the Cortex-M4 runs Juno's
     * carrier image ({@link #mbedM4Carrier}) and executes this image's code for the threads placed on it; the
     * {@link #mbedLink link} both images share comes with the runtime lock, which is emitted before the port.
     */
    public String mbedPort(int stackBytes, int carrierCount) {
        if (carrierCount == 1) return mbedPort(stackBytes);
        return runtimeSource.load("mbed/thread-port-dual.cpp")
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /** The block, hardware semaphores and request rings the Portenta H7's two carrier images share. */
    public String mbedLink() {
        return runtimeSource.load("mbed/m4-link.h")
                .replace("${JUNO_DUAL_CARRIER_SLOTS}", Integer.toString(RuntimeLimits.MAX_DUAL_CARRIER_THREADS));
    }

    /** The Portenta H7's Cortex-M4 carrier image: the M4 kernel's services for the program image, and no Java. */
    public String mbedM4Carrier() {
        return runtimeSource.load("mbed/m4-carrier.ino").replace("${JUNO_M4_LINK}", mbedLink());
    }

    /**
     * The Nano ESP32's port: like the Portenta H7's, every Juno thread is an RTOS task (here FreeRTOS, which the
     * {@code arduino:esp32} core already runs) parked on its own baton semaphore, so only the baton holder runs Java
     * code. There is no asynchronous tick: the backend emits safepoints, as on AVR.
     */
    public String esp32Port(int stackBytes) {
        return runtimeSource.load("esp32/thread-port.cpp").replace("${JUNO_MAX_THREADS}",
                        Integer.toString(RuntimeLimits.MAX_THREADS))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /** The ESP32 port selected for a single or dual carrier runtime. */
    public String esp32Port(int stackBytes, int carrierCount) {
        return carrierCount == 1 ? esp32Port(stackBytes) : runtimeSource.load("esp32/thread-port-dual.cpp")
                .replace("${JUNO_DUAL_CARRIER_SLOTS}", Integer.toString(RuntimeLimits.MAX_DUAL_CARRIER_THREADS))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The RP2350 port selected for a single or dual carrier runtime. With two carriers the sketch is built with
     * arduino-pico's FreeRTOS SMP, and every Juno thread is a FreeRTOS task pinned to one core, as on the ESP32.
     */
    public String rp2350Port(int stackBytes, int carrierCount) {
        if (carrierCount == 1) return continuationPort(stackBytes, "", "clock_get_hz(clk_sys)");
        return runtimeSource.load("rp2350/thread-port-dual.cpp")
                .replace("${JUNO_DUAL_CARRIER_SLOTS}", Integer.toString(RuntimeLimits.MAX_DUAL_CARRIER_THREADS))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The preemptive scheduler and the {@code Thread} intrinsics, over a core's port.
     *
     * @param preemption how generated code is preempted: from the port's tick at any instruction (Thumb-2), or at the
     *     safepoints the backend emits once the slice is over, read from the clock (AVR, Mbed) or asked of the port
     *     without a lock (ESP32)
     * @param maxHttpRequests the core's safe number of simultaneous HTTP transport leases
     * @param stackScanFunction the collector's scan of one stack range: word-aligned on Thumb-2, byte by byte on AVR,
     *     whose stack frames are unaligned
     */
    String scheduler(Preemption preemption, int maxHttpRequests, String delayFunction,
                            String stackScanFunction,
                            boolean exceptions, boolean threadEntry,
                            boolean taskCallableEntry, boolean structuredTasks, boolean scopedValues,
                            int failedExceptionClassId, int illegalStateExceptionClassId,
                            int interruptedExceptionClassId, int brokenBarrierExceptionClassId) {
        return scheduler(preemption, maxHttpRequests, delayFunction, stackScanFunction,
                exceptions, threadEntry, taskCallableEntry, structuredTasks, scopedValues,
                failedExceptionClassId, illegalStateExceptionClassId, interruptedExceptionClassId,
                brokenBarrierExceptionClassId, 1, false);
    }

    /**
     * Generates the common scheduler with the number of physical carriers supplied by the core. Keeping the old
     * overload above is deliberate: all single-carrier generated text follows the original template exactly.
     *
     * @param nativeCalls whether a thread may leave the runtime for a blocking library call ({@code
     *     common/native-calls.cpp}): on one carrier, on a port whose threads each have a native stack
     */
    String scheduler(Preemption preemption, int maxHttpRequests, String delayFunction,
                            String stackScanFunction,
                            boolean exceptions, boolean threadEntry,
                            boolean taskCallableEntry, boolean structuredTasks, boolean scopedValues,
                            int failedExceptionClassId, int illegalStateExceptionClassId,
                            int interruptedExceptionClassId, int brokenBarrierExceptionClassId, int carrierCount,
                            boolean nativeCalls) {
        String pendingSave = exceptions ? """
                juno_slots[from].pending = juno_pending_exception;
                  juno_pending_exception = juno_slots[to].pending;""" : "";
        // Each thread runs under its own scoped-value bindings, so the switch swaps the binding stack too.
        String bindingsSwap = scopedValues ? """
                juno_slots[from].bindings = juno_scoped_top;
                  juno_scoped_top = juno_slots[to].bindings;""" : "";
        String report = exceptions ? """
                  if (juno_pending_exception != 0) {
                    juno_throw_report(juno_pending_exception, static_cast<int32_t>(thread->id));
                    juno_pending_exception = 0;
                  }""" : "";
        String taskEntry = structuredTasks
                ? (taskCallableEntry ? "extern \"C\" int32_t juno_task_entry(int32_t callable);\n" : "")
                        + "extern \"C\" int32_t juno_task_configuration_entry(int32_t operatorHandle, "
                        + "int32_t configuration);\n"
                        + "static void juno_task_complete(JunoThreadObject* task, int32_t result);"
                : "";
        String ordinaryEntry = threadEntry ? "extern \"C\" void juno_thread_entry(int32_t runnable);" : "";
        String ordinaryBody = threadEntry
                ? "juno_thread_entry(static_cast<int32_t>(thread->runnable));\n${JUNO_THREAD_REPORT}"
                : "juno_panic();";
        String structuredBody = taskCallableEntry && threadEntry ? """
                    if ((thread->flags & JUNO_TASK_RUNNABLE) != 0u) {
                      juno_thread_entry(static_cast<int32_t>(thread->runnable));
                      juno_task_complete(thread, 0);
                    } else {
                      int32_t result = juno_task_entry(static_cast<int32_t>(thread->runnable));
                      juno_task_complete(thread, result);
                    }""" : taskCallableEntry ? """
                    int32_t result = juno_task_entry(static_cast<int32_t>(thread->runnable));
                    juno_task_complete(thread, result);""" : threadEntry ? """
                    juno_thread_entry(static_cast<int32_t>(thread->runnable));
                    juno_task_complete(thread, 0);""" : "juno_panic();";
        String taskBody = structuredTasks ? """
                  if ((thread->flags & JUNO_THREAD_TASK) != 0u) {
                ${JUNO_STRUCTURED_BODY}
                  } else {
                ${JUNO_ORDINARY_BODY}
                  }""".replace("${JUNO_STRUCTURED_BODY}", structuredBody) : """
                ${JUNO_ORDINARY_BODY}""";
        String taskRuntime = structuredTasks ? taskRuntime() : "";
        String raiseInterrupted = raiseException(exceptions, interruptedExceptionClassId);
        String raiseBrokenBarrier = raiseException(exceptions, brokenBarrierExceptionClassId);
        String preemptionSource = switch (preemption) {
            case TICK -> asynchronousPreemption(carrierCount);
            case SAFEPOINT_CLOCK -> safepointPreemption(carrierCount);
            case SAFEPOINT_PORT -> portSlicedSafepoints(carrierCount);
        };
        SchedulerCarriers carriers = SchedulerCarriers.of(carrierCount);
        String startPreemption = preemption == Preemption.TICK ? carriers.startPreemption() : "";
        String source = runtimeSource.load("common/thread-scheduler.cpp").replace("${JUNO_IO_OWNERS}", ioRuntime.owners(maxHttpRequests))
                .replace("${JUNO_PREEMPTION}", preemptionSource)
                .replace("${JUNO_START_PREEMPTION}", startPreemption)
                .replace("${JUNO_PENDING_SWAP}", pendingSave)
                .replace("${JUNO_BINDINGS_SWAP}", bindingsSwap)
                .replace("${JUNO_THREAD_BINDINGS}", scopedValues ? "JunoBindingFrame* bindings;" : "")
                .replace("${JUNO_SLOT_BINDINGS}", scopedValues ? "JunoBindingFrame* bindings;" : "")
                .replace("${JUNO_START_BINDINGS}",
                        scopedValues ? "juno_slots[slot].bindings = thread->bindings;" : "")
                .replace("${JUNO_THREAD_REPORT}", report)
                .replace("${JUNO_ORDINARY_ENTRY}", ordinaryEntry)
                .replace("${JUNO_TASK_ENTRY}", taskEntry)
                .replace("${JUNO_TASK_BODY}", taskBody.replace("${JUNO_ORDINARY_BODY}", ordinaryBody)
                        .replace("${JUNO_THREAD_REPORT}", report))
                .replace("${JUNO_TASK_RUNTIME}", taskRuntime)
                .replace("${JUNO_FORK_BINDINGS}", scopedValues ? "task->bindings = juno_scoped_top;" : "")
                .replace("${JUNO_CORE_DELAY}", delayFunction)
                .replace("${JUNO_STACK_SCAN}", stackScanFunction)
                .replace("${JUNO_MAX_MONITORS}", Integer.toString(RuntimeLimits.MAX_MONITORS))
                .replace("${JUNO_FAILED_EXCEPTION_CLASS_ID}", Integer.toString(failedExceptionClassId))
                .replace("${JUNO_ILLEGAL_STATE_EXCEPTION_CLASS_ID}", Integer.toString(illegalStateExceptionClassId))
                .replace("${JUNO_RAISE_INTERRUPTED}", raiseInterrupted)
                .replace("${JUNO_RAISE_BROKEN_BARRIER}", raiseBrokenBarrier)
                .replace("${JUNO_RUN_BARRIER_ACTION}", barrierAction(threadEntry))
                .replace("${JUNO_EXCEPTION_PENDING}", pendingException(exceptions))
                .replace("${JUNO_NATIVE_RUNNABLE}", nativeCalls ? """
                            case JUNO_SLOT_NATIVE:
                            case JUNO_SLOT_NATIVE_CANCELLED:
                              return juno_port_native_returned(index);
                        """ : "");
        String scheduler = carriers.apply(source, carrierLoad(exceptions, scopedValues), delayFunction);
        return nativeCalls ? scheduler + nativeCalls(pendingSave, bindingsSwap, structuredTasks, delayFunction)
                : scheduler;

    }

    /**
     * {@code juno_native_leave}/{@code juno_native_return}. With structured tasks a cancel can reach a thread inside its
     * native call, where it cannot be stopped: the cancel only marks it, and the thread stops itself once the call is
     * over, releasing its child tasks, monitors and devices (whose abort hooks close a half-used client) then.
     */
    private String nativeCalls(String pendingSave, String bindingsSwap, boolean structuredTasks, String delayFunction) {
        String stop = structuredTasks ? """

                static void juno_native_stop(uint32_t slot) {
                  juno_task_cancel_owned(slot + 1u);
                  juno_task_release_monitors(slot + 1u);
                  juno_io_release_owned(slot + 1u);
                  juno_slots[slot].state = JUNO_SLOT_FREE;
                  juno_slots[slot].thread = nullptr;
                  juno_slots[slot].joining = nullptr;
                  juno_slots[slot].scope = nullptr;
                  juno_live_threads--;
                  for (;;) {
                    uint32_t next = juno_sched_pick(false);
                    if (next != JUNO_NO_SLOT) {
                      juno_sched_handoff(next, true);
                      return;
                    }
                    ${JUNO_CORE_DELAY}(1u);
                  }
                }
                """.replace("${JUNO_CORE_DELAY}", delayFunction) : "";
        String cancelCheck = structuredTasks
                ? "if (juno_slots[juno_current_slot].state == JUNO_SLOT_NATIVE_CANCELLED) "
                        + "juno_native_stop(juno_current_slot);\n  "
                : "";
        return runtimeSource.load("common/native-calls.cpp")
                .replace("${JUNO_PENDING_SWAP}", pendingSave)
                .replace("${JUNO_BINDINGS_SWAP}", bindingsSwap)
                .replace("${JUNO_NATIVE_STOP}", stop)
                .replace("${JUNO_NATIVE_CANCEL_CHECK}", cancelCheck);
    }

    /**
     * What a thread loads into its carrier's per-carrier state when it takes the carrier without a handoff: a new
     * thread starting on an idle carrier, or the thread that inherits a carrier from a cancelled one. After a handoff
     * the values are the ones the handoff already stored.
     */
    private static String carrierLoad(boolean exceptions, boolean scopedValues) {
        return (exceptions ? "\n  juno_pending_exception = juno_slots[slot].pending;" : "")
                + (scopedValues ? "\n  juno_scoped_top = juno_slots[slot].bindings;" : "")
                + "\n  juno_slice_start = static_cast<uint32_t>(millis());";
    }

    /**
     * Thumb-2's asynchronous preemption. The tick runs in handler mode with the interrupted thread's exception frame
     * ({@code r0}-{@code r3}, {@code r12}, {@code lr}, {@code pc}, {@code xPSR}, then any FPU words). It only
     * redirects a thread caught in generated code, and never one inside an IT block or a continuable
     * {@code LDM}/{@code STM} (non-zero IT/ICI bits), which must resume at the very instruction it left; anything else
     * just marks a switch as due for the next backedge.
     *
     * <p>The trampoline then runs in thread mode, on the thread's own stack, with every register exactly as the
     * interrupted code left it but {@code pc}. It reserves a word for the resume address, saves the caller-saved
     * registers, the flags and the caller-saved FPU state (the callee-saved ones survive the C call and the context
     * switch on their own), aligns the stack for the call, and ends with a {@code pop {pc}} that leaves {@code sp}
     * exactly where the interrupted code had it.
     */
    private String asynchronousPreemption(int carrierCount) {
        String source = runtimeSource.load("thumb2/preemption.cpp");
        if (carrierCount > 1) {
            source = source.replace("static volatile uint32_t juno_preempt_due = 0u;", """
                    extern "C" uint32_t juno_port_carrier_id();
                    static volatile uint32_t juno_preempt_dues[${JUNO_CARRIER_COUNT}] = {};
                    #define juno_preempt_due juno_preempt_dues[juno_port_carrier_id()]"""
                    .replace("${JUNO_CARRIER_COUNT}", Integer.toString(carrierCount)));
            source = source.replace("static volatile uint32_t juno_preemptions = 0u;", """
                    static volatile uint32_t juno_preemptions_by_carrier[${JUNO_CARRIER_COUNT}] = {};
                    #define juno_preemptions juno_preemptions_by_carrier[juno_port_carrier_id()]"""
                    .replace("${JUNO_CARRIER_COUNT}", Integer.toString(carrierCount)));
            source = source.replace("""
                    extern "C" void juno_thread_backedge() {
                      yield();
                      if (juno_preempt_due == 0u || juno_live_threads == 0u) return;
                      juno_preempt_due = 0u;
                      juno_sched_block();
                    }
                    """, """
                    extern "C" void juno_thread_backedge() {
                      juno_gc_safepoint();
                      juno_port_check_killed();
                      juno_runtime_enter();
                      yield();
                      if (juno_preempt_due == 0u || juno_live_threads == 0u) {
                        juno_runtime_exit();
                        return;
                      }
                      juno_preempt_due = 0u;
                      juno_sched_block();
                      juno_runtime_exit();
                    }
                    """);
            // A carrier preempted in Java parks there while the other carrier collects, and a cancelled thread stops.
            source = source.replace("""
                    extern "C" uint32_t juno_thread_preempted() {
                    """, """
                    extern "C" uint32_t juno_thread_preempted() {
                      juno_gc_safepoint();
                      juno_port_check_killed();
                    """);
        }
        return source;
    }

    /**
     * The ESP32's preemption at safepoints: a safepoint asks the port whether its carrier's slice is over, without a
     * lock, and only a due slice enters the scheduler and, on two carriers, takes the shared runtime lock.
     */
    private String portSlicedSafepoints(int carrierCount) {
        return runtimeSource.load(carrierCount > 1 ? "esp32/preemption-dual.cpp" : "esp32/preemption.cpp");
    }

    /**
     * AVR's preemption at safepoints: the backend calls {@code juno_thread_backedge} on every loop backedge and
     * {@code juno_thread_safepoint} on every method entry, and either switches once the running thread's time slice
     * is over.
     */
    private String safepointPreemption(int carrierCount) {
        if (carrierCount > 1) {
            // The only dual-carrier core preempted at safepoints, the ESP32, reads a tick flag instead.
            throw new IllegalStateException("Clock-read safepoints run on one carrier, not " + carrierCount);
        }
        return runtimeSource.load("avr/preemption.cpp").replace("${JUNO_CARRIER_COUNT}", "1");
    }

    /**
     * The body of a scheduler function that raises the exception of {@code classId}. Without the exception runtime no
     * thread can be interrupted, so the raise is never reached.
     */
    private static String raiseException(boolean exceptions, int classId) {
        return exceptions ? """
                  auto* exception = static_cast<int32_t*>(juno_alloc(8u, 4u));
                  exception[0] = ${JUNO_EXCEPTION_CLASS_ID};
                  exception[1] = 0;
                  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));"""
                .replace("${JUNO_EXCEPTION_CLASS_ID}", Integer.toString(classId))
                : "  juno_panic();";
    }

    /** The call that runs a CyclicBarrier's trip action; without the thread entry no barrier has one. */
    private static String barrierAction(boolean threadEntry) {
        return threadEntry ? "juno_thread_entry(barrier[4]);" : "";
    }

    /** Whether a call left an exception pending; without the exception runtime nothing can. */
    private static String pendingException(boolean exceptions) {
        return exceptions ? "juno_throw_pending() != 0" : "false";
    }

    /** Joiner-based scopes and subtask operations inserted only when the program reaches that API. */
    private String taskRuntime() {
        return runtimeSource.load("common/structured-tasks.cpp");
    }
}
