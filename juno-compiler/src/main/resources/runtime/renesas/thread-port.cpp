
// ---- Juno threads: UNO R4 port (no RTOS: a stack per thread, switched by swapping sp) ----
static constexpr uint32_t JUNO_MAX_THREADS = ${JUNO_MAX_THREADS}u;
static constexpr uint32_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;
static constexpr uint32_t JUNO_STACK_CANARY = 0x4A554E4Fu;
#if defined(__ARM_FP)
static constexpr uint32_t JUNO_SWITCH_FRAME_WORDS = 16u + 9u;
#else
static constexpr uint32_t JUNO_SWITCH_FRAME_WORDS = 9u;
#endif

alignas(8) static uint8_t juno_stacks[JUNO_MAX_THREADS - 1u][JUNO_THREAD_STACK_BYTES];
static uint32_t juno_saved_sp[JUNO_MAX_THREADS];

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

static void juno_port_prepare(uint32_t slot) {
  uint32_t* canary = reinterpret_cast<uint32_t*>(&juno_stacks[slot - 1u][0]);
  canary[0] = JUNO_STACK_CANARY;
  canary[1] = JUNO_STACK_CANARY;
  uint32_t* top = reinterpret_cast<uint32_t*>(&juno_stacks[slot - 1u][JUNO_THREAD_STACK_BYTES]);
  uint32_t* frame = top - 2u - JUNO_SWITCH_FRAME_WORDS;
  for (uint32_t index = 0; index < JUNO_SWITCH_FRAME_WORDS; index++) frame[index] = 0u;
  frame[JUNO_SWITCH_FRAME_WORDS - 1u] = static_cast<uint32_t>(
      reinterpret_cast<uintptr_t>(&juno_thread_trampoline));
  juno_saved_sp[slot] = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(frame));
}

// A cancelled task will never be scheduled again; its private stack can be reused.
static void juno_port_cancel(uint32_t slot) {
  static_cast<void>(slot);
}

// An overflowed stack may already reach into another thread's stack or the runtime's data, and printing
// (USB serial is deep) from it would push further still, so the report runs on a stack of its own.
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
  const uint32_t* canary = reinterpret_cast<const uint32_t*>(&juno_stacks[slot - 1u][0]);
  uint8_t marker;
  bool below = reinterpret_cast<uintptr_t>(&marker) < reinterpret_cast<uintptr_t>(canary + 2);
  if (below || canary[0] != JUNO_STACK_CANARY || canary[1] != JUNO_STACK_CANARY) juno_stack_overflow();
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  juno_port_check_stack(from);
  juno_context_switch(&juno_saved_sp[from], juno_saved_sp[to]);
}

static void juno_port_exit(uint32_t from, uint32_t to) {
  juno_context_switch(&juno_saved_sp[from], juno_saved_sp[to]);
}

static uintptr_t juno_port_stack_top(uint32_t slot) {
  return reinterpret_cast<uintptr_t>(&juno_stacks[slot - 1u][JUNO_THREAD_STACK_BYTES]);
}

static uintptr_t juno_port_saved_sp(uint32_t slot) {
  return juno_saved_sp[slot];
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
