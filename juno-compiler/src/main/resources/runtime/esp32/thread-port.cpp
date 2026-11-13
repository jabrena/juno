// ---- Juno threads: ESP32 port. main keeps the Arduino loop task; every other Juno thread is a continuation on one
// carrier stack. Each continuation is a FreeRTOS task whose stack is that same carrier, so FreeRTOS saves and restores
// its registers (every register window spilled) on the carrier as usual; while another thread holds the carrier, its
// part of the carrier lives in an arena chunk. A switcher task, on a stack of its own, copies the carrier out and in.
// Every task runs on main's core at main's priority and parks on its own baton, so one runs Java code at a time. ----
// No stack per slot: the slot table grows in the arena for as long as the arena has room.
static constexpr uint32_t JUNO_SLOT_LIMIT = 0x7FFFFFFFu;
static constexpr uint32_t JUNO_INITIAL_SLOTS = 4u;
// Whether a slot can be held back from running by the port (see juno_port_runnable).
static constexpr bool JUNO_PORT_PINS = false;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;
// A chunk grows in steps of this many bytes, so a thread whose depth changes a little reuses its chunk.
static constexpr uint32_t JUNO_CHUNK_STEP = 64u;
static constexpr uint32_t JUNO_SWITCHER_STACK_BYTES = 4096u;

extern "C" void yield(void);

// The FreeRTOS objects behind one continuation, in one arena block that stays where it is while FreeRTOS uses it.
struct JunoEsp32Task {
  StaticTask_t tcb;
  StaticSemaphore_t batonStorage;
};

// Per slot: its task (none until it first runs), baton, carrier position and chunk.
struct JunoPortSlot {
  JunoEsp32Task* block;
  TaskHandle_t task;
  SemaphoreHandle_t baton;
  uint32_t savedSp;
  uint8_t* chunk;
  uint32_t chunkCapacity;
  uint32_t chunkUsed;
};
static JunoPortSlot& juno_port_state(uint32_t slot);
static void* juno_thread_alloc(uint32_t size, const char* message);
static void juno_gc_keep_block(const void* payload);

static StackType_t juno_esp32_carrier[JUNO_THREAD_STACK_BYTES] __attribute__((aligned(16)));
static StackType_t juno_esp32_switcher_stack[JUNO_SWITCHER_STACK_BYTES] __attribute__((aligned(16)));
static StaticTask_t juno_esp32_switcher_tcb;
static StaticSemaphore_t juno_esp32_switcher_baton_storage;
static StaticSemaphore_t juno_esp32_main_baton_storage;
static SemaphoreHandle_t juno_esp32_switcher_baton = nullptr;
static SemaphoreHandle_t juno_esp32_main_baton = nullptr;
static TaskHandle_t juno_esp32_main_task = nullptr;
// What a carrier thread or main asks the switcher for.
static volatile uint32_t juno_switch_from = 0u;
static volatile uint32_t juno_switch_to = 0u;
static volatile bool juno_switch_exiting = false;
static volatile bool juno_in_switcher = false;
// The slot whose frames are on the carrier (0: none, since main never runs there).
static uint32_t juno_carrier_owner = 0u;
static uint32_t juno_frozen_bytes = 0u;
static uint32_t juno_frozen_peak = 0u;

static void juno_thread_body();

static uint32_t juno_carrier_top() {
  return static_cast<uint32_t>(reinterpret_cast<uintptr_t>(juno_esp32_carrier) + JUNO_THREAD_STACK_BYTES);
}

// FreeRTOS keeps a parked task's stack pointer in the first word of its TCB, below the registers it saved.
static uint32_t juno_esp32_saved_sp(TaskHandle_t task) {
  return *reinterpret_cast<const uint32_t*>(task);
}

static void juno_esp32_entry(void* slot) {
  JunoPortSlot& state = juno_port_state(static_cast<uint32_t>(reinterpret_cast<uintptr_t>(slot)));
  xSemaphoreTake(state.baton, portMAX_DELAY);
  juno_thread_body();
  for (;;) vTaskDelay(portMAX_DELAY);
}

// A carrier thread may only be copied once FreeRTOS has parked it on its baton.
static void juno_esp32_wait_parked(TaskHandle_t task) {
  while (eTaskGetState(task) != eBlocked) taskYIELD();
}

// The FPU's registers are saved lazily, into a save area at the top of the task's stack: the carrier's. Touching the
// FPU here makes FreeRTOS save the carrier thread's registers into its own image before that image is copied, so a
// thawed thread never inherits another one's.
__attribute__((noinline)) static void juno_esp32_take_fpu() {
  volatile float value = 1.0f;
  value = value * 1.5f;
}

