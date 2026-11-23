// ---- Juno threads: Portenta H7 dual-carrier port (Cortex-M7 + Cortex-M4). ----
// Like the ESP32's: each slot owns one RTOS thread pinned to a carrier, created the first time the slot is used and
// kept for every later thread in that slot, and it parks on its baton between threads. main always runs on the M7,
// where every native call runs too. A slot on the M7 is an Mbed thread of the M7's kernel; a slot on the M4 is an
// Mbed thread of the M4's kernel, created by Juno's M4 carrier image. Both run this image's code. A core only ever
// calls its own kernel: a request for a thread of the other core (create it, give its baton) goes through the link's
// ring for that core and an HSEM interrupt, and that core's service thread carries it out. A slot's task keeps its
// stack for the whole program, so the stack comes from the heap of the core that runs it, never from the arena: the M7's
// in AXI SRAM, the M4's in D2 SRAM, next to the M4. The link publishes each stack's address as both cores see it.
#include <setjmp.h>
#include "rtx_os.h"

static constexpr uint32_t JUNO_INITIAL_SLOTS = JUNO_M4_SLOTS;
static constexpr uint32_t JUNO_SLOT_LIMIT = JUNO_INITIAL_SLOTS;
static constexpr bool JUNO_PORT_PINS = false;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;
static constexpr uint32_t JUNO_PORT_NO_CARRIER = 0xFFFFFFFFu;
// How long main waits for the M4 carrier image to report ready.
static constexpr uint32_t JUNO_M4_START_MILLIS = 3000u;

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

// The clock, the delay and the yield the runtime uses, for the carrier the caller runs on: on the M4 they are the M4
// kernel's, through the link. Generated code and native calls never run on the M4.
extern "C" unsigned long juno_carrier_millis(void) {
  return juno_port_carrier_id() == 0u ? millis() : juno_m4_link->millis();
}

extern "C" void juno_carrier_delay(unsigned long millisToSleep) {
  if (juno_port_carrier_id() == 0u) {
    delay(millisToSleep);
  } else {
    juno_m4_link->sleepMillis(millisToSleep);
  }
}

extern "C" void juno_carrier_yield(void) {
  if (juno_port_carrier_id() == 0u) {
    yield();
  } else {
    juno_m4_link->yield();
  }
}

#define millis juno_carrier_millis
#define delay juno_carrier_delay
#define yield juno_carrier_yield

// The slot table never moves: the other carrier reads it without the runtime lock.
static uint32_t juno_dual_cores[JUNO_INITIAL_SLOTS];
static volatile bool juno_dual_created[JUNO_INITIAL_SLOTS];
// A cancelled thread's task stops at its next safepoint or wake-up and restarts from its entry; until then its slot
// cannot take a new thread.
static volatile bool juno_dual_killed[JUNO_INITIAL_SLOTS];
static volatile uint32_t juno_dual_kills_pending = 0u;
static jmp_buf juno_dual_restart[JUNO_INITIAL_SLOTS];

// The M7's kernel objects: main's baton (slot 0) and every M7 slot's thread and baton, never in the arena.
alignas(rtos::Semaphore) static uint8_t juno_m7_baton_storage[JUNO_INITIAL_SLOTS][sizeof(rtos::Semaphore)];
alignas(rtos::Thread) static uint8_t juno_m7_thread_storage[JUNO_INITIAL_SLOTS][sizeof(rtos::Thread)];
static rtos::Semaphore* juno_m7_batons[JUNO_INITIAL_SLOTS];
static rtos::Thread* juno_m7_threads[JUNO_INITIAL_SLOTS];
static const uint8_t* juno_main_stack_low = nullptr;
alignas(rtos::Semaphore) static uint8_t juno_m7_service_wake_storage[sizeof(rtos::Semaphore)];
static rtos::Semaphore* juno_m7_service_wake = nullptr;
alignas(rtos::Thread) static uint8_t juno_m7_service_storage[sizeof(rtos::Thread)];

static uint32_t juno_port_fixed_carrier(uint32_t slot) {
  return juno_dual_created[slot] ? juno_dual_cores[slot] : JUNO_PORT_NO_CARRIER;
}

static bool juno_port_reusable(uint32_t slot) {
  return !juno_dual_killed[slot];
}

// Lets the slot's task run: it is parked on its baton, given by its own core.
static void juno_port_start(uint32_t slot) {
  uint32_t carrier = slot == 0u ? 0u : juno_dual_cores[slot];
  if (carrier != juno_port_carrier_id()) {
    JunoM4Ring& ring = carrier == 0u ? juno_m4_link->toM7 : juno_m4_link->toM4;
    juno_m4_ring_push(ring, JUNO_M4_OP_GIVE | slot);
    juno_hsem_notify(carrier == 0u ? JUNO_HSEM_NOTIFY_M7 : JUNO_HSEM_NOTIFY_M4);
  } else if (carrier == 0u) {
    juno_m7_batons[slot]->release();
  } else {
    juno_m4_link->batonGive(slot);
  }
}

