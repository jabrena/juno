
// ---- Juno threads: UNO R4 port (no RTOS): main keeps its own stack; every other thread is a continuation
// that runs on one shared carrier stack and, while another thread holds the carrier, keeps its frames in an
// arena chunk ----
static constexpr uint32_t JUNO_MAX_THREADS = ${JUNO_MAX_THREADS}u;
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
static constexpr uint32_t JUNO_SWITCHER_STACK_BYTES = 1024u;

// The carrier: the stack every Juno thread but main runs on.
alignas(8) static uint8_t juno_carrier_stack[JUNO_THREAD_STACK_BYTES];
// The switcher: a small stack of its own, from which a thread's frames are copied off and onto the carrier.
alignas(8) static uint8_t juno_switcher_stack[JUNO_SWITCHER_STACK_BYTES];
static uint32_t juno_switcher_sp = 0u;
static uint32_t juno_switch_to = 0u;
static volatile bool juno_in_switcher = false;
// The slot whose frames are on the carrier (0: none, since main never runs there).
static uint32_t juno_carrier_owner = 0u;
// Per slot: the sp it last switched away at (on the carrier, or main's own stack for slot 0), and the arena
// chunk that holds the bytes from that sp up to the carrier top while the slot is off the carrier.
static uint32_t juno_saved_sp[JUNO_MAX_THREADS];
static uint8_t* juno_chunks[JUNO_MAX_THREADS];
static uint32_t juno_chunk_capacity[JUNO_MAX_THREADS];
static uint32_t juno_chunk_used[JUNO_MAX_THREADS];
// Frames copied in and out of the carrier, for the risk report and the measurements of the switch cost.
static uint32_t juno_frozen_bytes = 0u;
static uint32_t juno_frozen_peak = 0u;

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

static uint32_t juno_carrier_top() {
  return static_cast<uint32_t>(reinterpret_cast<uintptr_t>(&juno_carrier_stack[JUNO_THREAD_STACK_BYTES]));
}

// Keeps a chunk allocated through the next sweep without tracing its payload: only its used part holds
// frames (scanned as a stack range), the rest is stale.
static void juno_chunk_keep(uint8_t* chunk) {
  reinterpret_cast<uint32_t*>(chunk)[-1] |= JUNO_GC_MARK_BIT;
}

// Makes sure the slot's chunk holds `bytes`; may collect, so the caller's frames must already be scannable.
static void juno_chunk_reserve(uint32_t slot, uint32_t bytes) {
  if (juno_chunks[slot] != nullptr && juno_chunk_capacity[slot] >= bytes) return;
  uint32_t capacity = (bytes + JUNO_CHUNK_STEP - 1u) & ~(JUNO_CHUNK_STEP - 1u);
  juno_chunks[slot] = nullptr;
  juno_chunk_used[slot] = 0u;
  juno_chunks[slot] = static_cast<uint8_t*>(juno_alloc(capacity, 4));
  juno_chunk_capacity[slot] = capacity;
}

// Copies the slot's frames off the carrier into its chunk.
static void juno_freeze(uint32_t slot) {
  uint32_t bytes = juno_carrier_top() - juno_saved_sp[slot];
  juno_chunk_reserve(slot, bytes);
  __builtin_memcpy(juno_chunks[slot], reinterpret_cast<const void*>(static_cast<uintptr_t>(juno_saved_sp[slot])),
      bytes);
  juno_chunk_used[slot] = bytes;
  juno_frozen_bytes += bytes;
  if (bytes > juno_frozen_peak) juno_frozen_peak = bytes;
}

// Copies the slot's frames from its chunk back onto the carrier, at the addresses they were taken from.
static void juno_thaw(uint32_t slot) {
  __builtin_memcpy(reinterpret_cast<void*>(static_cast<uintptr_t>(juno_saved_sp[slot])), juno_chunks[slot],
      juno_chunk_used[slot]);
}

// The switcher's only job: give the carrier to juno_switch_to, then resume it. It runs on its own stack, so
// the carrier can be overwritten under it; a collection it triggers sees every slot as switched away.
static void juno_switcher_loop() {
  for (;;) {
    uint32_t to = juno_switch_to;
    if (juno_carrier_owner != 0u && juno_carrier_owner != to) juno_freeze(juno_carrier_owner);
    juno_thaw(to);
    juno_carrier_owner = to;
    juno_in_switcher = false;
    juno_context_switch(&juno_switcher_sp, juno_saved_sp[to]);
  }
}

extern "C" void juno_switcher_trampoline() {
  juno_switcher_loop();
}

// The initial frame juno_context_switch pops: zeros, and a "return address" that starts the context.
static void juno_initial_frame(uint32_t* frame, void (*entry)()) {
  for (uint32_t index = 0; index < JUNO_SWITCH_FRAME_WORDS + 2u; index++) frame[index] = 0u;
  frame[JUNO_SWITCH_FRAME_WORDS - 1u] = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(entry));
}

static void juno_port_prepare(uint32_t slot) {
  if (juno_switcher_sp == 0u) {
    uint32_t* canary = reinterpret_cast<uint32_t*>(&juno_carrier_stack[0]);
    canary[0] = JUNO_STACK_CANARY;
    canary[1] = JUNO_STACK_CANARY;
    uint32_t* top = reinterpret_cast<uint32_t*>(&juno_switcher_stack[JUNO_SWITCHER_STACK_BYTES]);
    uint32_t* frame = top - 2u - JUNO_SWITCH_FRAME_WORDS;
    juno_initial_frame(frame, &juno_switcher_trampoline);
    juno_switcher_sp = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(frame));
  }
  // A new thread starts frozen: its chunk holds just the initial frame at the top of the carrier.
  uint32_t bytes = (JUNO_SWITCH_FRAME_WORDS + 2u) * 4u;
  if (juno_carrier_owner == slot) juno_carrier_owner = 0u;
  juno_saved_sp[slot] = juno_carrier_top() - bytes;
  juno_chunk_reserve(slot, bytes);
  juno_initial_frame(reinterpret_cast<uint32_t*>(juno_chunks[slot]), &juno_thread_trampoline);
  juno_chunk_used[slot] = bytes;
}

