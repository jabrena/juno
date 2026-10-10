package io.github.jabrena.juno.backend;

import io.github.jabrena.juno.RuntimeLimits;

/**
 * The C++ source of the {@code java.lang.Thread} runtime in the generated shim: cooperative threads, each on a
 * stack of its own, switched only where the program already yields — loop backedges, {@code Delay}/
 * {@code Thread.sleep}, {@code Thread.yield} and {@code Thread.join}. Nothing is ever preempted in the middle of
 * an allocation, a collection or a throw, so the arena, the collector and the pending-exception slot need no
 * locking; the collector just scans every live thread's stack, and the pending exception is saved and restored
 * around each switch.
 *
 * <p>The scheduler ({@link #scheduler}) is identical for every board. Only how a stack is created and how control
 * moves between two of them differs, and that lives in the {@link CoreRuntime}'s port ({@link #renesasPort},
 * {@link #zephyrPort}): the UNO R4 has no RTOS, so a few lines of assembly swap stack pointers; the UNO Q already
 * runs on Zephyr, whose own threads carry the stacks while a baton semaphore keeps exactly one of them running.
 */
final class ThreadRuntime {
    private ThreadRuntime() {
    }

    /**
     * {@code Thread.sleep}/{@code Thread.yield} in a program that never creates a thread: plain delay and yield,
     * with none of the scheduler.
     */
    static String basics(String delayFunction) {
        return """

                extern "C" void yield(void);

                extern "C" void juno_thread_sleep(int64_t millis) {
                  while (millis > 0) {
                    int64_t chunk = millis > 0x3FFFFFFF ? 0x3FFFFFFF : millis;
                    ${JUNO_CORE_DELAY}(static_cast<uint32_t>(chunk));
                    millis -= chunk;
                  }
                }

                extern "C" void juno_thread_yield() {
                  yield();
                }
                """.replace("${JUNO_CORE_DELAY}", delayFunction);
    }