// Makes sure the slot's chunk holds `bytes`; may collect, so every frame must already be scannable. Only the chunk's
// used part holds frames (scanned as a stack range); the collector never traces the rest, which is stale.
static void juno_chunk_reserve(uint32_t slot, uint32_t bytes) {
  JunoPortSlot& state = juno_port_state(slot);
  if (state.chunk != nullptr && state.chunkCapacity >= bytes) return;
  uint32_t capacity = (bytes + JUNO_CHUNK_STEP - 1u) & ~(JUNO_CHUNK_STEP - 1u);
  state.chunk = nullptr;
  state.chunkUsed = 0u;
  auto* chunk = static_cast<uint8_t*>(juno_thread_alloc(capacity, "cannot park virtual thread: arena full"));
  JunoPortSlot& after = juno_port_state(slot);
  after.chunk = chunk;
  after.chunkCapacity = capacity;
}

// Copies a parked slot's part of the carrier, from the stack pointer FreeRTOS saved up to the top, into its chunk.
static void juno_freeze(uint32_t slot) {
  juno_port_state(slot).savedSp = juno_esp32_saved_sp(juno_port_state(slot).task);
  uint32_t bytes = juno_carrier_top() - juno_port_state(slot).savedSp;
  juno_chunk_reserve(slot, bytes);
  JunoPortSlot& state = juno_port_state(slot);
  __builtin_memcpy(state.chunk, reinterpret_cast<const void*>(static_cast<uintptr_t>(state.savedSp)), bytes);
  state.chunkUsed = bytes;
  juno_frozen_bytes += bytes;
  if (bytes > juno_frozen_peak) juno_frozen_peak = bytes;
}

// Copies the slot's frames from its chunk back onto the carrier, at the addresses they were taken from.
static void juno_thaw(uint32_t slot) {
  JunoPortSlot& state = juno_port_state(slot);
  __builtin_memcpy(reinterpret_cast<void*>(static_cast<uintptr_t>(state.savedSp)), state.chunk, state.chunkUsed);
}

// Starts a thread's task on the carrier, which must hold no other thread's frames: FreeRTOS fills the stack and lays
// the first frame at its top.
static void juno_esp32_create(uint32_t slot) {
  JunoPortSlot& state = juno_port_state(slot);
  state.task = xTaskCreateStaticPinnedToCore(juno_esp32_entry, "juno", JUNO_THREAD_STACK_BYTES,
      reinterpret_cast<void*>(static_cast<uintptr_t>(slot)), uxTaskPriorityGet(juno_esp32_main_task),
      juno_esp32_carrier, &state.block->tcb, xPortGetCoreID());
}

// A finished or cancelled thread will never run again: its task goes, and its chunk and FreeRTOS objects go back to
// the arena. Its task is parked on its baton, so deleting it is immediate.
static void juno_esp32_drop(uint32_t slot) {
  JunoPortSlot& state = juno_port_state(slot);
  if (state.task != nullptr) vTaskDelete(state.task);
  if (juno_carrier_owner == slot) juno_carrier_owner = 0u;
  state.task = nullptr;
  state.block = nullptr;
  state.baton = nullptr;
  state.chunk = nullptr;
  state.chunkCapacity = 0u;
  state.chunkUsed = 0u;
}

// The switcher: on main's core, at main's priority, so it runs only once the thread that called it has parked.
static void juno_esp32_switcher(void*) {
  for (;;) {
    xSemaphoreTake(juno_esp32_switcher_baton, portMAX_DELAY);
    juno_in_switcher = true;
    uint32_t from = juno_switch_from;
    uint32_t to = juno_switch_to;
    if (from != 0u) juno_esp32_wait_parked(juno_port_state(from).task);
    juno_esp32_take_fpu();
    if (juno_switch_exiting) {
      juno_switch_exiting = false;
      juno_esp32_drop(from);
    }
    if (to == 0u) {
      juno_in_switcher = false;
      xSemaphoreGive(juno_esp32_main_baton);
      continue;
    }
    if (to != juno_carrier_owner) {
      if (juno_carrier_owner != 0u) {
        juno_esp32_wait_parked(juno_port_state(juno_carrier_owner).task);
        juno_freeze(juno_carrier_owner);
      }
      if (juno_port_state(to).task == nullptr) {
        juno_esp32_create(to);
      } else {
        juno_thaw(to);
      }
      juno_carrier_owner = to;
    }
    juno_in_switcher = false;
    xSemaphoreGive(juno_port_state(to).baton);
  }
}

