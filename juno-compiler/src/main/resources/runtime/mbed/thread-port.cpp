// ---- Juno threads: Portenta H7 Mbed RTOS port. Each Juno thread owns an Mbed thread and a stack allocated from
// the Juno arena. A semaphore baton lets the common scheduler decide which Mbed thread may execute Java. ----
static constexpr uint32_t JUNO_SLOT_LIMIT = 0x7FFFFFFFu;
static constexpr uint32_t JUNO_INITIAL_SLOTS = 4u;
static constexpr bool JUNO_PORT_PINS = false;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;

struct JunoMbedTask {
  rtos::Thread* thread;
  rtos::Semaphore* baton;
  uint8_t* stackBlock;
  unsigned char* stack;
};

struct JunoPortSlot {
  JunoMbedTask* task;
};

static rtos::Semaphore juno_mbed_main_baton(0, 1);
static const uint8_t* juno_mbed_main_marker = nullptr;

static JunoPortSlot& juno_port_state(uint32_t slot);
static void* juno_thread_alloc(uint32_t size, const char* message);
static void juno_gc_keep_block(const void* payload);
static void juno_thread_body();

static unsigned char* juno_mbed_align_stack(uint8_t* block) {
  uintptr_t address = reinterpret_cast<uintptr_t>(block);
  return reinterpret_cast<unsigned char*>((address + 7u) & ~static_cast<uintptr_t>(7u));
}

static void juno_mbed_entry(void* argument) {
  uint32_t slot = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(argument));
  JunoMbedTask* task = juno_port_state(slot).task;
  task->baton->acquire();
  juno_thread_body();
  for (;;) rtos::ThisThread::sleep_for(1000);
}

static void juno_port_prepare(uint32_t slot) {
  auto* task = static_cast<JunoMbedTask*>(
      juno_thread_alloc(sizeof(JunoMbedTask), "cannot start virtual thread: arena full"));
  task->stackBlock = static_cast<uint8_t*>(
      juno_thread_alloc(JUNO_THREAD_STACK_BYTES + 8u, "cannot start virtual thread: arena full"));
  task->stack = juno_mbed_align_stack(task->stackBlock);
  auto* semaphoreMemory = juno_thread_alloc(sizeof(rtos::Semaphore), "cannot start virtual thread: arena full");
  task->baton = new (semaphoreMemory) rtos::Semaphore(0, 1);
  auto* threadMemory = juno_thread_alloc(sizeof(rtos::Thread), "cannot start virtual thread: arena full");
  task->thread = new (threadMemory) rtos::Thread(osPriorityNormal, JUNO_THREAD_STACK_BYTES, task->stack, "juno");
  juno_port_state(slot).task = task;
  void* argument = reinterpret_cast<void*>(static_cast<uintptr_t>(slot));
  if (task->thread->start(mbed::callback(juno_mbed_entry, argument)) != osOK) {
    Serial.println("[juno-thread] cannot start Mbed RTOS thread");
    juno_panic();
  }
}

static void juno_port_release(uint32_t slot) {
  juno_port_state(slot).task = nullptr;
}

static void juno_port_cancel(uint32_t slot) {
  JunoMbedTask* task = juno_port_state(slot).task;
  if (task != nullptr && task->thread != nullptr) {
    task->thread->terminate();
    task->thread = nullptr;
  }
  juno_port_release(slot);
}

static bool juno_port_runnable(uint32_t slot) {
  static_cast<void>(slot);
  return true;
}

// Mbed owns the native thread stack and its overflow guard; the common scheduler still calls this hook at every
// guarded I/O boundary and handoff.
static void juno_port_check_stack(uint32_t slot) {
  static_cast<void>(slot);
}

static void juno_port_signal(uint32_t slot) {
  if (slot == 0u) {
    juno_mbed_main_baton.release();
  } else {
    juno_port_state(slot).task->baton->release();
  }
}

static void juno_port_wait(uint32_t slot) {
  if (slot == 0u) {
    juno_mbed_main_baton.acquire();
  } else {
    juno_port_state(slot).task->baton->acquire();
  }
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  uint8_t marker;
  if (from == 0u) juno_mbed_main_marker = &marker;
  juno_port_signal(to);
  juno_port_wait(from);
}

static void juno_port_exit(uint32_t from, uint32_t to) {
  juno_port_signal(to);
  JunoMbedTask* task = juno_port_state(from).task;
  if (task != nullptr && task->thread != nullptr) {
    task->thread->terminate();
  }
  for (;;) rtos::ThisThread::sleep_for(1000);
}

static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  const JunoPortSlot& state = juno_port_state(slot);
  if (slot == 0u) {
    *from = marker != nullptr ? marker : juno_mbed_main_marker;
    *to = reinterpret_cast<const uint8_t*>(juno_gc_stack_top);
    return *from != nullptr;
  }
  if (state.task == nullptr) return false;
  juno_gc_keep_block(state.task);
  juno_gc_keep_block(state.task->stackBlock);
  juno_gc_keep_block(state.task->baton);
  juno_gc_keep_block(state.task->thread);
  *from = marker != nullptr ? marker : state.task->stack;
  *to = state.task->stack + JUNO_THREAD_STACK_BYTES;
  return true;
}