    /**
     * The UNO R4's port: its own stacks and a context switch that saves the AAPCS callee-saved registers
     * (and, with an FPU, {@code s16}-{@code s31}) on the outgoing stack before swapping {@code sp}.
     */
    static String renesasPort(int maxThreads, int stackBytes) {
        return """

                // ---- Juno threads: UNO R4 port (no RTOS: a stack per thread, switched by swapping sp) ----
                static constexpr uint32_t JUNO_MAX_THREADS = ${JUNO_MAX_THREADS}u;
                static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
                static constexpr uint32_t JUNO_STACK_CANARY = 0x4A554E4Fu;
                #if defined(__ARM_FP)
                static constexpr uint32_t JUNO_SWITCH_FRAME_WORDS = 16u + 9u;
                #else
                static constexpr uint32_t JUNO_SWITCH_FRAME_WORDS = 9u;
                #endif

                alignas(8) static uint8_t juno_stacks[JUNO_MAX_THREADS - 1u][JUNO_THREAD_STACK_BYTES];
                static uint32_t juno_saved_sp[JUNO_MAX_THREADS];

                // Saves r4-r11, lr (and s16-s31) on the current stack, stores sp through r0, then runs on the
                // stack r1 holds: a thread's first switch finds a frame of zeros whose "return address" is its
                // trampoline.
                extern "C" void juno_context_switch(uint32_t* saveSp, uint32_t newSp);
                __asm__(
                    ".syntax unified\\n"
                    ".thumb\\n"
                    ".section .text.juno_context_switch,\\"ax\\",%progbits\\n"
                    ".global juno_context_switch\\n"
                    ".thumb_func\\n"
                    ".type juno_context_switch, %function\\n"
                    "juno_context_switch:\\n"
                    "  push {r4-r11, lr}\\n"
                #if defined(__ARM_FP)
                    "  vpush {s16-s31}\\n"
                #endif
                    "  str sp, [r0]\\n"
                    "  mov sp, r1\\n"
                #if defined(__ARM_FP)
                    "  vpop {s16-s31}\\n"
                #endif
                    "  pop {r4-r11, pc}\\n"
                    ".text\\n");

                static void juno_thread_body();

                extern "C" void juno_thread_trampoline() {
                  juno_thread_body();
                  for (;;) {}
                }

                static void juno_port_prepare(uint32_t slot) {
                  uint32_t* canary = reinterpret_cast<uint32_t*>(&juno_stacks[slot - 1u][0]);
                  canary[0] = JUNO_STACK_CANARY;
                  canary[1] = JUNO_STACK_CANARY;
                  uint32_t* top = reinterpret_cast<uint32_t*>(&juno_stacks[slot - 1u][JUNO_THREAD_STACK_BYTES]);
                  uint32_t* frame = top - 2u - JUNO_SWITCH_FRAME_WORDS;
                  for (uint32_t index = 0; index < JUNO_SWITCH_FRAME_WORDS; index++) frame[index] = 0u;
                  frame[JUNO_SWITCH_FRAME_WORDS - 1u] = static_cast<uint32_t>(
                      reinterpret_cast<uintptr_t>(&juno_thread_trampoline));
                  juno_saved_sp[slot] = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(frame));
                }

                // A cancelled cooperative task will never be scheduled again; its private stack can be reused.
                static void juno_port_cancel(uint32_t slot) {
                  static_cast<void>(slot);
                }

                static void juno_port_check_stack(uint32_t slot) {
                  if (slot == 0u) return;
                  const uint32_t* canary = reinterpret_cast<const uint32_t*>(&juno_stacks[slot - 1u][0]);
                  if (canary[0] != JUNO_STACK_CANARY || canary[1] != JUNO_STACK_CANARY) {
                    Serial.println("[juno-thread] stack overflow");
                    juno_panic();
                  }
                }

                static void juno_port_switch(uint32_t from, uint32_t to) {
                  juno_port_check_stack(from);
                  juno_context_switch(&juno_saved_sp[from], juno_saved_sp[to]);
                }

                static void juno_port_exit(uint32_t from, uint32_t to) {
                  juno_context_switch(&juno_saved_sp[from], juno_saved_sp[to]);
                }

                static uintptr_t juno_port_stack_top(uint32_t slot) {
                  return reinterpret_cast<uintptr_t>(&juno_stacks[slot - 1u][JUNO_THREAD_STACK_BYTES]);
                }

                static uintptr_t juno_port_saved_sp(uint32_t slot) {
                  return juno_saved_sp[slot];
                }
                """.replace("${JUNO_MAX_THREADS}", Integer.toString(maxThreads))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /**
     * The UNO Q's port: every Juno thread is a Zephyr thread, but each parks on its own semaphore and only the
     * one holding the baton runs, so scheduling stays cooperative and the runtime needs no other locking.
     */
    static String zephyrPort(int maxThreads, int stackBytes) {
        return """

                // ---- Juno threads: UNO Q port (Zephyr threads handing a baton, one runs at a time) ----
                static constexpr uint32_t JUNO_MAX_THREADS = ${JUNO_MAX_THREADS}u;
                static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;

                extern "C" void yield(void);

                K_THREAD_STACK_ARRAY_DEFINE(juno_zephyr_stacks, JUNO_MAX_THREADS - 1u, JUNO_THREAD_STACK_BYTES);
                static struct k_thread juno_zephyr_threads[JUNO_MAX_THREADS - 1u];
                static struct k_sem juno_zephyr_batons[JUNO_MAX_THREADS];
                static bool juno_zephyr_created[JUNO_MAX_THREADS];
                static bool juno_zephyr_ready = false;
                static uintptr_t juno_zephyr_sp[JUNO_MAX_THREADS];

                static void juno_thread_body();

                static void juno_zephyr_entry(void* slot, void*, void*) {
                  k_sem_take(&juno_zephyr_batons[reinterpret_cast<uintptr_t>(slot)], K_FOREVER);
                  juno_thread_body();
                }

                static uintptr_t juno_port_stack_top(uint32_t slot) {
                  return reinterpret_cast<uintptr_t>(K_THREAD_STACK_BUFFER(juno_zephyr_stacks[slot - 1u]))
                      + JUNO_THREAD_STACK_BYTES;
                }

                static void juno_port_prepare(uint32_t slot) {
                  if (!juno_zephyr_ready) {
                    for (uint32_t index = 0; index < JUNO_MAX_THREADS; index++) {
                      k_sem_init(&juno_zephyr_batons[index], 0, 1);
                    }
                    juno_zephyr_ready = true;
                  }
                  if (juno_zephyr_created[slot]) {
                    // The previous thread of this slot has handed over the baton and is returning; let it end.
                    k_thread_join(&juno_zephyr_threads[slot - 1u], K_FOREVER);
                  }
                  juno_zephyr_sp[slot] = juno_port_stack_top(slot);
                  k_thread_create(&juno_zephyr_threads[slot - 1u], juno_zephyr_stacks[slot - 1u],
                      K_THREAD_STACK_SIZEOF(juno_zephyr_stacks[slot - 1u]), juno_zephyr_entry,
                      reinterpret_cast<void*>(static_cast<uintptr_t>(slot)), nullptr, nullptr,
                      K_PRIO_PREEMPT(5), 0, K_NO_WAIT);
                  juno_zephyr_created[slot] = true;
                }

                static void juno_port_cancel(uint32_t slot) {
                  if (juno_zephyr_created[slot]) {
                    k_thread_abort(&juno_zephyr_threads[slot - 1u]);
                    juno_zephyr_created[slot] = false;
                  }
                }

                // __builtin_unwind_init() spills every callee-saved register into this frame, so the conservative
                // stack scan from the recorded sp sees whatever references they held.
                __attribute__((noinline)) static void juno_port_switch(uint32_t from, uint32_t to) {
                  __builtin_unwind_init();
                  uint8_t marker;
                  juno_zephyr_sp[from] = reinterpret_cast<uintptr_t>(&marker);
                  k_sem_give(&juno_zephyr_batons[to]);
                  k_sem_take(&juno_zephyr_batons[from], K_FOREVER);
                }

                // The finished thread's last act: pass the baton and return from its Zephyr entry function.
                static void juno_port_exit(uint32_t from, uint32_t to) {
                  static_cast<void>(from);
                  k_sem_give(&juno_zephyr_batons[to]);
                }

                static uintptr_t juno_port_saved_sp(uint32_t slot) {
                  return juno_zephyr_sp[slot];
                }
                """.replace("${JUNO_MAX_THREADS}", Integer.toString(maxThreads))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(stackBytes));
    }