// Parks the caller, a task of the core it runs on, until its baton is given.
static void juno_port_wait(uint32_t slot) {
  if (juno_port_carrier_id() == 0u) {
    juno_m7_batons[slot]->acquire();
  } else {
    juno_m4_link->batonWait(slot);
  }
}

static void juno_port_drain(uint32_t slot) {
  if (juno_port_carrier_id() == 0u) {
    juno_m7_batons[slot]->try_acquire();
  } else {
    juno_m4_link->batonTryTake(slot);
  }
}

[[noreturn]] static void juno_port_die(uint32_t slot) {
  juno_java_pause();
  while (juno_runtime_owned()) juno_runtime_exit();
  longjmp(juno_dual_restart[slot], 1);
}

static uint32_t juno_port_task_slot() {
  if (juno_port_carrier_id() != 0u) return juno_m4_link->taskSlot();
  osThreadId_t self = rtos::ThisThread::get_id();
  for (uint32_t slot = 1u; slot < JUNO_INITIAL_SLOTS; slot++) {
    if (juno_m7_threads[slot] != nullptr && juno_m7_threads[slot]->get_id() == self) return slot;
  }
  return 0u;
}

// Safepoints and idle waits: a cancelled thread stops here.
static void juno_port_check_killed() {
  if (juno_dual_kills_pending == 0u) return;
  uint32_t slot = juno_port_task_slot();
  if (slot != 0u && juno_dual_killed[slot]) juno_port_die(slot);
}

// Whether this carrier's time slice is over, asked at every safepoint before the runtime lock. The clock is the
// core's own cycle counter, and each carrier only compares against the counter of the core it runs on.
static uint32_t juno_slice_marks[2] = {};
static uint32_t juno_slice_lengths[2] = {};

static inline bool juno_port_slice_due() {
  uint32_t carrier = juno_port_carrier_id();
  uint32_t now = DWT->CYCCNT;
  if (now - juno_slice_marks[carrier] < juno_slice_lengths[carrier]) return false;
  juno_slice_marks[carrier] = now;
  return true;
}

// Mbed's kernels need no time handed to lower-priority work.
static void juno_port_breathe() {
}

// What every slot's task runs, on the M7 as an M7 thread and on the M4 as an M4 thread.
static void juno_portenta_task(uint32_t entrySlot) {
  volatile uint32_t slot = entrySlot;
  if (setjmp(juno_dual_restart[slot]) != 0) {
    juno_carrier_vacate(slot);
    // A give that arrived while the cancelled thread was still running is stale.
    juno_port_drain(slot);
    juno_runtime_enter();
    juno_dual_killed[slot] = false;
    juno_dual_kills_pending--;
    juno_runtime_exit();
  }
  for (;;) {
    juno_port_wait(slot);
    if (juno_dual_killed[slot]) juno_port_die(slot);
    juno_thread_body();
  }
}

static void juno_m7_task_entry(void* argument) {
  juno_portenta_task(static_cast<uint32_t>(reinterpret_cast<uintptr_t>(argument)));
}

// Runs on the M7 only.
static void juno_m7_create(uint32_t slot) {
  auto* stack = new (std::nothrow) uint64_t[JUNO_THREAD_STACK_BYTES / sizeof(uint64_t)];
  if (stack == nullptr) {
    Serial.println("[juno-thread] cannot start virtual thread: no heap left for its task stack");
    juno_panic();
  }
  juno_m4_link->stacks[slot] = reinterpret_cast<uint32_t>(stack);
  juno_m7_batons[slot] = new (juno_m7_baton_storage[slot]) rtos::Semaphore(0, 1);
  juno_m7_threads[slot] = new (juno_m7_thread_storage[slot]) rtos::Thread(osPriorityNormal,
      JUNO_THREAD_STACK_BYTES, reinterpret_cast<unsigned char*>(stack), "juno");
  void* argument = reinterpret_cast<void*>(static_cast<uintptr_t>(slot));
  if (juno_m7_threads[slot]->start(mbed::callback(juno_m7_task_entry, argument)) != osOK) {
    Serial.println("[juno-thread] cannot start Mbed RTOS thread");
    juno_panic();
  }
}

// The M7's service thread: carries out what the M4's threads ask of the M7's kernel.
static void juno_m7_service() {
  for (;;) {
    juno_m7_service_wake->acquire();
    uint32_t item;
    while (juno_m4_ring_pop(juno_m4_link->toM7, &item)) {
      uint32_t slot = item & JUNO_M4_OP_SLOT;
      if ((item & ~JUNO_M4_OP_SLOT) == JUNO_M4_OP_CREATE) {
        juno_m7_create(slot);
      } else {
        juno_m7_batons[slot]->release();
      }
    }
  }
}

static void juno_m7_notified() {
  HSEM->C1ICR = 1u << JUNO_HSEM_NOTIFY_M7;
  juno_m7_service_wake->release();
}

