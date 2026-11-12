// ---- Juno threads: ESP32 dual-carrier port. ----
// Each slot owns one FreeRTOS task pinned to a core, created the first time the slot is used and kept for every later
// thread in that slot: its stack, TCB and baton are static, so no kernel object ever lives in the arena. A task parks
// on its baton between threads, and both carriers execute generated Java at the same time. Runtime entry points are
// serialized by the dual-core runtime lock.
#include <setjmp.h>

static constexpr uint32_t JUNO_INITIAL_SLOTS = ${JUNO_MAX_THREADS}u;
// One stack per slot, so the table cannot grow past it.
static constexpr uint32_t JUNO_SLOT_LIMIT = JUNO_INITIAL_SLOTS;
static constexpr bool JUNO_PORT_PINS = false;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;
static constexpr uint32_t JUNO_PORT_NO_CARRIER = 0xFFFFFFFFu;

extern "C" void yield(void);
extern "C" uint32_t juno_slot_carrier(uint32_t slot);
extern "C" bool juno_runtime_owned();
extern "C" void juno_runtime_enter();
extern "C" void juno_runtime_exit();
extern "C" void juno_java_pause();

struct JunoPortSlot {
  uint32_t unused;
};

static void juno_thread_body();
static void juno_carrier_vacate(uint32_t slot);

static StackType_t juno_dual_stacks[JUNO_INITIAL_SLOTS][JUNO_THREAD_STACK_BYTES] __attribute__((aligned(16)));
static StaticTask_t juno_dual_tcbs[JUNO_INITIAL_SLOTS];
static StaticSemaphore_t juno_dual_baton_storage[JUNO_INITIAL_SLOTS];
static TaskHandle_t juno_dual_tasks[JUNO_INITIAL_SLOTS];
static SemaphoreHandle_t juno_dual_batons[JUNO_INITIAL_SLOTS];
static uint32_t juno_dual_cores[JUNO_INITIAL_SLOTS];
// A cancelled thread's task stops at its next safepoint or wake-up and restarts from its entry; until then its slot
// cannot take a new thread.
static volatile bool juno_dual_killed[JUNO_INITIAL_SLOTS];
static volatile uint32_t juno_dual_kills_pending = 0u;
static jmp_buf juno_dual_restart[JUNO_INITIAL_SLOTS];
static StaticSemaphore_t juno_main_baton_storage;
static SemaphoreHandle_t juno_main_baton = nullptr;
static TaskHandle_t juno_main_task = nullptr;

extern "C" uint32_t juno_port_carrier_id() {
  return static_cast<uint32_t>(xPortGetCoreID()) & 1u;
}

// The carrier a slot's task is pinned to, or JUNO_PORT_NO_CARRIER while the slot has no task yet.
static uint32_t juno_port_fixed_carrier(uint32_t slot) {
  return juno_dual_tasks[slot] != nullptr ? juno_dual_cores[slot] : JUNO_PORT_NO_CARRIER;
}

static bool juno_port_reusable(uint32_t slot) {
  return !juno_dual_killed[slot];
}

static SemaphoreHandle_t juno_port_baton(uint32_t slot) {
  return slot == 0u ? juno_main_baton : juno_dual_batons[slot];
}

// Lets the slot's task run: it is parked on its baton.
static void juno_port_start(uint32_t slot) {
  xSemaphoreGive(juno_port_baton(slot));
}

[[noreturn]] static void juno_port_die(uint32_t slot) {
  juno_java_pause();
  while (juno_runtime_owned()) juno_runtime_exit();
  longjmp(juno_dual_restart[slot], 1);
}

static uint32_t juno_port_task_slot() {
  TaskHandle_t self = xTaskGetCurrentTaskHandle();
  for (uint32_t slot = 1u; slot < JUNO_INITIAL_SLOTS; slot++) {
    if (juno_dual_tasks[slot] == self) return slot;
  }
  return 0u;
}

