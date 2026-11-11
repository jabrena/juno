// ---- Juno threads: UNO Q port. main keeps its Zephyr thread; every other Juno thread is a continuation that runs
// on one carrier, a second Zephyr thread, and keeps its frames in an arena chunk while another thread holds the
// carrier. main and the carrier hand a baton to each other, so only one of them runs Java code at a time. ----
// No stack per slot: the slot table grows in the arena for as long as the arena has room.
static constexpr uint32_t JUNO_SLOT_LIMIT = 0x7FFFFFFFu;
static constexpr uint32_t JUNO_INITIAL_SLOTS = 4u;
// Whether a slot can be held back from running by the port (see juno_port_runnable).
static constexpr bool JUNO_PORT_PINS = true;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;
static constexpr uint32_t JUNO_STACK_CANARY = 0x4A554E4Fu;
#if defined(__ARM_FP)
static constexpr uint32_t JUNO_SWITCH_FRAME_WORDS = 16u + 9u;
#else
static constexpr uint32_t JUNO_SWITCH_FRAME_WORDS = 9u;
#endif
// A chunk grows in steps of this many bytes, so a thread whose depth changes a little reuses its chunk.
static constexpr uint32_t JUNO_CHUNK_STEP = 64u;
// The switcher runs on the carrier's own Zephyr stack, above the part Juno threads run on.
static constexpr uint32_t JUNO_SWITCHER_STACK_BYTES = 1536u;

extern "C" void yield(void);

// One Zephyr stack: Juno threads run in its lower JUNO_THREAD_STACK_BYTES, the switcher (the Zephyr thread's own
// entry) above them, so the stack pointer never leaves the Zephyr thread's stack.
K_THREAD_STACK_DEFINE(juno_zephyr_carrier_stack, JUNO_THREAD_STACK_BYTES + JUNO_SWITCHER_STACK_BYTES);
static struct k_thread juno_zephyr_carrier;
static struct k_sem juno_zephyr_main_baton;
static struct k_sem juno_zephyr_carrier_baton;
static bool juno_zephyr_carrier_started = false;
static uint32_t juno_switcher_sp = 0u;
// The thread main hands the carrier to, and the one a carrier thread asks the switcher for (0: back to main).
static volatile uint32_t juno_carrier_target = 0u;
static volatile uint32_t juno_switch_to = 0u;
static volatile bool juno_in_switcher = false;
// The slot whose frames are on the carrier (0: none, since main never runs there).
static uint32_t juno_carrier_owner = 0u;
static uint32_t juno_frozen_bytes = 0u;
static uint32_t juno_frozen_peak = 0u;

// Per slot: the sp it last switched away at (on the carrier, or main's own stack for slot 0), and the arena
// chunk that holds the bytes from that sp up to the carrier top while the slot is off the carrier.
struct JunoPortSlot {
  uint32_t savedSp;
  uint8_t* chunk;
  uint32_t chunkCapacity;
  uint32_t chunkUsed;
};
static JunoPortSlot& juno_port_state(uint32_t slot);
static void* juno_thread_alloc(uint32_t size, const char* message);
static void juno_gc_keep_block(const void* payload);
// Whether the slot's frames must stay where they are: its I/O worker may still write into them.
static bool juno_slot_pinned(uint32_t slot);

// Saves r4-r11, lr (and s16-s31) on the current stack, stores sp through r0, then runs on the
// stack r1 holds: a thread's first switch finds a frame of zeros whose "return address" is its
// trampoline.
extern "C" void juno_context_switch(uint32_t* saveSp, uint32_t newSp);
__asm__(
    ".syntax unified\n"
    ".thumb\n"
    ".section .text.juno_context_switch,\"ax\",%progbits\n"
    ".global juno_context_switch\n"
    ".thumb_func\n"
    ".type juno_context_switch, %function\n"
    "juno_context_switch:\n"
    "  push {r4-r11, lr}\n"
#if defined(__ARM_FP)
    "  vpush {s16-s31}\n"
#endif
    "  str sp, [r0]\n"
    "  mov sp, r1\n"
#if defined(__ARM_FP)
    "  vpop {s16-s31}\n"
#endif
    "  pop {r4-r11, pc}\n"
    ".text\n");

