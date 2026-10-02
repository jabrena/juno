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
    static String renesasPort() {
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
                """.replace("${JUNO_MAX_THREADS}", Integer.toString(RuntimeLimits.MAX_THREADS))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(2048));
    }

    /**
     * The UNO Q's port: every Juno thread is a Zephyr thread, but each parks on its own semaphore and only the
     * one holding the baton runs, so scheduling stays cooperative and the runtime needs no other locking.
     */
    static String zephyrPort() {
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
                """.replace("${JUNO_MAX_THREADS}", Integer.toString(RuntimeLimits.MAX_THREADS))
                .replace("${JUNO_THREAD_STACK_BYTES}", Integer.toString(4096));
    }

    /** The cooperative scheduler and the {@code Thread} intrinsics, over a core's port. */
    static String scheduler(String delayFunction, boolean exceptions) {
        String pendingSave = exceptions ? "juno_slots[from].pending = juno_pending_exception;\n"
                + "  juno_pending_exception = juno_slots[to].pending;" : "";
        String report = exceptions ? """
                  if (juno_pending_exception != 0) {
                    juno_throw_report(juno_pending_exception, static_cast<int32_t>(thread->id));
                    juno_pending_exception = 0;
                  }""" : "";
        return """

                // ---- Juno threads: cooperative scheduler (same for every board) ----
                // Slot 0 is the main thread. A Thread handle is a small arena object, so the collector keeps it
                // (and the Runnable it points at) alive for as long as something references it.
                struct JunoThreadObject {
                  uint32_t runnable;
                  uint32_t flags;
                  uint32_t id;
                  uint32_t reserved;
                };
                static constexpr uint32_t JUNO_THREAD_STARTED = 1u;
                static constexpr uint32_t JUNO_THREAD_FINISHED = 2u;
                static constexpr uint32_t JUNO_THREAD_DAEMON = 4u;

                enum : uint32_t {
                  JUNO_SLOT_FREE,
                  JUNO_SLOT_READY,
                  JUNO_SLOT_SLEEPING,
                  JUNO_SLOT_JOINING,
                  JUNO_SLOT_JOINING_ALL
                };

                struct JunoSlot {
                  uint32_t state;
                  uint32_t wake;
                  JunoThreadObject* thread;
                  JunoThreadObject* joining;
                  int32_t pending;
                };

                static JunoSlot juno_slots[JUNO_MAX_THREADS] = {{JUNO_SLOT_READY, 0u, nullptr, nullptr, 0}};
                static uint32_t juno_current_slot = 0u;
                static uint32_t juno_live_threads = 0u;
                static uint32_t juno_threads_created = 0u;
                static uint32_t juno_slice_start = 0u;

                extern "C" void juno_thread_entry(int32_t runnable);

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
                  juno_thread_entry(static_cast<int32_t>(thread->runnable));
                ${JUNO_THREAD_REPORT}
                  thread->flags |= JUNO_THREAD_FINISHED;
                  juno_slots[slot].state = JUNO_SLOT_FREE;
                  juno_slots[slot].thread = nullptr;
                  juno_slots[slot].joining = nullptr;
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
                  juno_slots[slot] = {JUNO_SLOT_READY, 0u, thread, nullptr, 0};
                  juno_live_threads++;
                  juno_port_prepare(slot);
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
                .replace("${JUNO_THREAD_REPORT}", report)
                .replace("${JUNO_CORE_DELAY}", delayFunction);
    }
}