// A finished or cancelled thread will never run again: its chunk goes back to the arena, and the carrier
// holds nothing worth keeping.
static void juno_port_release(uint32_t slot) {
  if (juno_carrier_owner == slot) juno_carrier_owner = 0u;
  juno_chunks[slot] = nullptr;
  juno_chunk_capacity[slot] = 0u;
  juno_chunk_used[slot] = 0u;
}

static void juno_port_cancel(uint32_t slot) {
  juno_port_release(slot);
}

// An overflowed stack may already reach into the runtime's data, and printing (USB serial is deep) from it
// would push further still, so the report runs on a stack of its own.
alignas(8) static uint8_t juno_overflow_stack[512];

[[noreturn]] __attribute__((noinline)) static void juno_stack_overflow_report() {
  Serial.println("[juno-thread] stack overflow");
  juno_panic();
}

[[noreturn]] static void juno_stack_overflow() {
  __asm__ volatile("mov sp, %0" : : "r"(juno_overflow_stack + sizeof juno_overflow_stack) : "memory");
  juno_stack_overflow_report();
}

// A large C frame can allocate straight past the canary without writing it, so the stack pointer itself
// is checked too. Called from deep inside the runtime (every switch and every guarded I/O call).
static void juno_port_check_stack(uint32_t slot) {
  if (slot == 0u) return;
  const uint32_t* canary = reinterpret_cast<const uint32_t*>(&juno_carrier_stack[0]);
  uint8_t marker;
  bool below = reinterpret_cast<uintptr_t>(&marker) < reinterpret_cast<uintptr_t>(canary + 2);
  if (below || canary[0] != JUNO_STACK_CANARY || canary[1] != JUNO_STACK_CANARY) juno_stack_overflow();
}

// Main, and the thread already on the carrier, are resumed in place; any other thread goes through the switcher.
static void juno_port_resume(uint32_t* saveSp, uint32_t to) {
  if (to == 0u || to == juno_carrier_owner) {
    juno_context_switch(saveSp, juno_saved_sp[to]);
    return;
  }
  juno_switch_to = to;
  juno_in_switcher = true;
  juno_context_switch(saveSp, juno_switcher_sp);
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  juno_port_check_stack(from);
  juno_port_resume(&juno_saved_sp[from], to);
}

static void juno_port_exit(uint32_t from, uint32_t to) {
  static uint32_t discarded;
  juno_port_release(from);
  juno_port_resume(&discarded, to);
}

// The part of one slot's frames the collector scans, or false when the slot has none: main's own stack, the
// carrier for the thread on it, and the used part of a chunk for every other thread. `marker` is the
// collector's own frame when this slot is the one running; inside the switcher no slot is.
static bool juno_port_live_range(uint32_t slot, const uint8_t* marker, const uint8_t** from, const uint8_t** to) {
  if (juno_in_switcher) marker = nullptr;
  if (slot == 0u) {
    *from = marker != nullptr ? marker : reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(juno_saved_sp[0]));
    *to = reinterpret_cast<const uint8_t*>(juno_gc_stack_top);
    return true;
  }
  if (juno_chunks[slot] != nullptr) juno_chunk_keep(juno_chunks[slot]);
  if (slot == juno_carrier_owner) {
    *from = marker != nullptr ? marker : reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(juno_saved_sp[slot]));
    *to = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(juno_carrier_top()));
    return true;
  }
  if (juno_chunks[slot] == nullptr) return false;
  *from = juno_chunks[slot];
  *to = juno_chunks[slot] + juno_chunk_used[slot];
  return true;
}

// The preemption tick: SysTick, which the core leaves free (its startup code only binds a weak
// default handler). Juno threads run on the main stack, but the handler reads EXC_RETURN anyway, so
// the interrupted frame is found whichever stack the thread used.
extern "C" uint32_t SystemCoreClock;
extern "C" void juno_preempt_tick(uint32_t* frame);
extern "C" void SysTick_Handler(void);
__asm__(
    ".syntax unified\n"
    ".thumb\n"
    ".section .text.SysTick_Handler,\"ax\",%progbits\n"
    ".global SysTick_Handler\n"
    ".thumb_func\n"
    ".type SysTick_Handler, %function\n"
    "SysTick_Handler:\n"
    "  tst lr, #4\n"
    "  ite eq\n"
    "  mrseq r0, msp\n"
    "  mrsne r0, psp\n"
    "  b juno_preempt_tick\n"
    ".text\n");

// Lowest priority, so the tick never interrupts another handler and the frame it inspects is
// always the thread's own.
static void juno_port_preempt_start() {
  volatile uint32_t* systick = reinterpret_cast<volatile uint32_t*>(0xE000E010u);
  *reinterpret_cast<volatile uint8_t*>(0xE000ED23u) = 0xFFu;
  systick[1] = SystemCoreClock / 1000u * JUNO_TIME_SLICE_MILLIS - 1u;
  systick[2] = 0u;
  systick[0] = 7u;
}
