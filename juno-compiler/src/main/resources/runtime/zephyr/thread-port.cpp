
// ---- Juno threads: UNO Q port (Zephyr threads handing a baton, one runs at a time) ----
static constexpr uint32_t JUNO_MAX_THREADS = ${JUNO_MAX_THREADS}u;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;

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

// Zephyr runs this core without an MPU stack guard, so a Juno thread's stack pointer is checked against
// its buffer from deep inside the runtime (every guarded I/O call).
static void juno_port_check_stack(uint32_t slot) {
  if (slot == 0u) return;
  uint8_t marker;
  uintptr_t bottom = reinterpret_cast<uintptr_t>(K_THREAD_STACK_BUFFER(juno_zephyr_stacks[slot - 1u]));
  if (reinterpret_cast<uintptr_t>(&marker) < bottom + 64u) {
    Serial.println("[juno-thread] stack overflow");
    juno_panic();
  }
}

extern "C" void juno_preempt_tick(uint32_t* frame);
static struct k_timer juno_preempt_timer;

static void juno_preempt_expiry(struct k_timer* timer) {
  static_cast<void>(timer);
  uint32_t* frame;
  __asm__ volatile("mrs %0, psp" : "=r"(frame));
  juno_preempt_tick(frame);
}

static void juno_port_preempt_start() {
  k_timer_init(&juno_preempt_timer, juno_preempt_expiry, nullptr);
  k_timer_start(&juno_preempt_timer, K_MSEC(JUNO_TIME_SLICE_MILLIS), K_MSEC(JUNO_TIME_SLICE_MILLIS));
}