    /** The cooperative scheduler and the {@code Thread} intrinsics, over a core's port. */
    static String scheduler(String delayFunction, boolean exceptions, boolean threadEntry, boolean taskCallableEntry,
                            boolean structuredTasks, boolean scopedValues, int failedExceptionClassId,
                            int illegalStateExceptionClassId) {
        String pendingSave = exceptions ? "juno_slots[from].pending = juno_pending_exception;\n"
                + "  juno_pending_exception = juno_slots[to].pending;" : "";
        // Each thread runs under its own scoped-value bindings, so the switch swaps the binding stack too.
        String bindingsSwap = scopedValues ? "juno_slots[from].bindings = juno_scoped_top;\n"
                + "  juno_scoped_top = juno_slots[to].bindings;" : "";
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
        return """

                // ---- Juno threads: cooperative scheduler (same for every board) ----
                // Slot 0 is the main thread. A Thread handle is a small arena object, so the collector keeps it
                // (and the Runnable it points at) alive for as long as something references it.
                struct JunoTaskScope;
                struct JunoThreadObject {
                  uint32_t runnable;
                  uint32_t flags;
                  uint32_t id;
                  uint32_t reserved;
                  JunoTaskScope* scope;
                  int32_t result;
                  int32_t failure;
                  ${JUNO_THREAD_BINDINGS}
                };
                static constexpr uint32_t JUNO_THREAD_STARTED = 1u;
                static constexpr uint32_t JUNO_THREAD_FINISHED = 2u;
                static constexpr uint32_t JUNO_THREAD_DAEMON = 4u;
                static constexpr uint32_t JUNO_THREAD_TASK = 8u;
                static constexpr uint32_t JUNO_TASK_SUCCESS = 16u;
                static constexpr uint32_t JUNO_TASK_FAILED = 32u;
                static constexpr uint32_t JUNO_TASK_CANCELLED = 64u;
                static constexpr uint32_t JUNO_TASK_RUNNABLE = 128u;

                struct JunoTaskScope {
                  uint32_t policy;
                  uint32_t flags;
                  uint32_t active;
                  uint32_t owner;
                  int32_t firstFailure;
                  int32_t result;
                  JunoThreadObject* tasks[JUNO_MAX_THREADS - 1u];
                };

                enum : uint32_t {
                  JUNO_SLOT_FREE,
                  JUNO_SLOT_READY,
                  JUNO_SLOT_SLEEPING,
                  JUNO_SLOT_JOINING,
                  JUNO_SLOT_JOINING_ALL,
                  JUNO_SLOT_JOINING_SCOPE
                };

                struct JunoSlot {
                  uint32_t state;
                  uint32_t wake;
                  JunoThreadObject* thread;
                  JunoThreadObject* joining;
                  JunoTaskScope* scope;
                  int32_t pending;
                  ${JUNO_SLOT_BINDINGS}
                };

                static JunoSlot juno_slots[JUNO_MAX_THREADS] = {
                    {JUNO_SLOT_READY, 0u, nullptr, nullptr, nullptr, 0}};
                static uint32_t juno_current_slot = 0u;
                static uint32_t juno_live_threads = 0u;
                static uint32_t juno_threads_created = 0u;
                static uint32_t juno_slice_start = 0u;

                struct JunoMonitor {
                  uintptr_t key;
                  uint32_t owner;
                  uint32_t depth;
                };
                static constexpr uint32_t JUNO_MAX_MONITORS = ${JUNO_MAX_MONITORS}u;
                static JunoMonitor juno_monitors[JUNO_MAX_MONITORS];

                ${JUNO_ORDINARY_ENTRY}
                ${JUNO_TASK_ENTRY}

                static JunoThreadObject* juno_thread_of(int32_t handle) {
                  if (handle == 0) juno_panic();
                  return reinterpret_cast<JunoThreadObject*>(static_cast<intptr_t>(handle));
                }

                static bool juno_slot_runnable(uint32_t index, uint32_t now) {
                  const JunoSlot& slot = juno_slots[index];
                  switch (slot.state) {
                    case JUNO_SLOT_READY:
                      return true;
                    case JUNO_SLOT_SLEEPING:
                      return static_cast<int32_t>(now - slot.wake) >= 0;
                    case JUNO_SLOT_JOINING:
                      return (slot.joining->flags & JUNO_THREAD_FINISHED) != 0u;
                    case JUNO_SLOT_JOINING_ALL:
                      for (uint32_t other = 1u; other < JUNO_MAX_THREADS; other++) {
                        if (juno_slots[other].state != JUNO_SLOT_FREE
                            && (juno_slots[other].thread->flags & JUNO_THREAD_DAEMON) == 0u) {
                          return false;
                        }
                      }
                      return true;
                    case JUNO_SLOT_JOINING_SCOPE:
                      return slot.scope->active == 0u || (slot.scope->flags & 8u) != 0u;
                    default:
                      return false;
                  }
                }

                // The next thread after the current one that can run, or JUNO_MAX_THREADS if none can. The
                // current thread comes last, so it is chosen only when nobody else is runnable.
                static uint32_t juno_sched_pick(bool includeCurrent) {
                  uint32_t now = static_cast<uint32_t>(millis());
                  uint32_t last = includeCurrent ? JUNO_MAX_THREADS : JUNO_MAX_THREADS - 1u;
                  for (uint32_t step = 1u; step <= last; step++) {
                    uint32_t next = (juno_current_slot + step) % JUNO_MAX_THREADS;
                    if (juno_slot_runnable(next, now)) return next;
                  }
                  return JUNO_MAX_THREADS;
                }

                static void juno_sched_handoff(uint32_t to, bool finished) {
                  uint32_t from = juno_current_slot;
                  ${JUNO_PENDING_SWAP}
                  ${JUNO_BINDINGS_SWAP}
                  juno_current_slot = to;
                  if (finished) {
                    juno_port_exit(from, to);
                  } else {
                    juno_port_switch(from, to);
                  }
                }

                // The caller has set its own slot's state; returns once this thread is runnable and has the CPU
                // again. With nothing runnable (every thread sleeping or joining) the core's delay idles.
                static void juno_sched_block() {
                  for (;;) {
                    uint32_t next = juno_sched_pick(true);
                    if (next != JUNO_MAX_THREADS) {
                      if (next != juno_current_slot) juno_sched_handoff(next, false);
                      juno_slots[juno_current_slot].state = JUNO_SLOT_READY;
                      return;
                    }
                    ${JUNO_CORE_DELAY}(1u);
                  }
                }

                static void juno_thread_body() {
                  uint32_t slot = juno_current_slot;
                  JunoThreadObject* thread = juno_slots[slot].thread;
                ${JUNO_TASK_BODY}
                  thread->flags |= JUNO_THREAD_FINISHED;
                  juno_slots[slot].state = JUNO_SLOT_FREE;
                  juno_slots[slot].thread = nullptr;
                  juno_slots[slot].joining = nullptr;
                  juno_slots[slot].scope = nullptr;
                  juno_live_threads--;
                  for (;;) {
                    uint32_t next = juno_sched_pick(false);
                    if (next != JUNO_MAX_THREADS) {
                      juno_sched_handoff(next, true);
                      return;
                    }
                    ${JUNO_CORE_DELAY}(1u);
                  }
                }

                // Every thread's stack is a root: this thread's from the collector's own frame up, the rest from
                // wherever each one last switched away.
                static void juno_thread_gc_scan(const uint8_t* marker) {
                  for (uint32_t index = 0u; index < JUNO_MAX_THREADS; index++) {
                    const JunoSlot& slot = juno_slots[index];
                    if (slot.state == JUNO_SLOT_FREE) continue;
                    if (slot.thread != nullptr) juno_gc_mark_candidate(reinterpret_cast<uintptr_t>(slot.thread));
                    if (slot.joining != nullptr) juno_gc_mark_candidate(reinterpret_cast<uintptr_t>(slot.joining));
                    if (slot.scope != nullptr) juno_gc_mark_candidate(reinterpret_cast<uintptr_t>(slot.scope));
                    uintptr_t top = index == 0u ? juno_gc_stack_top : juno_port_stack_top(index);
                    const uint8_t* from = index == juno_current_slot
                        ? marker : reinterpret_cast<const uint8_t*>(juno_port_saved_sp(index));
                    juno_gc_scan_range(from, reinterpret_cast<const uint8_t*>(top));
                  }
                }

                extern "C" int32_t juno_thread_new(int32_t runnable) {
                  auto* thread = static_cast<JunoThreadObject*>(juno_alloc(sizeof(JunoThreadObject), 4));
                  thread->runnable = static_cast<uint32_t>(runnable);
                  thread->id = juno_threads_created++;
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(thread));
                }

                extern "C" void juno_thread_set_daemon(int32_t handle, int32_t daemon) {
                  JunoThreadObject* thread = juno_thread_of(handle);
                  if (daemon != 0) {
                    thread->flags |= JUNO_THREAD_DAEMON;
                  } else {
                    thread->flags &= ~JUNO_THREAD_DAEMON;
                  }
                }

                extern "C" void juno_thread_start(int32_t handle) {
                  JunoThreadObject* thread = juno_thread_of(handle);
                  if ((thread->flags & JUNO_THREAD_STARTED) != 0u) {
                    Serial.println("[juno-thread] IllegalThreadStateException: thread already started");
                    juno_panic();
                  }
                  uint32_t slot = 1u;
                  while (slot < JUNO_MAX_THREADS && juno_slots[slot].state != JUNO_SLOT_FREE) slot++;
                  if (slot == JUNO_MAX_THREADS) {
                    Serial.print("[juno-thread] too many threads: at most ");
                    Serial.print(JUNO_MAX_THREADS);
                    Serial.println(" run at once, the main thread included");
                    juno_panic();
                  }
                  thread->flags |= JUNO_THREAD_STARTED;
                  juno_slots[slot] = {JUNO_SLOT_READY, 0u, thread, nullptr, nullptr, 0};
                  juno_live_threads++;
                  juno_port_prepare(slot);
                  ${JUNO_START_BINDINGS}
                }

                extern "C" void juno_thread_join(int32_t handle) {
                  JunoThreadObject* thread = juno_thread_of(handle);
                  if ((thread->flags & JUNO_THREAD_STARTED) == 0u) return;
                  while ((thread->flags & JUNO_THREAD_FINISHED) == 0u) {
                    juno_slots[juno_current_slot].state = JUNO_SLOT_JOINING;
                    juno_slots[juno_current_slot].joining = thread;
                    juno_sched_block();
                  }
                  juno_slots[juno_current_slot].joining = nullptr;
                }

                ${JUNO_TASK_RUNTIME}

                extern "C" int32_t juno_thread_is_alive(int32_t handle) {
                  JunoThreadObject* thread = juno_thread_of(handle);
                  return (thread->flags & JUNO_THREAD_STARTED) != 0u
                      && (thread->flags & JUNO_THREAD_FINISHED) == 0u ? 1 : 0;
                }

                extern "C" void juno_thread_sleep(int64_t millisToSleep) {
                  while (millisToSleep > 0) {
                    uint32_t chunk = millisToSleep > 0x3FFFFFFF ? 0x3FFFFFFFu : static_cast<uint32_t>(millisToSleep);
                    millisToSleep -= chunk;
                    if (juno_live_threads == 0u) {
                      ${JUNO_CORE_DELAY}(chunk);
                    } else {
                      juno_slots[juno_current_slot].state = JUNO_SLOT_SLEEPING;
                      juno_slots[juno_current_slot].wake = static_cast<uint32_t>(millis()) + chunk;
                      juno_sched_block();
                    }
                  }
                }

                // What Delay.millis becomes once the program has threads.
                extern "C" void juno_thread_delay(uint32_t millisToSleep) {
                  juno_thread_sleep(static_cast<int64_t>(millisToSleep));
                }

                extern "C" void juno_thread_yield() {
                  yield();
                  if (juno_live_threads != 0u) juno_sched_block();
                }

                static JunoMonitor* juno_monitor_find(uintptr_t key, bool create) {
                  JunoMonitor* available = nullptr;
                  for (uint32_t index = 0u; index < JUNO_MAX_MONITORS; index++) {
                    if (juno_monitors[index].key == key) return &juno_monitors[index];
                    if (available == nullptr && juno_monitors[index].key == 0u) available = &juno_monitors[index];
                  }
                  if (!create) return nullptr;
                  if (available == nullptr) {
                    Serial.print("[juno-monitor] too many monitors: at most ");
                    Serial.println(JUNO_MAX_MONITORS);
                    juno_panic();
                  }
                  available->key = key;
                  return available;
                }

                static int32_t juno_monitor_try_enter_handle(int32_t handle) {
                  if (handle == 0) juno_panic();
                  JunoMonitor* monitor = juno_monitor_find(static_cast<uintptr_t>(static_cast<uint32_t>(handle)), true);
                  uint32_t owner = juno_current_slot + 1u;
                  if (monitor->owner != 0u && monitor->owner != owner) return 0;
                  monitor->owner = owner;
                  monitor->depth++;
                  return 1;
                }

                extern "C" void juno_monitor_enter(int32_t handle) {
                  while (juno_monitor_try_enter_handle(handle) == 0) juno_thread_yield();
                }

                extern "C" int32_t juno_monitor_try_enter(int32_t handle) {
                  return juno_monitor_try_enter_handle(handle);
                }

                extern "C" int32_t juno_reentrant_lock_new() {
                  void* lock = juno_alloc(sizeof(uint32_t), sizeof(uint32_t));
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(lock));
                }

                extern "C" void juno_monitor_exit(int32_t handle) {
                  if (handle == 0) juno_panic();
                  JunoMonitor* monitor = juno_monitor_find(
                      static_cast<uintptr_t>(static_cast<uint32_t>(handle)), false);
                  uint32_t owner = juno_current_slot + 1u;
                  if (monitor == nullptr || monitor->owner != owner || monitor->depth == 0u) {
                    Serial.println("[juno-monitor] unlock by non-owner");
                    juno_panic();
                  }
                  monitor->depth--;
                  if (monitor->depth == 0u) {
                    monitor->owner = 0u;
                    monitor->key = 0u;
                  }
                }

                // Every loop backedge: keep the core serviced and, at most once per millisecond, give the other
                // threads a turn so a thread that never sleeps cannot starve them.
                extern "C" void juno_thread_backedge() {
                  yield();
                  if (juno_live_threads == 0u) return;
                  uint32_t now = static_cast<uint32_t>(millis());
                  if (now == juno_slice_start) return;
                  juno_slice_start = now;
                  juno_sched_block();
                }

                // Where main() returns: like the JVM, wait for every other (non-daemon) thread to finish.
                extern "C" void juno_thread_main_exit() {
                  if (juno_live_threads == 0u) return;
                  juno_slots[juno_current_slot].state = JUNO_SLOT_JOINING_ALL;
                  juno_sched_block();
                }
                """.replace("${JUNO_PENDING_SWAP}", pendingSave)
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
                .replace("${JUNO_MAX_MONITORS}", Integer.toString(RuntimeLimits.MAX_MONITORS))
                .replace("${JUNO_FAILED_EXCEPTION_CLASS_ID}", Integer.toString(failedExceptionClassId))
                .replace("${JUNO_ILLEGAL_STATE_EXCEPTION_CLASS_ID}", Integer.toString(illegalStateExceptionClassId));
    }