static void juno_thread_body();

extern "C" void juno_thread_trampoline() {
  juno_thread_body();
  for (;;) {}
}

static uint8_t* juno_carrier_bottom() {
  return reinterpret_cast<uint8_t*>(K_THREAD_STACK_BUFFER(juno_zephyr_carrier_stack));
}

static uint32_t juno_carrier_top() {
  return static_cast<uint32_t>(reinterpret_cast<uintptr_t>(juno_carrier_bottom() + JUNO_THREAD_STACK_BYTES));
}

// Makes sure the slot's chunk holds `bytes`; may collect, so the caller's frames must already be scannable. Only
// the chunk's used part holds frames (scanned as a stack range); the collector never traces the rest, which is stale.
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

// Copies the slot's frames off the carrier into its chunk.
static void juno_freeze(uint32_t slot) {
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

// The carrier's Zephyr thread: put `to` on the carrier and run it until it asks for another thread, and while a
// carrier thread asks for main, hand the baton to main and wait for the next thread main hands over. It runs above
// the Juno threads' part of the stack, so that part can be overwritten under it; a collection it triggers sees every
// slot as switched away.
static void juno_switcher_run(uint32_t to) {
  for (;;) {
    if (to != juno_carrier_owner) {
      if (juno_carrier_owner != 0u) juno_freeze(juno_carrier_owner);
      juno_thaw(to);
      juno_carrier_owner = to;
    }
    juno_in_switcher = false;
    juno_context_switch(&juno_switcher_sp, juno_port_state(to).savedSp);
    to = juno_switch_to;
    if (to == 0u) {
      juno_in_switcher = false;
      k_sem_give(&juno_zephyr_main_baton);
      k_sem_take(&juno_zephyr_carrier_baton, K_FOREVER);
      juno_in_switcher = true;
      to = juno_carrier_target;
    }
  }
}

static void juno_zephyr_carrier_entry(void*, void*, void*) {
  k_sem_take(&juno_zephyr_carrier_baton, K_FOREVER);
  juno_in_switcher = true;
  juno_switcher_run(juno_carrier_target);
}

// The initial frame juno_context_switch pops: zeros, and a "return address" that starts the thread.
static void juno_initial_frame(uint32_t* frame) {
  for (uint32_t index = 0; index < JUNO_SWITCH_FRAME_WORDS + 2u; index++) frame[index] = 0u;
  frame[JUNO_SWITCH_FRAME_WORDS - 1u] = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(&juno_thread_trampoline));
}

static void juno_port_prepare(uint32_t slot) {
  if (!juno_zephyr_carrier_started) {
    juno_zephyr_carrier_started = true;
    k_sem_init(&juno_zephyr_main_baton, 0, 1);
    k_sem_init(&juno_zephyr_carrier_baton, 0, 1);
    uint32_t* canary = reinterpret_cast<uint32_t*>(juno_carrier_bottom());
    canary[0] = JUNO_STACK_CANARY;
    canary[1] = JUNO_STACK_CANARY;
    k_thread_create(&juno_zephyr_carrier, juno_zephyr_carrier_stack,
        K_THREAD_STACK_SIZEOF(juno_zephyr_carrier_stack), juno_zephyr_carrier_entry, nullptr, nullptr, nullptr,
        K_PRIO_PREEMPT(5), 0, K_NO_WAIT);
  }
  // A new thread starts frozen: its chunk holds just the initial frame at the top of the carrier's thread part.
  uint32_t bytes = (JUNO_SWITCH_FRAME_WORDS + 2u) * 4u;
  if (juno_carrier_owner == slot) juno_carrier_owner = 0u;
  juno_port_state(slot).savedSp = juno_carrier_top() - bytes;
  juno_chunk_reserve(slot, bytes);
  JunoPortSlot& state = juno_port_state(slot);
  juno_initial_frame(reinterpret_cast<uint32_t*>(state.chunk));
  state.chunkUsed = bytes;
}