static void juno_esp32_request(uint32_t from, uint32_t to, bool exiting) {
  juno_switch_from = from;
  juno_switch_to = to;
  juno_switch_exiting = exiting;
  xSemaphoreGive(juno_esp32_switcher_baton);
}

static void juno_port_prepare(uint32_t slot) {
  if (juno_esp32_switcher_baton == nullptr) {
    juno_esp32_main_task = xTaskGetCurrentTaskHandle();
    juno_esp32_switcher_baton = xSemaphoreCreateBinaryStatic(&juno_esp32_switcher_baton_storage);
    juno_esp32_main_baton = xSemaphoreCreateBinaryStatic(&juno_esp32_main_baton_storage);
    xTaskCreateStaticPinnedToCore(juno_esp32_switcher, "juno-switch", JUNO_SWITCHER_STACK_BYTES, nullptr,
        uxTaskPriorityGet(nullptr), juno_esp32_switcher_stack, &juno_esp32_switcher_tcb, xPortGetCoreID());
  }
  if (juno_carrier_owner == slot) juno_carrier_owner = 0u;
  auto* block = static_cast<JunoEsp32Task*>(
      juno_thread_alloc(sizeof(JunoEsp32Task), "cannot start virtual thread: arena full"));
  JunoPortSlot& state = juno_port_state(slot);
  state.block = block;
  state.task = nullptr;
  state.baton = xSemaphoreCreateBinaryStatic(&block->batonStorage);
  state.chunk = nullptr;
  state.chunkCapacity = 0u;
  state.chunkUsed = 0u;
}

// A cancelled task is parked on its baton (only the baton holder runs), so deleting it is immediate.
static void juno_port_cancel(uint32_t slot) {
  juno_esp32_drop(slot);
}

// No I/O worker ever writes into a thread's frames here, so any runnable slot can take the carrier.
static bool juno_port_runnable(uint32_t slot) {
  static_cast<void>(slot);
  return true;
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  if (from == 0u) {
    if (to == juno_carrier_owner) {
      xSemaphoreGive(juno_port_state(to).baton);
    } else {
      juno_esp32_request(0u, to, false);
    }
    xSemaphoreTake(juno_esp32_main_baton, portMAX_DELAY);
    return;
  }
  if (to == 0u) {
    xSemaphoreGive(juno_esp32_main_baton);
  } else {
    juno_esp32_request(from, to, false);
  }
  xSemaphoreTake(juno_port_state(from).baton, portMAX_DELAY);
}

// The finished thread's last act: the switcher deletes its task once it has parked on its baton.
static void juno_port_exit(uint32_t from, uint32_t to) {
  SemaphoreHandle_t baton = juno_port_state(from).baton;
  juno_esp32_request(from, to, true);
  xSemaphoreTake(baton, portMAX_DELAY);
}

// The part of one slot's frames the collector scans, or false when the slot has none: main's own stack and the carrier
// for the thread on it, each from the sp FreeRTOS saved when it last stopped running (parking or time-sliced, every
// live register window spilled), and the used part of a chunk for every other thread. `marker` is the collector's
// own frame when this slot is the one running; inside the switcher no slot is.
static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  if (juno_in_switcher) marker = nullptr;
  const JunoPortSlot& state = juno_port_state(slot);
  if (slot == 0u) {
    *from = marker != nullptr ? marker
        : reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(juno_esp32_saved_sp(juno_esp32_main_task)));
    *to = reinterpret_cast<const uint8_t*>(juno_gc_stack_top);
    return true;
  }
  if (state.block != nullptr) juno_gc_keep_block(state.block);
  if (state.chunk != nullptr) juno_gc_keep_block(state.chunk);
  if (slot == juno_carrier_owner && state.task != nullptr) {
    *from = marker != nullptr ? marker
        : reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(juno_esp32_saved_sp(state.task)));
    *to = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(juno_carrier_top()));
    return true;
  }
  if (state.chunk == nullptr) return false;
  *from = state.chunk;
  *to = state.chunk + state.chunkUsed;
  return true;
}

// A Juno thread's stack pointer is checked against the carrier from deep inside the runtime (every guarded I/O call),
// ahead of FreeRTOS's own overflow check at the next context switch.
static void juno_port_check_stack(uint32_t slot) {
  if (slot == 0u) return;
  uint8_t marker;
  if (reinterpret_cast<uintptr_t>(&marker) < reinterpret_cast<uintptr_t>(juno_esp32_carrier) + 256u) {
    Serial.println("[juno-thread] stack overflow");
    juno_panic();
  }
}