    /** Joiner-based scopes and subtask operations inserted only when the program reaches that API. */
    private static String taskRuntime() {
        return """
                static constexpr uint32_t JUNO_JOINER_ALL_SUCCESSFUL = 1u;
                static constexpr uint32_t JUNO_JOINER_ANY_SUCCESSFUL = 2u;
                static constexpr uint32_t JUNO_JOINER_AWAIT_ALL_SUCCESSFUL = 3u;
                static constexpr uint32_t JUNO_JOINER_AWAIT_ALL = 4u;
                static constexpr uint32_t JUNO_SCOPE_JOINED = 1u;
                static constexpr uint32_t JUNO_SCOPE_CLOSED = 2u;
                static constexpr uint32_t JUNO_SCOPE_HAS_RESULT = 4u;
                static constexpr uint32_t JUNO_SCOPE_SHUTDOWN = 8u;

                static JunoTaskScope* juno_task_scope_of(int32_t handle) {
                  if (handle == 0) juno_panic();
                  return reinterpret_cast<JunoTaskScope*>(static_cast<intptr_t>(handle));
                }

                static int32_t juno_task_exception(uint32_t classId, const char* message) {
                  auto* exception = static_cast<int32_t*>(juno_alloc(8u, 4u));
                  exception[0] = static_cast<int32_t>(classId);
                  exception[1] = static_cast<int32_t>(reinterpret_cast<intptr_t>(message));
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(exception));
                }

                static void juno_task_raise_illegal_state(const char* message) {
                  juno_throw_raise(juno_task_exception(${JUNO_ILLEGAL_STATE_EXCEPTION_CLASS_ID}u, message));
                }

                static void juno_task_raise_failed(int32_t failure) {
                  const char* message = "structured subtask failed";
                  if (failure != 0) {
                    const int32_t* header = reinterpret_cast<const int32_t*>(static_cast<intptr_t>(failure));
                    const char* original = reinterpret_cast<const char*>(static_cast<intptr_t>(header[1]));
                    if (original != nullptr) message = original;
                  }
                  juno_throw_raise(juno_task_exception(${JUNO_FAILED_EXCEPTION_CLASS_ID}u, message));
                }

                static void juno_task_remove(JunoTaskScope* scope, JunoThreadObject* task) {
                  for (uint32_t index = 0u; index < JUNO_MAX_THREADS - 1u; index++) {
                    if (scope->tasks[index] == task) scope->tasks[index] = nullptr;
                  }
                  if (scope->active != 0u) scope->active--;
                }

                static void juno_task_cancel_siblings(JunoTaskScope* scope, JunoThreadObject* completed);

                // A cancelled slot never reaches its monitorexit / unlock, so its monitors are released here.
                static void juno_task_release_monitors(uint32_t owner) {
                  for (uint32_t index = 0u; index < JUNO_MAX_MONITORS; index++) {
                    if (juno_monitors[index].key != 0u && juno_monitors[index].owner == owner) {
                      juno_monitors[index] = {0u, 0u, 0u};
                    }
                  }
                }

                // Cancelling a task also cancels the subtasks of every scope it owns, so they cannot be orphaned
                // holding scheduler slots. Each child cancel recurses through this same path.
                static void juno_task_cancel_owned(uint32_t owner) {
                  for (uint32_t slot = 1u; slot < JUNO_MAX_THREADS; slot++) {
                    JunoThreadObject* child = juno_slots[slot].thread;
                    if (child == nullptr || (child->flags & JUNO_THREAD_TASK) == 0u
                            || child->scope == nullptr || child->scope->owner != owner) continue;
                    JunoTaskScope* scope = child->scope;
                    scope->flags |= JUNO_SCOPE_SHUTDOWN | JUNO_SCOPE_CLOSED;
                    juno_task_cancel_siblings(scope, nullptr);
                  }
                }

                static void juno_task_cancel(JunoThreadObject* task) {
                  if (task == nullptr || (task->flags & JUNO_THREAD_FINISHED) != 0u) return;
                  task->flags |= JUNO_THREAD_FINISHED | JUNO_TASK_CANCELLED;
                  for (uint32_t slot = 1u; slot < JUNO_MAX_THREADS; slot++) {
                    if (juno_slots[slot].thread != task) continue;
                    juno_task_cancel_owned(slot + 1u);
                    juno_task_release_monitors(slot + 1u);
                    juno_port_cancel(slot);
                    juno_slots[slot] = {JUNO_SLOT_FREE, 0u, nullptr, nullptr, nullptr, 0};
                    if (juno_live_threads != 0u) juno_live_threads--;
                    return;
                  }
                }

                static void juno_task_cancel_siblings(JunoTaskScope* scope, JunoThreadObject* completed) {
                  for (uint32_t index = 0u; index < JUNO_MAX_THREADS - 1u; index++) {
                    JunoThreadObject* task = scope->tasks[index];
                    if (task == nullptr || task == completed) continue;
                    juno_task_cancel(task);
                    scope->tasks[index] = nullptr;
                    if (scope->active != 0u) scope->active--;
                  }
                }

                static void juno_task_complete(JunoThreadObject* task, int32_t result) {
                  JunoTaskScope* scope = task->scope;
                  int32_t failure = juno_pending_exception;
                  juno_pending_exception = 0;
                  juno_slots[juno_current_slot].pending = 0;
                  if (failure == 0) {
                    task->result = result;
                    task->flags |= JUNO_TASK_SUCCESS;
                    if (scope->policy == JUNO_JOINER_ANY_SUCCESSFUL
                            && (scope->flags & JUNO_SCOPE_HAS_RESULT) == 0u) {
                      scope->result = result;
                      scope->flags |= JUNO_SCOPE_HAS_RESULT | JUNO_SCOPE_SHUTDOWN;
                    }
                  } else {
                    task->failure = failure;
                    task->flags |= JUNO_TASK_FAILED;
                    if (scope->firstFailure == 0) scope->firstFailure = failure;
                    if (scope->policy == JUNO_JOINER_ALL_SUCCESSFUL
                            || scope->policy == JUNO_JOINER_AWAIT_ALL_SUCCESSFUL) {
                      scope->flags |= JUNO_SCOPE_SHUTDOWN;
                    }
                  }
                  juno_task_remove(scope, task);
                  if ((scope->flags & JUNO_SCOPE_SHUTDOWN) != 0u) juno_task_cancel_siblings(scope, task);
                }

                extern "C" int32_t juno_task_scope_open(int32_t policy) {
                  if (policy < static_cast<int32_t>(JUNO_JOINER_ALL_SUCCESSFUL)
                          || policy > static_cast<int32_t>(JUNO_JOINER_AWAIT_ALL)) {
                    juno_task_raise_illegal_state("Unsupported StructuredTaskScope Joiner");
                    return 0;
                  }
                  auto* scope = static_cast<JunoTaskScope*>(juno_alloc(sizeof(JunoTaskScope), 4u));
                  scope->policy = static_cast<uint32_t>(policy);
                  scope->owner = juno_current_slot + 1u;
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(scope));
                }

                extern "C" int32_t juno_task_scope_open_default() {
                  return juno_task_scope_open(static_cast<int32_t>(JUNO_JOINER_AWAIT_ALL_SUCCESSFUL));
                }

                static bool juno_task_scope_check_owner(JunoTaskScope* scope) {
                  if (scope->owner == juno_current_slot + 1u) return true;
                  juno_task_raise_illegal_state("StructuredTaskScope used by a non-owner thread");
                  return false;
                }

                static int32_t juno_task_scope_fork(int32_t scopeHandle, int32_t taskFunction, bool runnable) {
                  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
                  if (!juno_task_scope_check_owner(scope)) return 0;
                  if ((scope->flags & (JUNO_SCOPE_CLOSED | JUNO_SCOPE_SHUTDOWN | JUNO_SCOPE_JOINED)) != 0u) {
                    juno_task_raise_illegal_state("StructuredTaskScope cannot fork after join or cancellation");
                    return 0;
                  }
                  uint32_t taskIndex = 0u;
                  while (taskIndex < JUNO_MAX_THREADS - 1u && scope->tasks[taskIndex] != nullptr) taskIndex++;
                  if (taskIndex == JUNO_MAX_THREADS - 1u) {
                    juno_task_raise_illegal_state("StructuredTaskScope has too many active subtasks");
                    return 0;
                  }
                  auto* task = static_cast<JunoThreadObject*>(juno_alloc(sizeof(JunoThreadObject), 4u));
                  task->runnable = static_cast<uint32_t>(taskFunction);
                  task->flags = JUNO_THREAD_TASK | (runnable ? JUNO_TASK_RUNNABLE : 0u);
                  task->id = juno_threads_created++;
                  task->scope = scope;
                  ${JUNO_FORK_BINDINGS}
                  scope->tasks[taskIndex] = task;
                  scope->active++;
                  juno_thread_start(static_cast<int32_t>(reinterpret_cast<intptr_t>(task)));
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(task));
                }

                extern "C" int32_t juno_task_scope_fork_callable(int32_t scopeHandle, int32_t callable) {
                  return juno_task_scope_fork(scopeHandle, callable, false);
                }

                extern "C" int32_t juno_task_scope_fork_runnable(int32_t scopeHandle, int32_t runnable) {
                  return juno_task_scope_fork(scopeHandle, runnable, true);
                }

                extern "C" int32_t juno_task_scope_join(int32_t scopeHandle) {
                  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
                  if (!juno_task_scope_check_owner(scope)) return 0;
                  if ((scope->flags & (JUNO_SCOPE_JOINED | JUNO_SCOPE_CLOSED)) != 0u) {
                    juno_task_raise_illegal_state("join() has already been attempted");
                    return 0;
                  }
                  while (scope->active != 0u && (scope->flags & JUNO_SCOPE_SHUTDOWN) == 0u) {
                    juno_slots[juno_current_slot].state = JUNO_SLOT_JOINING_SCOPE;
                    juno_slots[juno_current_slot].scope = scope;
                    juno_sched_block();
                  }
                  juno_slots[juno_current_slot].scope = nullptr;
                  scope->flags |= JUNO_SCOPE_JOINED;
                  if ((scope->policy == JUNO_JOINER_ALL_SUCCESSFUL
                          || scope->policy == JUNO_JOINER_AWAIT_ALL_SUCCESSFUL)
                          && scope->firstFailure != 0) {
                    juno_task_raise_failed(scope->firstFailure);
                    return 0;
                  }
                  if (scope->policy == JUNO_JOINER_ANY_SUCCESSFUL) {
                    if ((scope->flags & JUNO_SCOPE_HAS_RESULT) == 0u) {
                      juno_task_raise_failed(scope->firstFailure);
                      return 0;
                    }
                    return scope->result;
                  }
                  // allSuccessfulOrThrow returns a non-null scope-backed token. Stream operations
                  // remain outside Juno's deliberately small subset; both await policies return null.
                  return scope->policy == JUNO_JOINER_ALL_SUCCESSFUL ? scopeHandle : 0;
                }

                extern "C" int32_t juno_task_scope_is_cancelled(int32_t scopeHandle) {
                  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
                  return (scope->flags & JUNO_SCOPE_SHUTDOWN) != 0u ? 1 : 0;
                }

                extern "C" int32_t juno_task_get(int32_t taskHandle) {
                  JunoThreadObject* task = juno_thread_of(taskHandle);
                  if ((task->flags & JUNO_TASK_SUCCESS) == 0u) {
                    juno_task_raise_illegal_state("Subtask did not complete successfully");
                    return 0;
                  }
                  return task->result;
                }

                extern "C" int32_t juno_task_state(int32_t taskHandle) {
                  JunoThreadObject* task = juno_thread_of(taskHandle);
                  if ((task->flags & JUNO_TASK_SUCCESS) != 0u) return 1;
                  if ((task->flags & JUNO_TASK_FAILED) != 0u) return 2;
                  return 0;
                }

                extern "C" int32_t juno_task_exception(int32_t taskHandle) {
                  JunoThreadObject* task = juno_thread_of(taskHandle);
                  if ((task->flags & JUNO_TASK_FAILED) == 0u) {
                    juno_task_raise_illegal_state("Subtask did not complete with exception");
                    return 0;
                  }
                  return task->failure;
                }

                extern "C" void juno_task_scope_close(int32_t scopeHandle) {
                  JunoTaskScope* scope = juno_task_scope_of(scopeHandle);
                  if (!juno_task_scope_check_owner(scope)) return;
                  if ((scope->flags & JUNO_SCOPE_JOINED) == 0u) {
                    scope->flags |= JUNO_SCOPE_SHUTDOWN;
                    juno_task_cancel_siblings(scope, nullptr);
                  }
                  scope->flags |= JUNO_SCOPE_CLOSED;
                }
                """;
    }
}