// A finished or cancelled thread will never run again: its chunk goes back to the arena, and the carrier holds
// nothing worth keeping.
static void juno_port_release(uint32_t slot) {
  if (juno_carrier_owner == slot) juno_carrier_owner = 0u;
  JunoPortSlot& state = juno_port_state(slot);
  state.chunk = nullptr;
  state.chunkCapacity = 0u;
  state.chunkUsed = 0u;
}

static void juno_port_cancel(uint32_t slot) {
  juno_port_release(slot);
}

// While the thread on the carrier waits for its I/O worker, the worker may write into its frames, so no other
// carrier thread can take the carrier until the call is over; main still can.
static bool juno_port_runnable(uint32_t slot) {
  return slot == 0u || slot == juno_carrier_owner || juno_carrier_owner == 0u || !juno_slot_pinned(juno_carrier_owner);
}

// Zephyr runs this core without an MPU stack guard, so a Juno thread's stack pointer is checked against
// the carrier's part of the stack from deep inside the runtime (every switch and every guarded I/O call).
static void juno_port_check_stack(uint32_t slot) {
  if (slot == 0u) return;
  const uint32_t* canary = reinterpret_cast<const uint32_t*>(juno_carrier_bottom());
  uint8_t marker;
  bool below = reinterpret_cast<uintptr_t>(&marker) < reinterpret_cast<uintptr_t>(canary + 16);
  if (below || canary[0] != JUNO_STACK_CANARY || canary[1] != JUNO_STACK_CANARY) {
    Serial.println("[juno-thread] stack overflow");
    juno_panic();
  }
}

// main hands the carrier to `to` and waits for the baton; __builtin_unwind_init() spills every callee-saved register
// into this frame first, so the conservative stack scan from the recorded sp sees whatever references they held.
__attribute__((noinline)) static void juno_main_to_carrier(uint32_t to) {
  __builtin_unwind_init();
  uint8_t marker;
  juno_port_state(0).savedSp = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(&marker));
  juno_carrier_target = to;
  k_sem_give(&juno_zephyr_carrier_baton);
  k_sem_take(&juno_zephyr_main_baton, K_FOREVER);
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  if (from == 0u) {
    juno_main_to_carrier(to);
    return;
  }
  juno_port_check_stack(from);
  juno_switch_to = to;
  juno_in_switcher = true;
  juno_context_switch(&juno_port_state(from).savedSp, juno_switcher_sp);
}

static void juno_port_exit(uint32_t from, uint32_t to) {
  static uint32_t discarded;
  juno_port_release(from);
  juno_switch_to = to;
  juno_in_switcher = true;
  juno_context_switch(&discarded, juno_switcher_sp);
}

// The part of one slot's frames the collector scans, or false when the slot has none: main's own stack, the
// carrier for the thread on it, and the used part of a chunk for every other thread. `marker` is the
// collector's own frame when this slot is the one running; inside the switcher no slot is.
static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  if (juno_in_switcher) marker = nullptr;
  const JunoPortSlot& state = juno_port_state(slot);
  const uint8_t* saved = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(state.savedSp));
  if (slot == 0u) {
    *from = marker != nullptr ? marker : saved;
    *to = reinterpret_cast<const uint8_t*>(juno_gc_stack_top);
    return true;
  }
  if (state.chunk != nullptr) juno_gc_keep_block(state.chunk);
  if (slot == juno_carrier_owner) {
    *from = marker != nullptr ? marker : saved;
    *to = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(juno_carrier_top()));
    return true;
  }
  if (state.chunk == nullptr) return false;
  *from = state.chunk;
  *to = state.chunk + state.chunkUsed;
  return true;
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
