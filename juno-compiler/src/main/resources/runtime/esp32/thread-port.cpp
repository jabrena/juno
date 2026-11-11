// ---- Juno threads: Nano ESP32 port (FreeRTOS tasks handing a baton, one runs at a time) ----
static constexpr uint32_t JUNO_MAX_THREADS = ${JUNO_MAX_THREADS}u;
// A stack per slot, so the slot table never grows past the stacks there are.
static constexpr uint32_t JUNO_SLOT_LIMIT = JUNO_MAX_THREADS;
static constexpr uint32_t JUNO_INITIAL_SLOTS = JUNO_MAX_THREADS;
// Whether a slot can be held back from running by the port (see juno_port_runnable).
static constexpr bool JUNO_PORT_PINS = false;
struct JunoPortSlot {};
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;

extern "C" void yield(void);

// Each slot beyond main's owns one FreeRTOS task, created on first use and then kept: a finished thread's task
// parks on its baton until the slot's next thread is scheduled, so a slot never waits for FreeRTOS to reclaim a
// deleted task before it can be reused. ESP-IDF sizes task stacks in bytes.
static StackType_t juno_esp32_stacks[JUNO_MAX_THREADS - 1u][JUNO_THREAD_STACK_BYTES]
    __attribute__((aligned(16)));
static StaticTask_t juno_esp32_tasks[JUNO_MAX_THREADS - 1u];
static TaskHandle_t juno_esp32_handles[JUNO_MAX_THREADS - 1u];
static StaticSemaphore_t juno_esp32_baton_storage[JUNO_MAX_THREADS];
static SemaphoreHandle_t juno_esp32_batons[JUNO_MAX_THREADS];
static bool juno_esp32_ready = false;
static uintptr_t juno_esp32_sp[JUNO_MAX_THREADS];

static void juno_thread_body();

static void juno_esp32_entry(void* slot) {
  uint32_t index = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(slot));
  for (;;) {
    xSemaphoreTake(juno_esp32_batons[index], portMAX_DELAY);
    juno_thread_body();
  }
}

static uintptr_t juno_port_stack_top(uint32_t slot) {
  return reinterpret_cast<uintptr_t>(juno_esp32_stacks[slot - 1u]) + JUNO_THREAD_STACK_BYTES;
}

static void juno_port_prepare(uint32_t slot) {
  if (!juno_esp32_ready) {
    for (uint32_t index = 0; index < JUNO_MAX_THREADS; index++) {
      juno_esp32_batons[index] = xSemaphoreCreateBinaryStatic(&juno_esp32_baton_storage[index]);
    }
    juno_esp32_ready = true;
  }
  juno_esp32_sp[slot] = juno_port_stack_top(slot);
  if (juno_esp32_handles[slot - 1u] == nullptr) {
    // Same core and priority as the Arduino loop task that runs main, so FreeRTOS never runs two Juno threads at
    // once even before the baton is taken.
    juno_esp32_handles[slot - 1u] = xTaskCreateStaticPinnedToCore(juno_esp32_entry, "juno",
        JUNO_THREAD_STACK_BYTES, reinterpret_cast<void*>(static_cast<uintptr_t>(slot)), uxTaskPriorityGet(nullptr),
        juno_esp32_stacks[slot - 1u], &juno_esp32_tasks[slot - 1u], xPortGetCoreID());
  }
}

// A cancelled subtask is parked on its baton (only the baton holder runs), so deleting it is immediate.
static void juno_port_cancel(uint32_t slot) {
  if (juno_esp32_handles[slot - 1u] != nullptr) {
    vTaskDelete(juno_esp32_handles[slot - 1u]);
    juno_esp32_handles[slot - 1u] = nullptr;
  }
}

// Blocking on the baton makes FreeRTOS spill every live register window into this task's stack, so the
// conservative stack scan from the recorded sp sees whatever references the frames above held.
__attribute__((noinline)) static void juno_port_switch(uint32_t from, uint32_t to) {
  uint8_t marker;
  juno_esp32_sp[from] = reinterpret_cast<uintptr_t>(&marker);
  xSemaphoreGive(juno_esp32_batons[to]);
  xSemaphoreTake(juno_esp32_batons[from], portMAX_DELAY);
}

// The finished thread's last act: pass the baton; its task then parks on its own baton for the slot's next thread.
static void juno_port_exit(uint32_t from, uint32_t to) {
  static_cast<void>(from);
  xSemaphoreGive(juno_esp32_batons[to]);
}

static uintptr_t juno_port_saved_sp(uint32_t slot) {
  return juno_esp32_sp[slot];
}

// The part of one slot's stack the collector scans: from the collector's own frame (`marker`) when the slot is
// running, else from where it last switched away, up to its top.
static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  *from = marker != nullptr ? marker : reinterpret_cast<const uint8_t*>(juno_port_saved_sp(slot));
  *to = reinterpret_cast<const uint8_t*>(slot == 0u ? juno_gc_stack_top : juno_port_stack_top(slot));
  return true;
}

// A Juno thread's stack pointer is checked against its buffer from deep inside the runtime (every guarded I/O
// call), ahead of FreeRTOS's own overflow check at the next context switch.
static void juno_port_check_stack(uint32_t slot) {
  if (slot == 0u) return;
  uint8_t marker;
  uintptr_t bottom = reinterpret_cast<uintptr_t>(juno_esp32_stacks[slot - 1u]);
  if (reinterpret_cast<uintptr_t>(&marker) < bottom + 256u) {
    Serial.println("[juno-thread] stack overflow");
    juno_panic();
  }
}

// Every slot has its own stack, so any runnable slot can run.
static bool juno_port_runnable(uint32_t slot) {
  static_cast<void>(slot);
  return true;
}