// Safepoints and idle waits: a cancelled thread stops here.
static void juno_port_check_killed() {
  if (juno_dual_kills_pending == 0u) return;
  uint32_t slot = juno_port_task_slot();
  if (slot != 0u && juno_dual_killed[slot]) juno_port_die(slot);
}

static void juno_esp32_entry(void* argument) {
  volatile uint32_t slot = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(argument));
  if (setjmp(juno_dual_restart[slot]) != 0) {
    juno_carrier_vacate(slot);
    // A give that arrived while the cancelled thread was still running is stale.
    xSemaphoreTake(juno_dual_batons[slot], 0);
    juno_runtime_enter();
    juno_dual_killed[slot] = false;
    juno_dual_kills_pending--;
    juno_runtime_exit();
  }
  for (;;) {
    xSemaphoreTake(juno_dual_batons[slot], portMAX_DELAY);
    if (juno_dual_killed[slot]) juno_port_die(slot);
    juno_thread_body();
  }
}

static void juno_port_prepare(uint32_t slot) {
  if (juno_main_baton == nullptr) {
    juno_main_task = xTaskGetCurrentTaskHandle();
    juno_main_baton = xSemaphoreCreateBinaryStatic(&juno_main_baton_storage);
  }
  if (juno_dual_tasks[slot] != nullptr) return;
  juno_dual_cores[slot] = juno_slot_carrier(slot);
  juno_dual_batons[slot] = xSemaphoreCreateBinaryStatic(&juno_dual_baton_storage[slot]);
  juno_dual_tasks[slot] = xTaskCreateStaticPinnedToCore(juno_esp32_entry, "juno", JUNO_THREAD_STACK_BYTES,
      reinterpret_cast<void*>(static_cast<uintptr_t>(slot)), uxTaskPriorityGet(juno_main_task),
      juno_dual_stacks[slot], &juno_dual_tcbs[slot], static_cast<BaseType_t>(juno_dual_cores[slot]));
}

// The thread may be running on the other core right now, so its task is never deleted: it stops itself.
static void juno_port_cancel(uint32_t slot) {
  if (juno_dual_tasks[slot] == nullptr || juno_dual_killed[slot]) return;
  juno_dual_killed[slot] = true;
  juno_dual_kills_pending++;
  xSemaphoreGive(juno_dual_batons[slot]);
}

static bool juno_port_runnable(uint32_t slot) {
  static_cast<void>(slot);
  return true;
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  if (from == to) return;
  xSemaphoreGive(juno_port_baton(to));
  xSemaphoreTake(juno_port_baton(from), portMAX_DELAY);
  if (from != 0u && juno_dual_killed[from]) juno_port_die(from);
}

// A finished thread hands its carrier on and returns to its task's entry, which parks until the slot's next thread.
static void juno_port_exit(uint32_t from, uint32_t to) {
  static_cast<void>(from);
  xSemaphoreGive(juno_port_baton(to));
}

static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  if (slot == 0u) {
    if (marker != nullptr) {
      *from = marker;
    } else {
      // main runs on the other carrier or is parked: its whole loop-task stack below the entry frame is a root.
      if (juno_main_task == nullptr) return false;
      *from = reinterpret_cast<const uint8_t*>(pxTaskGetStackStart(juno_main_task));
    }
    *to = reinterpret_cast<const uint8_t*>(juno_gc_stack_top);
    return true;
  }
  if (marker != nullptr) {
    *from = marker;
  } else {
    *from = reinterpret_cast<const uint8_t*>(juno_dual_stacks[slot]);
  }
  *to = reinterpret_cast<const uint8_t*>(&juno_dual_stacks[slot][JUNO_THREAD_STACK_BYTES]);
  return juno_dual_tasks[slot] != nullptr;
}

static void juno_port_check_stack(uint32_t slot) {
  static_cast<void>(slot);
}