static void juno_port_prepare(uint32_t slot) {
  if (juno_dual_created[slot]) return;
  uint32_t carrier = juno_slot_carrier(slot);
  juno_dual_cores[slot] = carrier;
  juno_m4_link->stacks[slot] = 0u;
  __DMB();
  juno_dual_created[slot] = true;
  if (carrier != juno_port_carrier_id()) {
    JunoM4Ring& ring = carrier == 0u ? juno_m4_link->toM7 : juno_m4_link->toM4;
    juno_m4_ring_push(ring, JUNO_M4_OP_CREATE | slot);
    juno_hsem_notify(carrier == 0u ? JUNO_HSEM_NOTIFY_M7 : JUNO_HSEM_NOTIFY_M4);
  } else if (carrier == 0u) {
    juno_m7_create(slot);
  } else {
    juno_m4_link->createTask(slot);
  }
}

// The thread may be running on the other core right now, so its task is never deleted: it stops itself.
static void juno_port_cancel(uint32_t slot) {
  if (!juno_dual_created[slot] || juno_dual_killed[slot]) return;
  juno_dual_killed[slot] = true;
  juno_dual_kills_pending++;
  juno_port_start(slot);
}

static bool juno_port_runnable(uint32_t slot) {
  static_cast<void>(slot);
  return true;
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  if (from == to) return;
  juno_port_start(to);
  juno_port_wait(from);
  if (from != 0u && juno_dual_killed[from]) juno_port_die(from);
}

// A finished thread hands its carrier on and returns to its task's entry, which parks until the slot's next thread.
static void juno_port_exit(uint32_t from, uint32_t to) {
  static_cast<void>(from);
  juno_port_start(to);
}

static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  if (slot == 0u) {
    if (marker != nullptr) {
      *from = marker;
    } else {
      // main runs on the other carrier or is parked: its whole Mbed thread stack below the entry frame is a root.
      if (juno_main_stack_low == nullptr) return false;
      *from = juno_main_stack_low;
    }
    *to = reinterpret_cast<const uint8_t*>(juno_gc_stack_top);
    return true;
  }
  // A task created through the other core's ring has no stack until that core gets to it, and runs no Java before.
  if (!juno_dual_created[slot] || juno_m4_link->stacks[slot] == 0u) return false;
  const uint8_t* base = reinterpret_cast<const uint8_t*>(juno_m4_link->stacks[slot]);
  *from = marker != nullptr ? marker : base;
  *to = base + JUNO_THREAD_STACK_BYTES;
  return true;
}

static void juno_port_check_stack(uint32_t slot) {
  static_cast<void>(slot);
}

static void juno_slice_counter_start() {
  CoreDebug->DEMCR |= CoreDebug_DEMCR_TRCENA_Msk;
  DWT->CYCCNT = 0u;
  DWT->CTRL |= DWT_CTRL_CYCCNTENA_Msk;
}

// main's setup(): turns the M7's data cache off so both cores see every shared word, starts the M4 carrier image,
// waits until its services are in place, and then runs main on this, the M7's main Mbed thread.
extern "C" void juno_launch_main(void (*entry)()) {
  SCB_DisableDCache();
  juno_slice_counter_start();
  juno_slice_lengths[0] = SystemCoreClock / 1000u * JUNO_TIME_SLICE_MILLIS;
  auto* self = static_cast<osRtxThread_t*>(rtos::ThisThread::get_id());
  juno_main_stack_low = static_cast<const uint8_t*>(self->stack_mem);
  juno_m7_batons[0] = new (juno_m7_baton_storage[0]) rtos::Semaphore(0, 1);
  juno_m7_service_wake = new (juno_m7_service_wake_storage) rtos::Semaphore(0, 1);
  auto* service = new (juno_m7_service_storage) rtos::Thread(osPriorityHigh, 1024u, nullptr, "juno-m4-link");
  service->start(mbed::callback(juno_m7_service));

  __HAL_RCC_HSEM_CLK_ENABLE();
  memset(reinterpret_cast<void*>(juno_m4_link), 0, sizeof(JunoM4Link));
  juno_m4_link->stackBytes = JUNO_THREAD_STACK_BYTES;
  juno_m4_link->taskEntry = juno_portenta_task;
  juno_m4_link->m7MillisAtStart = static_cast<uint32_t>(millis());
  NVIC_SetVector(HSEM1_IRQn, reinterpret_cast<uint32_t>(juno_m7_notified));
  HSEM->C1IER |= 1u << JUNO_HSEM_NOTIFY_M7;
  NVIC_EnableIRQ(HSEM1_IRQn);
  __DMB();
  juno_m4_link->magic = JUNO_M4_LINK_MAGIC;
  bootM4();
  uint32_t started = static_cast<uint32_t>(millis());
  while (juno_m4_link->m4Ready != JUNO_M4_LINK_MAGIC) {
    if (static_cast<uint32_t>(millis()) - started > JUNO_M4_START_MILLIS) {
      Serial.println("[juno-thread] the Portenta H7's M4 carrier did not start: flash this program with juno:flash");
      juno_panic();
    }
    delay(1);
  }
  juno_slice_lengths[1] = juno_m4_link->m4CoreHz / 1000u * JUNO_TIME_SLICE_MILLIS;
  entry();
}
