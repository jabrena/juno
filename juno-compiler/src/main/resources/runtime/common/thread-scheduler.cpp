
// ---- Juno threads: preemptive scheduler (same for every board) ----
// Slot 0 is the main thread. A Thread handle is a small arena object, so the collector keeps it
// (and the Runnable it points at) alive for as long as something references it. The slot table starts static and,
// on ports without a fixed stack per slot, grows in the arena, so the arena is the only limit on live threads.
struct JunoTaskScope;
struct JunoTaskConfiguration {
  int32_t timeoutMillis;
};
struct JunoThreadObject {
  uint32_t runnable;
  uint32_t flags;
  uint32_t id;
  // The next subtask of the same scope (a scope keeps its live subtasks as a list).
  JunoThreadObject* sibling;
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
// Thread.interrupt() sets it; the next blocking call on that thread takes it and throws InterruptedException.
static constexpr uint32_t JUNO_THREAD_INTERRUPTED = 256u;

struct JunoTaskScope {
  uint32_t policy;
  uint32_t flags;
  uint32_t active;
  uint32_t owner;
  uint32_t deadline;
  int32_t firstFailure;
  int32_t result;
  JunoThreadObject* tasks;
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
  // Object.wait: the monitor this thread waits on (0 when it is not waiting) and whether a notify reached it.
  uintptr_t waitKey;
  uint32_t notified;
  ${JUNO_SLOT_BINDINGS}
  // Where a preempted thread resumes in generated code (0 when it was not preempted there).
  uint32_t preemptResume;
  // The I/O worker running this thread's offloaded call, plus one (0: none).
  uint32_t ioWorker;
  JunoPortSlot port;
${JUNO_SLOT_CARRIER_FIELD}};

static constexpr uint32_t JUNO_NO_SLOT = 0xFFFFFFFFu;
static JunoSlot juno_initial_slots[JUNO_INITIAL_SLOTS] = {
    {JUNO_SLOT_READY, 0u, nullptr, nullptr, nullptr, 0}};
static JunoSlot* juno_slot_table = juno_initial_slots;
static uint32_t juno_slot_capacity = JUNO_INITIAL_SLOTS;
// The table and its size are compile-time constants where the table cannot grow (a stack per slot), so the scheduler
// stays as cheap as it was with a fixed table: an AVR thread's stack has little room to spare.
#define juno_slots (JUNO_SLOT_LIMIT == JUNO_INITIAL_SLOTS ? juno_initial_slots : juno_slot_table)
#define juno_slot_count (JUNO_SLOT_LIMIT == JUNO_INITIAL_SLOTS ? JUNO_INITIAL_SLOTS : juno_slot_capacity)
${JUNO_SLOT_CARRIER_ACCESSOR}${JUNO_CURRENT_SLOT_DECLARATION}
static uint32_t juno_live_threads = 0u;
static uint32_t juno_threads_created = 0u;
${JUNO_SLICE_START_DECLARATION}${JUNO_CARRIER_DECLARATIONS}

static JunoPortSlot& juno_port_state(uint32_t slot) {
  return juno_slots[slot].port;
}

// Keeps an arena block through the next sweep without tracing its payload.
static void juno_gc_keep_block(const void* payload) {
  uintptr_t base = reinterpret_cast<uintptr_t>(juno_arena);
  uintptr_t address = reinterpret_cast<uintptr_t>(payload);
  if (address < base + 4u || address >= base + juno_arena_used) return;
  reinterpret_cast<uint32_t*>(const_cast<void*>(payload))[-1] |= JUNO_GC_MARK_BIT;
}

// Thread memory (thread objects, the slot table, continuation chunks) comes from the arena like any object; when it
// does not fit even after a collection, the program stops with `message`.
static void* juno_thread_alloc(uint32_t size, const char* message) {
  uint32_t padded = juno_gc_round_up4(size);
  void* memory = juno_gc_try_allocate(padded);
  if (memory == nullptr) {
    juno_gc_collect();
    memory = juno_gc_try_allocate(padded);
  }
  if (memory == nullptr) {
    Serial.print("[juno-thread] ");
    Serial.println(message);
    juno_panic();
  }
  uint8_t* bytes = static_cast<uint8_t*>(memory);
  for (uint32_t index = 0; index < padded; index++) bytes[index] = 0;
  return memory;
}

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

static bool juno_slot_pinned(uint32_t index) {
  uint32_t state = juno_slots[index].state;
  return state == JUNO_SLOT_WAITING_IO || state == JUNO_SLOT_DRAINING;
}

static bool juno_slot_runnable(uint32_t index, uint32_t now) {
  const JunoSlot& slot = juno_slots[index];
  ${JUNO_CARRIER_FILTER}if (JUNO_PORT_PINS && slot.state != JUNO_SLOT_FREE && !juno_port_runnable(index)) return false;
  switch (slot.state) {
    case JUNO_SLOT_READY:
      return true;
    case JUNO_SLOT_SLEEPING:
      return static_cast<int32_t>(now - slot.wake) >= 0;
    case JUNO_SLOT_JOINING:
      return (slot.joining->flags & JUNO_THREAD_FINISHED) != 0u;
    case JUNO_SLOT_JOINING_ALL:
      for (uint32_t other = 1u; other < juno_slot_count; other++) {
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

// The next thread after the current one that can run, or JUNO_NO_SLOT if none can. The
// current thread comes last, so it is chosen only when nobody else is runnable.
${JUNO_IO_REAP_DECLARATION}
${JUNO_PLACEMENT}static uint32_t juno_sched_pick(bool includeCurrent) {
  ${JUNO_IO_REAP_CALL}
  uint32_t now = static_cast<uint32_t>(millis());
  uint32_t count = juno_slot_count;
  uint32_t last = includeCurrent ? count : count - 1u;
  uint32_t next = juno_current_slot;
  for (uint32_t step = 1u; step <= last; step++) {
    next = next + 1u == count ? 0u : next + 1u;
    if (juno_slot_runnable(next, now)) return next;
  }
  return JUNO_NO_SLOT;
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
  ${JUNO_RUNTIME_UNLOCK}for (;;) {
    uint32_t next = juno_sched_pick(true);
    if (next != JUNO_NO_SLOT) {
      if (next != juno_current_slot) juno_sched_handoff(next, false);
      juno_slots[juno_current_slot].state = JUNO_SLOT_READY;
      ${JUNO_RUNTIME_RELOCK}return;
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
    if (next != JUNO_NO_SLOT) {
      juno_sched_handoff(next, true);
      return;
    }
    ${JUNO_FINISHED_SIGNAL}${JUNO_CORE_DELAY}(1u);
  }
}

// Every thread's frames are a root: the running thread's from the collector's own frame up, the rest from
// wherever each one last switched away (the port says where those frames are now).
static void juno_thread_gc_scan(const uint8_t* marker) {
  juno_gc_keep_block(juno_slots);
  for (uint32_t index = 0u; index < juno_slot_count; index++) {
    const JunoSlot& slot = juno_slots[index];
    if (slot.state == JUNO_SLOT_FREE) continue;
    if (slot.thread != nullptr) juno_gc_mark_candidate(reinterpret_cast<uintptr_t>(slot.thread));
    if (slot.joining != nullptr) juno_gc_mark_candidate(reinterpret_cast<uintptr_t>(slot.joining));
    if (slot.scope != nullptr) juno_gc_mark_candidate(reinterpret_cast<uintptr_t>(slot.scope));
    const uint8_t* from;
    const uint8_t* to;
    if (juno_port_live_range(index, ${JUNO_CURRENT_SCAN}, &from, &to)) {
      ${JUNO_STACK_SCAN}(from, to);
    }
  }
}

extern "C" int32_t juno_thread_new(int32_t runnable) {
  auto* thread = static_cast<JunoThreadObject*>(
      juno_thread_alloc(sizeof(JunoThreadObject), "cannot start virtual thread: arena full"));
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

// Every slot is taken: double the table in the arena (ports with a stack per slot stop at JUNO_SLOT_LIMIT instead).
static void juno_slots_grow() {
  if (juno_slot_count >= JUNO_SLOT_LIMIT) {
    Serial.print("[juno-thread] too many threads: at most ");
    Serial.print(JUNO_SLOT_LIMIT);
    Serial.println(" run at once, the main thread included");
    juno_panic();
  }
  uint32_t count = juno_slot_capacity > JUNO_SLOT_LIMIT / 2u ? JUNO_SLOT_LIMIT : juno_slot_capacity * 2u;
  auto* grown = static_cast<JunoSlot*>(
      juno_thread_alloc(count * sizeof(JunoSlot), "cannot start virtual thread: arena full"));
  __builtin_memcpy(grown, juno_slots, juno_slot_count * sizeof(JunoSlot));
  juno_slot_table = grown;
  juno_slot_capacity = count;
}

extern "C" void juno_thread_start(int32_t handle) {
  JunoThreadObject* thread = juno_thread_of(handle);
  if ((thread->flags & JUNO_THREAD_STARTED) != 0u) {
    Serial.println("[juno-thread] IllegalThreadStateException: thread already started");
    juno_panic();
  }
  uint32_t slot = 1u;
  while (slot < juno_slot_count && juno_slots[slot].state != JUNO_SLOT_FREE) slot++;
  if (slot == juno_slot_count) juno_slots_grow();
  thread->flags |= JUNO_THREAD_STARTED;
  juno_slots[slot] = {JUNO_SLOT_READY, 0u, thread, nullptr, nullptr, 0};
${JUNO_SET_CARRIER}  juno_live_threads++;
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

// The interrupt flag of the calling thread, cleared when taken. The main thread has no handle, so it is never interrupted.
static bool juno_interrupt_pending() {
  JunoThreadObject* self = juno_slots[juno_current_slot].thread;
  if (self == nullptr || (self->flags & JUNO_THREAD_INTERRUPTED) == 0u) return false;
  self->flags &= ~JUNO_THREAD_INTERRUPTED;
  return true;
}

// Raises InterruptedException for the calling thread. Without the exception runtime an interrupt cannot be
// requested, so the panic is only a guard.
static void juno_raise_interrupted() {
${JUNO_RAISE_INTERRUPTED}
}

// Raises BrokenBarrierException for a thread whose CyclicBarrier was broken by another thread's interrupt.
static void juno_raise_broken_barrier() {
${JUNO_RAISE_BROKEN_BARRIER}
}

extern "C" void juno_thread_interrupt(int32_t handle) {
  JunoThreadObject* thread = juno_thread_of(handle);
  thread->flags |= JUNO_THREAD_INTERRUPTED;
  // A sleeping thread wakes at once; it sees the flag when its sleep returns.
  for (uint32_t index = 0u; index < juno_slot_count; index++) {
    if (juno_slots[index].thread == thread && juno_slots[index].state == JUNO_SLOT_SLEEPING) {
      juno_slots[index].wake = static_cast<uint32_t>(millis());
    }
  }
}

extern "C" int32_t juno_thread_is_interrupted(int32_t handle) {
  return (juno_thread_of(handle)->flags & JUNO_THREAD_INTERRUPTED) != 0u ? 1 : 0;
}

extern "C" void juno_thread_sleep(int64_t millisToSleep);

// Thread.sleep in a program with threads: it ends early, throwing InterruptedException, when interrupted.
extern "C" void juno_thread_sleep_interruptible(int64_t millisToSleep) {
  if (juno_interrupt_pending()) {
    juno_raise_interrupted();
    return;
  }
  if (juno_live_threads == 0u) {
    juno_thread_sleep(millisToSleep);
    return;
  }
  while (millisToSleep > 0) {
    uint32_t chunk = millisToSleep > 0x3FFFFFFF ? 0x3FFFFFFFu : static_cast<uint32_t>(millisToSleep);
    millisToSleep -= chunk;
    juno_slots[juno_current_slot].state = JUNO_SLOT_SLEEPING;
    juno_slots[juno_current_slot].wake = static_cast<uint32_t>(millis()) + chunk;
    juno_sched_block();
    if (juno_interrupt_pending()) {
      juno_raise_interrupted();
      return;
    }
  }
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

// Lock handles: four words {kind, state, ticket, serving}. The kind is a monitor (ReentrantLock), a fair monitor
// (ReentrantLock(true)) or one end of a ReentrantReadWriteLock; state is the read/write state a read or write lock
// belongs to; ticket and serving are the queue of a fair monitor. A monitor's key stays the handle itself.
static constexpr int32_t JUNO_LOCK_MONITOR = 0;
static constexpr int32_t JUNO_LOCK_READ = 1;
static constexpr int32_t JUNO_LOCK_WRITE = 2;
static constexpr int32_t JUNO_LOCK_FAIR_MONITOR = 3;

extern "C" void juno_monitor_exit(int32_t handle);

static uintptr_t juno_monitor_key(int32_t handle) {
  if (handle == 0) juno_panic();
  return static_cast<uintptr_t>(static_cast<uint32_t>(handle));
}

static int32_t* juno_lock_words(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
}

static bool juno_lock_is_monitor(const int32_t* lock) {
  return lock[0] == JUNO_LOCK_MONITOR || lock[0] == JUNO_LOCK_FAIR_MONITOR;
}

static int32_t juno_new_lock(int32_t kind, int32_t state) {
  auto* lock = static_cast<int32_t*>(juno_alloc(4u * sizeof(int32_t), sizeof(int32_t)));
  lock[0] = kind;
  lock[1] = state;
  lock[2] = 0;
  lock[3] = 0;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(lock));
}

extern "C" int32_t juno_reentrant_lock_new() {
  return juno_new_lock(JUNO_LOCK_MONITOR, 0);
}

// ReentrantLock(boolean fair): a nonzero argument makes the lock fair.
extern "C" int32_t juno_reentrant_lock_new_policy(int32_t fair) {
  return juno_new_lock(fair != 0 ? JUNO_LOCK_FAIR_MONITOR : JUNO_LOCK_MONITOR, 0);
}

// ReentrantReadWriteLock state, five words: {writer (slot + 1, or 0), writer depth, readers, read lock, write lock}.
// A writer may take the lock again; readers wait while a writer holds it, and a writer waits for the readers to leave.
static constexpr uint32_t JUNO_RW_WRITER = 0u;
static constexpr uint32_t JUNO_RW_DEPTH = 1u;
static constexpr uint32_t JUNO_RW_READERS = 2u;

static int32_t* juno_rw_state(int32_t lockHandle) {
  int32_t* lock = juno_lock_words(lockHandle);
  if (juno_lock_is_monitor(lock)) juno_panic();
  return juno_lock_words(lock[1]);
}

extern "C" int32_t juno_rw_lock_new() {
  auto* state = static_cast<int32_t*>(juno_alloc(5u * sizeof(int32_t), sizeof(int32_t)));
  state[JUNO_RW_WRITER] = 0;
  state[JUNO_RW_DEPTH] = 0;
  state[JUNO_RW_READERS] = 0;
  int32_t stateHandle = static_cast<int32_t>(reinterpret_cast<intptr_t>(state));
  state[3] = juno_new_lock(JUNO_LOCK_READ, stateHandle);
  state[4] = juno_new_lock(JUNO_LOCK_WRITE, stateHandle);
  return stateHandle;
}

extern "C" int32_t juno_rw_read_lock(int32_t handle) {
  return juno_lock_words(handle)[3];
}

extern "C" int32_t juno_rw_write_lock(int32_t handle) {
  return juno_lock_words(handle)[4];
}

static bool juno_rw_try_write(int32_t* state, uint32_t self) {
  if (state[JUNO_RW_WRITER] == static_cast<int32_t>(self)) {
    state[JUNO_RW_DEPTH]++;
    return true;
  }
  if (state[JUNO_RW_WRITER] == 0 && state[JUNO_RW_READERS] == 0) {
    state[JUNO_RW_WRITER] = static_cast<int32_t>(self);
    state[JUNO_RW_DEPTH] = 1;
    return true;
  }
  return false;
}

static bool juno_rw_try_read(int32_t* state, uint32_t self) {
  if (state[JUNO_RW_WRITER] != 0 && state[JUNO_RW_WRITER] != static_cast<int32_t>(self)) return false;
  state[JUNO_RW_READERS]++;
  return true;
}

static bool juno_monitor_held_by_self(int32_t handle) {
  JunoMonitor* monitor = juno_monitor_find(juno_monitor_key(handle), false);
  return monitor != nullptr && monitor->owner == juno_current_slot + 1u;
}

// A fair monitor is granted in the order its tickets were taken; a reentrant acquire takes no ticket. With
// interruptible set, an interrupt while queued is remembered: the thread still takes its turn, gives the lock straight
// back and returns false, so the caller can throw InterruptedException without stalling the queue.
static bool juno_fair_monitor_enter(int32_t handle, int32_t* lock, bool interruptible) {
  if (juno_monitor_held_by_self(handle)) {
    juno_monitor_enter(handle);
    return true;
  }
  int32_t ticket = lock[2]++;
  bool interrupted = false;
  for (;;) {
    if (lock[3] == ticket && juno_monitor_try_enter(handle) != 0) {
      lock[3]++;
      if (interrupted) {
        juno_monitor_exit(handle);
        return false;
      }
      return true;
    }
    juno_thread_yield();
    if (interruptible && !interrupted && juno_interrupt_pending()) interrupted = true;
  }
}

// Lock.lock, Lock.tryLock and Lock.unlock on any kind of lock: a ReentrantLock is entered as a monitor.
extern "C" void juno_lock_lock(int32_t handle) {
  int32_t* lock = juno_lock_words(handle);
  if (lock[0] == JUNO_LOCK_MONITOR) {
    juno_monitor_enter(handle);
    return;
  }
  if (lock[0] == JUNO_LOCK_FAIR_MONITOR) {
    juno_fair_monitor_enter(handle, lock, false);
    return;
  }
  int32_t* state = juno_rw_state(handle);
  uint32_t self = juno_current_slot + 1u;
  bool isRead = lock[0] == JUNO_LOCK_READ;
  while (!(isRead ? juno_rw_try_read(state, self) : juno_rw_try_write(state, self))) juno_thread_yield();
}

extern "C" int32_t juno_lock_try_lock(int32_t handle) {
  int32_t* lock = juno_lock_words(handle);
  if (juno_lock_is_monitor(lock)) return juno_monitor_try_enter(handle);
  int32_t* state = juno_rw_state(handle);
  uint32_t self = juno_current_slot + 1u;
  bool isRead = lock[0] == JUNO_LOCK_READ;
  return (isRead ? juno_rw_try_read(state, self) : juno_rw_try_write(state, self)) ? 1 : 0;
}

// Lock.lockInterruptibly: an interrupt before or while waiting throws InterruptedException.
extern "C" void juno_lock_lock_interruptibly(int32_t handle) {
  if (juno_interrupt_pending()) {
    juno_raise_interrupted();
    return;
  }
  int32_t* lock = juno_lock_words(handle);
  if (lock[0] == JUNO_LOCK_FAIR_MONITOR) {
    if (!juno_fair_monitor_enter(handle, lock, true)) juno_raise_interrupted();
    return;
  }
  while (juno_lock_try_lock(handle) == 0) {
    juno_thread_yield();
    if (juno_interrupt_pending()) {
      juno_raise_interrupted();
      return;
    }
  }
}

extern "C" void juno_lock_unlock(int32_t handle) {
  int32_t* lock = juno_lock_words(handle);
  if (juno_lock_is_monitor(lock)) {
    juno_monitor_exit(handle);
    return;
  }
  int32_t* state = juno_rw_state(handle);
  if (lock[0] == JUNO_LOCK_READ) {
    if (state[JUNO_RW_READERS] <= 0) {
      Serial.println("[juno-lock] unlock of a read lock that is not held");
      juno_panic();
    }
    state[JUNO_RW_READERS]--;
    return;
  }
  if (state[JUNO_RW_WRITER] != static_cast<int32_t>(juno_current_slot + 1u)) {
    Serial.println("[juno-lock] unlock of a write lock by a non-owner");
    juno_panic();
  }
  if (--state[JUNO_RW_DEPTH] == 0) state[JUNO_RW_WRITER] = 0;
}

// Milliseconds for a TimeUnit amount, rounded up. The unit is its ordinal: NANOSECONDS first, DAYS last.
static int64_t juno_timeout_millis(int64_t amount, int32_t unit) {
  static const int64_t NANOS_PER_UNIT[] = {1, 1000, 1000000, 1000000000, 60000000000LL, 3600000000000LL,
      86400000000000LL};
  if (unit < 0 || unit > 6) juno_panic();
  if (amount <= 0) return 0;
  int64_t nanos = NANOS_PER_UNIT[unit];
  if (nanos < 1000000) {
    int64_t unitsPerMilli = 1000000 / nanos;
    if (amount > 0x3FFFFFFFLL * unitsPerMilli) return 0x3FFFFFFFLL;
    return (amount + unitsPerMilli - 1) / unitsPerMilli;
  }
  int64_t millisPerUnit = nanos / 1000000;
  if (amount > 0x3FFFFFFFLL / millisPerUnit) return 0x3FFFFFFFLL;
  return amount * millisPerUnit;
}

static bool juno_deadline_passed(uint32_t deadline) {
  return static_cast<int32_t>(static_cast<uint32_t>(millis()) - deadline) >= 0;
}

extern "C" int32_t juno_lock_try_lock_timed(int32_t handle, int64_t amount, int32_t unit) {
  if (juno_interrupt_pending()) {
    juno_raise_interrupted();
    return 0;
  }
  uint32_t deadline = static_cast<uint32_t>(millis()) + static_cast<uint32_t>(juno_timeout_millis(amount, unit));
  for (;;) {
    if (juno_lock_try_lock(handle) != 0) return 1;
    if (juno_deadline_passed(deadline)) return 0;
    juno_thread_yield();
    if (juno_interrupt_pending()) {
      juno_raise_interrupted();
      return 0;
    }
  }
}

static JunoMonitor* juno_monitor_owned(uintptr_t key, const char* operation) {
  JunoMonitor* monitor = juno_monitor_find(key, false);
  if (monitor == nullptr || monitor->owner != juno_current_slot + 1u) {
    Serial.print("[juno-monitor] ");
    Serial.print(operation);
    Serial.println(" without holding the monitor (IllegalMonitorStateException)");
    juno_panic();
  }
  return monitor;
}

extern "C" void juno_monitor_exit(int32_t handle) {
  if (handle == 0) juno_panic();
  JunoMonitor* monitor = juno_monitor_find(juno_monitor_key(handle), false);
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

static void juno_not_held(const char* operation) {
  Serial.print("[juno-lock] ");
  Serial.print(operation);
  Serial.println(" without holding the lock (IllegalMonitorStateException)");
  juno_panic();
}

// Releases the lock a wait is on, all of its depth, and returns that depth to take back afterwards.
static uint32_t juno_release_for_wait(int32_t lockHandle) {
  int32_t* lock = juno_lock_words(lockHandle);
  if (lock[0] == JUNO_LOCK_WRITE) {
    int32_t* state = juno_rw_state(lockHandle);
    if (state[JUNO_RW_WRITER] != static_cast<int32_t>(juno_current_slot + 1u)) juno_not_held("wait");
    uint32_t depth = static_cast<uint32_t>(state[JUNO_RW_DEPTH]);
    state[JUNO_RW_WRITER] = 0;
    state[JUNO_RW_DEPTH] = 0;
    return depth;
  }
  JunoMonitor* monitor = juno_monitor_owned(juno_monitor_key(lockHandle), "wait");
  uint32_t depth = monitor->depth;
  monitor->owner = 0u;
  monitor->depth = 0u;
  monitor->key = 0u;
  return depth;
}

static void juno_reacquire_after_wait(int32_t lockHandle, uint32_t depth) {
  int32_t* lock = juno_lock_words(lockHandle);
  if (lock[0] == JUNO_LOCK_WRITE) {
    int32_t* state = juno_rw_state(lockHandle);
    uint32_t self = juno_current_slot + 1u;
    while (!juno_rw_try_write(state, self)) juno_thread_yield();
    state[JUNO_RW_DEPTH] = static_cast<int32_t>(depth);
    return;
  }
  juno_monitor_enter(lockHandle);
  juno_monitor_find(juno_monitor_key(lockHandle), false)->depth = depth;
}

// Waits on waitKey for a notify or, when timeoutMillis is not negative, for that long. The lock is released for the
// wait and taken back at its depth. Returns whether a notify arrived; an interrupt raises InterruptedException once the
// lock is taken back, as on the JDK.
static bool juno_wait_on(int32_t lockHandle, uintptr_t waitKey, int64_t timeoutMillis) {
  uint32_t index = juno_current_slot;
  uint32_t depth = juno_release_for_wait(lockHandle);
  uint32_t deadline = static_cast<uint32_t>(millis()) + static_cast<uint32_t>(timeoutMillis < 0 ? 0 : timeoutMillis);
  juno_slots[index].waitKey = waitKey;
  juno_slots[index].notified = 0u;
  bool timedOut = false;
  while (juno_slots[index].notified == 0u && !juno_interrupt_pending()) {
    if (timeoutMillis >= 0 && juno_deadline_passed(deadline)) {
      timedOut = true;
      break;
    }
    juno_thread_yield();
  }
  bool notified = juno_slots[index].notified != 0u;
  bool interrupted = !notified && !timedOut;
  juno_slots[index].waitKey = 0u;
  juno_slots[index].notified = 0u;
  juno_reacquire_after_wait(lockHandle, depth);
  if (interrupted) juno_raise_interrupted();
  return notified;
}

extern "C" void juno_monitor_wait(int32_t handle) {
  juno_wait_on(handle, juno_monitor_key(handle), -1);
}

static void juno_wake_waiters(int32_t lockHandle, uintptr_t waitKey, bool all, const char* operation) {
  int32_t* lock = juno_lock_words(lockHandle);
  if (lock[0] == JUNO_LOCK_WRITE) {
    if (juno_rw_state(lockHandle)[JUNO_RW_WRITER] != static_cast<int32_t>(juno_current_slot + 1u)) {
      juno_not_held(operation);
    }
  } else {
    juno_monitor_owned(juno_monitor_key(lockHandle), operation);
  }
  for (uint32_t index = 0u; index < juno_slot_count; index++) {
    JunoSlot& slot = juno_slots[index];
    if (slot.waitKey == waitKey && slot.notified == 0u) {
      slot.notified = 1u;
      if (!all) return;
    }
  }
}

extern "C" void juno_monitor_notify(int32_t handle) {
  juno_wake_waiters(handle, juno_monitor_key(handle), false, "notify");
}

extern "C" void juno_monitor_notify_all(int32_t handle) {
  juno_wake_waiters(handle, juno_monitor_key(handle), true, "notifyAll");
}

// CountDownLatch and Semaphore: an arena cell each. Latch: one word, the count. Semaphore: four words {permits,
// fair, ticket, serving}. The waits poll with yield, like monitor entry does.
static int32_t* juno_counter_cell(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
}

extern "C" int32_t juno_latch_new(int32_t count) {
  if (count < 0) {
    Serial.println("[juno-latch] IllegalArgumentException: count < 0");
    juno_panic();
  }
  auto* cell = static_cast<int32_t*>(juno_alloc(sizeof(int32_t), sizeof(int32_t)));
  *cell = count;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(cell));
}

extern "C" void juno_latch_count_down(int32_t handle) {
  int32_t* count = juno_counter_cell(handle);
  if (*count > 0) (*count)--;
}

extern "C" int64_t juno_latch_get_count(int32_t handle) {
  return static_cast<int64_t>(*juno_counter_cell(handle));
}

extern "C" void juno_latch_await(int32_t handle) {
  int32_t* count = juno_counter_cell(handle);
  if (juno_interrupt_pending()) {
    juno_raise_interrupted();
    return;
  }
  while (*count > 0) {
    juno_thread_yield();
    if (juno_interrupt_pending()) {
      juno_raise_interrupted();
      return;
    }
  }
}

extern "C" int32_t juno_latch_await_timed(int32_t handle, int64_t amount, int32_t unit) {
  int32_t* count = juno_counter_cell(handle);
  if (juno_interrupt_pending()) {
    juno_raise_interrupted();
    return 0;
  }
  uint32_t deadline = static_cast<uint32_t>(millis()) + static_cast<uint32_t>(juno_timeout_millis(amount, unit));
  for (;;) {
    if (*count <= 0) return 1;
    if (juno_deadline_passed(deadline)) return 0;
    juno_thread_yield();
    if (juno_interrupt_pending()) {
      juno_raise_interrupted();
      return 0;
    }
  }
}

// Semaphore(int permits, boolean fair): a nonzero fair makes the acquires queue in ticket order.
extern "C" int32_t juno_semaphore_new_policy(int32_t permits, int32_t fair) {
  auto* cell = static_cast<int32_t*>(juno_alloc(4u * sizeof(int32_t), sizeof(int32_t)));
  cell[0] = permits;
  cell[1] = fair != 0 ? 1 : 0;
  cell[2] = 0;
  cell[3] = 0;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(cell));
}

extern "C" int32_t juno_semaphore_new(int32_t permits) {
  return juno_semaphore_new_policy(permits, 0);
}

// A fair acquire takes its ticket and waits for its turn. As for the fair monitor, an interrupted waiter keeps its turn,
// gives back the permit it did not take and returns false.
static bool juno_fair_acquire(int32_t* cell, bool interruptible) {
  int32_t ticket = cell[2]++;
  bool interrupted = false;
  for (;;) {
    if (cell[3] == ticket && cell[0] > 0) {
      cell[3]++;
      if (interrupted) return false;
      cell[0]--;
      return true;
    }
    juno_thread_yield();
    if (interruptible && !interrupted && juno_interrupt_pending()) interrupted = true;
  }
}

extern "C" void juno_semaphore_acquire(int32_t handle) {
  int32_t* permits = juno_counter_cell(handle);
  if (juno_interrupt_pending()) {
    juno_raise_interrupted();
    return;
  }
  if (permits[1] != 0) {
    if (!juno_fair_acquire(permits, true)) juno_raise_interrupted();
    return;
  }
  while (*permits <= 0) {
    juno_thread_yield();
    if (juno_interrupt_pending()) {
      juno_raise_interrupted();
      return;
    }
  }
  (*permits)--;
}

extern "C" int32_t juno_semaphore_try_acquire(int32_t handle) {
  int32_t* permits = juno_counter_cell(handle);
  if (*permits <= 0) return 0;
  (*permits)--;
  return 1;
}

extern "C" int32_t juno_semaphore_try_acquire_timed(int32_t handle, int64_t amount, int32_t unit) {
  int32_t* permits = juno_counter_cell(handle);
  if (juno_interrupt_pending()) {
    juno_raise_interrupted();
    return 0;
  }
  uint32_t deadline = static_cast<uint32_t>(millis()) + static_cast<uint32_t>(juno_timeout_millis(amount, unit));
  for (;;) {
    if (*permits > 0) {
      (*permits)--;
      return 1;
    }
    if (juno_deadline_passed(deadline)) return 0;
    juno_thread_yield();
    if (juno_interrupt_pending()) {
      juno_raise_interrupted();
      return 0;
    }
  }
}

extern "C" void juno_semaphore_release(int32_t handle) {
  (*juno_counter_cell(handle))++;
}

extern "C" int32_t juno_semaphore_available(int32_t handle) {
  return *juno_counter_cell(handle);
}

// ReentrantLock.newCondition() and WriteLock.newCondition(): one arena word holding the lock's handle. The condition
// itself is the waiting key. A read lock has no conditions: the JDK throws UnsupportedOperationException.
extern "C" int32_t juno_lock_new_condition(int32_t lockHandle) {
  if (juno_lock_words(lockHandle)[0] == JUNO_LOCK_READ) {
    Serial.println("[juno-lock] newCondition on a read lock (UnsupportedOperationException)");
    juno_panic();
  }
  auto* condition = static_cast<int32_t*>(juno_alloc(sizeof(int32_t), sizeof(int32_t)));
  *condition = lockHandle;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(condition));
}

extern "C" void juno_condition_await(int32_t handle) {
  juno_wait_on(*juno_counter_cell(handle), juno_monitor_key(handle), -1);
}

extern "C" int32_t juno_condition_await_timed(int32_t handle, int64_t amount, int32_t unit) {
  return juno_wait_on(*juno_counter_cell(handle), juno_monitor_key(handle), juno_timeout_millis(amount, unit)) ? 1 : 0;
}

extern "C" void juno_condition_signal(int32_t handle) {
  juno_wake_waiters(*juno_counter_cell(handle), juno_monitor_key(handle), false, "signal");
}

extern "C" void juno_condition_signal_all(int32_t handle) {
  juno_wake_waiters(*juno_counter_cell(handle), juno_monitor_key(handle), true, "signalAll");
}

// CyclicBarrier: five arena words {parties, waiting, generation, broken, action}. The last thread to arrive trips the
// barrier and returns index 0; the others wait for the generation to change. A thread interrupted while it waits
// breaks the barrier and throws InterruptedException; the other waiters and later callers throw
// BrokenBarrierException, as on the JDK.
static int32_t* juno_barrier_of(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
}

extern "C" int32_t juno_barrier_new(int32_t parties) {
  if (parties <= 0) {
    Serial.println("[juno-barrier] IllegalArgumentException: parties must be positive");
    juno_panic();
  }
  auto* barrier = static_cast<int32_t*>(juno_alloc(5u * sizeof(int32_t), sizeof(int32_t)));
  barrier[0] = parties;
  barrier[1] = 0;
  barrier[2] = 0;
  barrier[3] = 0;
  barrier[4] = 0;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(barrier));
}

// CyclicBarrier(int parties, Runnable action): the last arrival runs the action before the others are released.
extern "C" int32_t juno_barrier_new_action(int32_t parties, int32_t action) {
  int32_t handle = juno_barrier_new(parties);
  juno_barrier_of(handle)[4] = action;
  return handle;
}

extern "C" int32_t juno_barrier_parties(int32_t handle) {
  return juno_barrier_of(handle)[0];
}

extern "C" int32_t juno_barrier_await(int32_t handle) {
  int32_t* barrier = juno_barrier_of(handle);
  if (barrier[3] != 0) {
    juno_raise_broken_barrier();
    return 0;
  }
  if (juno_interrupt_pending()) {
    barrier[3] = 1;
    juno_raise_interrupted();
    return 0;
  }
  int32_t index = barrier[0] - 1 - barrier[1];
  barrier[1]++;
  if (barrier[1] == barrier[0]) {
    barrier[1] = 0;
    if (barrier[4] != 0) {
      ${JUNO_RUN_BARRIER_ACTION}
      // A failed action breaks the barrier: the tripping thread gets its exception, the others BrokenBarrierException.
      if (${JUNO_EXCEPTION_PENDING}) {
        barrier[3] = 1;
        return 0;
      }
    }
    barrier[2]++;
    return 0;
  }
  int32_t generation = barrier[2];
  while (barrier[2] == generation) {
    juno_thread_yield();
    if (barrier[3] != 0) {
      juno_raise_broken_barrier();
      return 0;
    }
    if (juno_interrupt_pending()) {
      barrier[3] = 1;
      juno_raise_interrupted();
      return 0;
    }
  }
  return index;
}

// Phaser: three arena words {registered parties, unarrived parties, phase}. A phase ends when every unarrived party has
// arrived; the next phase then starts with every registered party unarrived. Termination and tiered phasers are not
// supported, so the phase number only grows.
static int32_t* juno_phaser_of(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
}

extern "C" int32_t juno_phaser_new_parties(int32_t parties) {
  if (parties < 0) {
    Serial.println("[juno-phaser] IllegalArgumentException: parties < 0");
    juno_panic();
  }
  auto* phaser = static_cast<int32_t*>(juno_alloc(3u * sizeof(int32_t), sizeof(int32_t)));
  phaser[0] = parties;
  phaser[1] = parties;
  phaser[2] = 0;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(phaser));
}

extern "C" int32_t juno_phaser_new() {
  return juno_phaser_new_parties(0);
}

extern "C" int32_t juno_phaser_register(int32_t handle) {
  int32_t* phaser = juno_phaser_of(handle);
  phaser[0]++;
  phaser[1]++;
  return phaser[2];
}

extern "C" int32_t juno_phaser_bulk_register(int32_t handle, int32_t parties) {
  int32_t* phaser = juno_phaser_of(handle);
  if (parties < 0) {
    Serial.println("[juno-phaser] IllegalArgumentException: parties < 0");
    juno_panic();
  }
  phaser[0] += parties;
  phaser[1] += parties;
  return phaser[2];
}

// One party arrives; the phase ends when it was the last unarrived party. Returns the phase it arrived in.
static int32_t juno_phaser_arrive_one(int32_t* phaser, bool deregister) {
  if (phaser[1] <= 0) {
    Serial.println("[juno-phaser] arrival with no unarrived party (IllegalStateException)");
    juno_panic();
  }
  int32_t arrivalPhase = phaser[2];
  phaser[1]--;
  if (deregister) phaser[0]--;
  if (phaser[1] == 0) {
    phaser[2]++;
    phaser[1] = phaser[0];
  }
  return arrivalPhase;
}

extern "C" int32_t juno_phaser_arrive(int32_t handle) {
  return juno_phaser_arrive_one(juno_phaser_of(handle), false);
}

extern "C" int32_t juno_phaser_arrive_and_deregister(int32_t handle) {
  return juno_phaser_arrive_one(juno_phaser_of(handle), true);
}

// Waits until the phase moves past phase. The uninterruptible waits, as in the JDK, ignore interrupts.
static void juno_phaser_wait_past(int32_t* phaser, int32_t phase, bool interruptible) {
  if (interruptible && juno_interrupt_pending()) {
    juno_raise_interrupted();
    return;
  }
  while (phaser[2] == phase) {
    juno_thread_yield();
    if (interruptible && juno_interrupt_pending()) {
      juno_raise_interrupted();
      return;
    }
  }
}

// Returns the phase after the one this party arrived in, once that phase has ended.
extern "C" int32_t juno_phaser_arrive_and_await(int32_t handle) {
  int32_t* phaser = juno_phaser_of(handle);
  int32_t arrivalPhase = juno_phaser_arrive_one(phaser, false);
  juno_phaser_wait_past(phaser, arrivalPhase, false);
  return arrivalPhase + 1;
}

extern "C" int32_t juno_phaser_await_advance(int32_t handle, int32_t phase) {
  int32_t* phaser = juno_phaser_of(handle);
  juno_phaser_wait_past(phaser, phase, false);
  return phaser[2];
}

extern "C" int32_t juno_phaser_await_advance_interruptibly(int32_t handle, int32_t phase) {
  int32_t* phaser = juno_phaser_of(handle);
  juno_phaser_wait_past(phaser, phase, true);
  return phaser[2];
}

extern "C" int32_t juno_phaser_get_phase(int32_t handle) {
  return juno_phaser_of(handle)[2];
}

extern "C" int32_t juno_phaser_get_registered_parties(int32_t handle) {
  return juno_phaser_of(handle)[0];
}

extern "C" int32_t juno_phaser_get_unarrived_parties(int32_t handle) {
  return juno_phaser_of(handle)[1];
}

${JUNO_PREEMPTION}

// Where main() returns: like the JVM, wait for every other (non-daemon) thread to finish.
${JUNO_MAIN_EXIT}
