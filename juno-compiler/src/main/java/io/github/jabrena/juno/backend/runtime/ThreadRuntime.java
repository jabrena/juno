package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;

/**
 * The C++ source of the {@code java.lang.Thread} runtime in the generated shim: threads, each on a stack of its own,
 * time-sliced by the scheduler ({@link #scheduler}) every {@code JUNO_TIME_SLICE_MILLIS}. Virtual threads and
 * {@code main} are preemptive, as described below. {@code StructuredTaskScope} subtasks are cooperative: the tick never
 * interrupts one, it gives the CPU up only where the program already pauses (a loop backedge once its slice is over,
 * {@code sleep}/{@code yield}/{@code Delay}, {@code join}, a contended monitor), so a statement of a subtask is never
 * interrupted halfway.
 *
 * <p>On the Thumb-2 boards preemption is asynchronous: a periodic tick ({@code SysTick} on the UNO R4, a Zephyr
 * {@code k_timer} on the UNO Q) interrupts whatever runs. When the interrupted instruction belongs to the generated
 * Java code (between {@code juno_java_code_start} and {@code juno_java_code_end}) the tick rewrites the exception
 * frame's return address so that, the moment the interrupt returns, the thread enters
 * {@code juno_preempt_trampoline}: it saves every register the interrupted code may be using (the caller-saved
 * core registers, the flags, {@code s0}-{@code s15} and {@code FPSCR}), switches to the next runnable thread like
 * {@code Thread.yield}, and later restores all of it and resumes exactly where the tick struck. A thread is
 * therefore preempted between any two instructions of generated code, inside a loop or not, unless it is a
 * cooperative subtask.
 *
 * <p>The runtime itself (the arena, the collector, the pending-exception slot, monitors, atomics, the scheduler and
 * every core library the shim calls) is never preempted part-way: a tick that lands outside generated code only
 * marks a switch as due, and the thread takes it at its next loop backedge or when the next tick finds it back in
 * Java code. So none of that state needs locking; the collector scans every live thread's stack, which for a
 * preempted thread includes the registers the trampoline saved, and the pending exception and scoped-value bindings
 * are swapped on each switch.
 *
 * <p>The UNO WiFi Rev2 (AVR) cannot be interrupted at an arbitrary instruction: a reference or {@code int} is
 * loaded and stored a byte at a time, and generated calls pass arguments through one global area. Its threads are
 * preempted at safepoints instead: on every loop backedge and every method entry the running thread hands over the
 * CPU once its time slice has expired, so neither a spinning loop nor a loop-free recursion can starve the others. A
 * cooperative subtask skips the method-entry safepoints.
 *
 * <p>Only how a stack is created, how control moves between two of them and where the tick comes from differ per
 * board; that lives in the {@link CoreRuntime}'s port ({@link #renesasPort}, {@link #zephyrPort},
 * {@link #avrPort}): the UNO R4 has no RTOS, so a few lines of assembly swap stack pointers; the UNO Q already runs
 * on Zephyr, whose own threads carry the stacks while a baton semaphore keeps exactly one of them running Java code.
 */
