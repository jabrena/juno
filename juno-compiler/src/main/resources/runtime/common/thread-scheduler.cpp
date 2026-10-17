
// ---- Juno threads: preemptive scheduler (same for every board) ----
// Slot 0 is the main thread. A Thread handle is a small arena object, so the collector keeps it
// (and the Runnable it points at) alive for as long as something references it.
struct JunoTaskScope;
struct JunoTaskConfiguration {
  int32_t timeoutMillis;
};
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
  uint32_t deadline;
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
  JUNO_SLOT_JOINING_SCOPE,
  JUNO_SLOT_WAITING_IO,
  JUNO_SLOT_DRAINING
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
${JUNO_IO_WAITING_DECLARATION}

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
        if (juno_slots[other].state != JUNO_SLOT_FREE && (juno_slots[other].thread == nullptr
            || (juno_slots[other].thread->flags & JUNO_THREAD_DAEMON) == 0u)) {
          return false;
        }
      }
      return true;
    case JUNO_SLOT_JOINING_SCOPE:
      return slot.scope->active == 0u || (slot.scope->flags & 8u) != 0u
          || (slot.scope->deadline != 0u && static_cast<int32_t>(now - slot.scope->deadline) >= 0);
    ${JUNO_WAITING_IO_CASE}
    default:
      return false;
  }
}

// The next thread after the current one that can run, or JUNO_MAX_THREADS if none can. The
// current thread comes last, so it is chosen only when nobody else is runnable.
${JUNO_IO_REAP_DECLARATION}
static uint32_t juno_sched_pick(bool includeCurrent) {
  ${JUNO_IO_REAP_CALL}
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
  juno_slice_start = static_cast<uint32_t>(millis());
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
    ${JUNO_STACK_SCAN}(from, reinterpret_cast<const uint8_t*>(top));
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
  ${JUNO_START_PREEMPTION}
  ${JUNO_START_BINDINGS}
}

// Thread.startVirtualThread: a virtual thread is a daemon, so main() does not wait for it at exit.
extern "C" int32_t juno_thread_start_virtual(int32_t runnable) {
  int32_t handle = juno_thread_new(runnable);
  juno_thread_set_daemon(handle, 1);
  juno_thread_start(handle);
  return handle;
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

${JUNO_IO_OWNERS}

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

${JUNO_PREEMPTION}

// Where main() returns: like the JVM, wait for every other (non-daemon) thread to finish.
extern "C" void juno_thread_main_exit() {
  if (juno_live_threads == 0u) return;
  juno_slots[juno_current_slot].state = JUNO_SLOT_JOINING_ALL;
  juno_sched_block();
}
