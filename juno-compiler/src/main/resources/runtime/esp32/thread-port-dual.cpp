// ---- Juno threads: ESP32 dual-carrier port. ----
// Each virtual thread is a FreeRTOS task pinned to its assigned core.  The task's own stack is deliberately kept
// separate from the scheduler state: unlike the single-carrier continuation port this lets both carriers execute
// generated Java at the same time.  Runtime entry points are serialized by the dual-core runtime lock.
static constexpr uint32_t JUNO_SLOT_LIMIT = 0x7FFFFFFFu;
static constexpr uint32_t JUNO_INITIAL_SLOTS = 4u;
static constexpr bool JUNO_PORT_PINS = false;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;

extern "C" void yield(void);
extern "C" uint32_t juno_slot_carrier(uint32_t slot);

struct JunoEsp32Task {
  StaticTask_t tcb;
  StaticSemaphore_t batonStorage;
};

struct JunoPortSlot {
  JunoEsp32Task* block;
  TaskHandle_t task;
  SemaphoreHandle_t baton;
};

static JunoPortSlot& juno_port_state(uint32_t slot);
static void* juno_thread_alloc(uint32_t size, const char* message);
static void juno_gc_keep_block(const void* payload);
static void juno_thread_body();

static StackType_t juno_dual_stacks[${JUNO_MAX_THREADS}][JUNO_THREAD_STACK_BYTES] __attribute__((aligned(16)));
static StaticSemaphore_t juno_main_baton_storage;
static SemaphoreHandle_t juno_main_baton = nullptr;
static TaskHandle_t juno_main_task = nullptr;

extern "C" uint32_t juno_port_carrier_id() {
  return static_cast<uint32_t>(xPortGetCoreID()) & 1u;
}

static void juno_port_finished() {
  if (juno_main_baton != nullptr) xSemaphoreGive(juno_main_baton);
}

static void juno_esp32_entry(void* argument) {
  uint32_t slot = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(argument));
  JunoPortSlot& state = juno_port_state(slot);
  xSemaphoreTake(state.baton, portMAX_DELAY);
  juno_thread_body();
  for (;;) vTaskDelay(portMAX_DELAY);
}

static void juno_port_prepare(uint32_t slot) {
  if (juno_main_baton == nullptr) {
    juno_main_task = xTaskGetCurrentTaskHandle();
    juno_main_baton = xSemaphoreCreateBinaryStatic(&juno_main_baton_storage);
  }
  auto* block = static_cast<JunoEsp32Task*>(
      juno_thread_alloc(sizeof(JunoEsp32Task), "cannot start virtual thread: arena full"));
  JunoPortSlot& state = juno_port_state(slot);
  state.block = block;
  state.baton = xSemaphoreCreateBinaryStatic(&block->batonStorage);
  state.task = xTaskCreateStaticPinnedToCore(juno_esp32_entry, "juno", JUNO_THREAD_STACK_BYTES,
      reinterpret_cast<void*>(static_cast<uintptr_t>(slot)), uxTaskPriorityGet(juno_main_task),
      juno_dual_stacks[slot], &block->tcb, static_cast<BaseType_t>(juno_slot_carrier(slot)));
}

static void juno_port_cancel(uint32_t slot) {
  JunoPortSlot& state = juno_port_state(slot);
  if (state.task != nullptr) vTaskDelete(state.task);
  state.task = nullptr;
  state.baton = nullptr;
  state.block = nullptr;
}

static bool juno_port_runnable(uint32_t slot) {
  static_cast<void>(slot);
  return true;
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  if (from == to) return;
  if (to == 0u) {
    xSemaphoreGive(juno_main_baton);
  } else {
    xSemaphoreGive(juno_port_state(to).baton);
  }
  if (from == 0u) {
    xSemaphoreTake(juno_main_baton, portMAX_DELAY);
  } else {
    xSemaphoreTake(juno_port_state(from).baton, portMAX_DELAY);
  }
}

static void juno_port_exit(uint32_t from, uint32_t to) {
  juno_port_switch(from, to);
}

static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  if (slot == 0u) {
    if (marker == nullptr) return false;
    *from = marker;
    *to = reinterpret_cast<const uint8_t*>(juno_gc_stack_top);
    return true;
  }
  const JunoPortSlot& state = juno_port_state(slot);
  if (state.block != nullptr) juno_gc_keep_block(state.block);
  if (marker != nullptr) {
    *from = marker;
  } else {
    *from = reinterpret_cast<const uint8_t*>(juno_dual_stacks[slot]);
  }
  *to = reinterpret_cast<const uint8_t*>(&juno_dual_stacks[slot][JUNO_THREAD_STACK_BYTES]);
  return state.task != nullptr;
}

static void juno_port_check_stack(uint32_t slot) {
  static_cast<void>(slot);
}