public final class ThreadRuntime {
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
     * The UNO R4's port: its own stacks and a context switch that saves the AAPCS callee-saved registers
     * (and, with an FPU, {@code s16}-{@code s31}) on the outgoing stack before swapping {@code sp}.
     */
    public String renesasPort(int maxThreads, int stackBytes) {
        return runtimeSource.load("renesas/thread-port.cpp").replace("${JUNO_MAX_THREADS}", Integer.toString(maxThreads))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The UNO WiFi Rev2's port: like the UNO R4's, its own stacks and a context switch, here saving avr-gcc's
     * call-saved registers ({@code r2}-{@code r17} and the frame pointer {@code Y}) before swapping {@code SP}. The
     * return address a fresh stack starts from is two bytes on the ATmega4809 and three on a part with more than
     * 128 KB of flash, which the simulator's ATmega2560 is.
     *
     * <p>A task's stack is small and sits next to the scheduler's own state, so a deep recursion must stop before it
     * writes past the stack: every generated function compares its new SP with {@code juno_stack_limit}, which keeps
     * {@code JUNO_STACK_GUARD_BYTES} free for the C calls of the deepest frame and for reporting the overflow.
     */
    public String avrPort(int maxThreads, int stackBytes) {
        return runtimeSource.load("avr/thread-port.cpp").replace("${JUNO_MAX_THREADS}", Integer.toString(maxThreads))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The UNO Q's port: every Juno thread is a Zephyr thread, but each parks on its own semaphore and only the
     * one holding the baton runs Java code, so the runtime needs no other locking. The preemption tick is a Zephyr
     * {@code k_timer}, whose expiry runs in the system clock's interrupt: Zephyr threads run on the process stack, so
     * {@code psp} holds the interrupted thread's exception frame.
     */
    public String zephyrPort(int maxThreads, int stackBytes) {
        return runtimeSource.load("zephyr/thread-port.cpp").replace("${JUNO_MAX_THREADS}", Integer.toString(maxThreads))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The UNO Q's I/O worker ({@link CoreRuntime#ioWorker()}), emitted after its thread port when the program makes
     * waiting device calls.
     */
    public String zephyrIoWorker() {
        return runtimeSource.load("zephyr/io-worker.cpp");
    }

    /**
     * The preemptive scheduler and the {@code Thread} intrinsics, over a core's port.
     *
     * @param asynchronousPreemption whether the port's tick preempts generated code at any instruction (Thumb-2:
     *     the port defines {@code juno_port_preempt_start} and its tick calls {@code juno_preempt_tick}); otherwise
     *     (AVR) a thread is preempted at the safepoints the backend emits once its time slice has expired
     * @param ioWorker whether the port runs waiting device calls on its I/O worker ({@link CoreRuntime#ioWorker()})
     * @param stackScanFunction the collector's scan of one stack range: word-aligned on Thumb-2, byte by byte on AVR,
     *     whose stack frames are unaligned
     */
    String scheduler(boolean asynchronousPreemption, boolean ioWorker, String delayFunction,
                            String stackScanFunction,
                            boolean exceptions, boolean threadEntry,
                            boolean taskCallableEntry, boolean structuredTasks, boolean scopedValues,
                            int failedExceptionClassId, int illegalStateExceptionClassId) {
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
        String preemption = COOPERATIVE_TASKS
                + (asynchronousPreemption ? asynchronousPreemption() : safepointPreemption());
        String startPreemption = asynchronousPreemption ? """
                  static bool juno_preempt_started = false;
                  if (!juno_preempt_started) {
                    juno_preempt_started = true;
                    juno_port_preempt_start();
                  }""" : "";
        return runtimeSource.load("common/thread-scheduler.cpp").replace("${JUNO_IO_OWNERS}", ioRuntime.owners(ioWorker))
                .replace("${JUNO_WAITING_IO_CASE}", ioWorker
                        ? "case JUNO_SLOT_WAITING_IO:\n                      return !juno_io_waiting(index);" : "")
                .replace("${JUNO_IO_WAITING_DECLARATION}", ioWorker ? "static bool juno_io_waiting(uint32_t slot);" : "")
                .replace("${JUNO_IO_REAP_DECLARATION}", ioWorker
                        ? "static void juno_io_reap();\nstatic bool juno_io_waiting(uint32_t slot);" : "")
                .replace("${JUNO_IO_REAP_CALL}", ioWorker ? "juno_io_reap();" : "")
                .replace("${JUNO_PREEMPTION}", preemption)
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
                .replace("${JUNO_ILLEGAL_STATE_EXCEPTION_CLASS_ID}", Integer.toString(illegalStateExceptionClassId));
    }

    /**
     * Which threads are cooperative: a {@code StructuredTaskScope} subtask gives the CPU up only where the program
     * already pauses (a loop backedge once its time slice is over, {@code sleep}/{@code yield}/{@code Delay},
     * {@code join}, a contended monitor), never part-way through a statement. Virtual threads and {@code main} are
     * preemptive.
     */
    private static final String COOPERATIVE_TASKS = """
            static bool juno_slot_cooperative(uint32_t slot) {
              const JunoThreadObject* thread = juno_slots[slot].thread;
              return thread != nullptr && (thread->flags & JUNO_THREAD_TASK) != 0u;
            }

            """;

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
    private String asynchronousPreemption() {
        return runtimeSource.load("thumb2/preemption.cpp");
    }

    /**
     * AVR's preemption at safepoints: the backend calls {@code juno_thread_backedge} on every loop backedge and
     * {@code juno_thread_safepoint} on every method entry, and either switches once the running thread's time slice
     * is over.
     */
    private String safepointPreemption() {
        return runtimeSource.load("avr/preemption.cpp");
    }

    /** Joiner-based scopes and subtask operations inserted only when the program reaches that API. */
    private String taskRuntime() {
        return runtimeSource.load("common/structured-tasks.cpp");
    }
}
