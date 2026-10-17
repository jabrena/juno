
// ---- Juno threads: AVR port (no RTOS: a stack per thread, switched by swapping SP) ----
static constexpr uint32_t JUNO_MAX_THREADS = ${JUNO_MAX_THREADS}u;
static constexpr uint16_t JUNO_THREAD_STACK_BYTES = ${JUNO_THREAD_STACK_BYTES}u;
static constexpr uint32_t JUNO_TIME_SLICE_MILLIS = 1u;
static constexpr uint32_t JUNO_STACK_CANARY = 0x4A554E4Fu;
static constexpr uint16_t JUNO_SWITCH_FRAME_BYTES = 18u;
#if defined(__AVR_3_BYTE_PC__)
static constexpr uint16_t JUNO_RETURN_ADDRESS_BYTES = 3u;
#else
static constexpr uint16_t JUNO_RETURN_ADDRESS_BYTES = 2u;
#endif

static constexpr uint16_t JUNO_STACK_GUARD_BYTES = 128u;

static uint8_t juno_stacks[JUNO_MAX_THREADS - 1u][JUNO_THREAD_STACK_BYTES];
static uint16_t juno_saved_sp[JUNO_MAX_THREADS];

// The lowest SP a generated function may run at on the current task's stack; 0 on the main thread,
// whose stack has nothing of the runtime's below it.
extern "C" {
uint16_t juno_stack_limit = 0u;
}

static void juno_port_set_limit(uint32_t slot) {
  juno_stack_limit = slot == 0u ? 0u
      : static_cast<uint16_t>(reinterpret_cast<uintptr_t>(juno_stacks[slot - 1u] + JUNO_STACK_GUARD_BYTES));
}

// An overflowed stack may already reach into another thread's stack or the runtime's data, and printing
// from it would push further still, so the report runs on a small stack of its own.
static uint8_t juno_overflow_stack[128];

[[noreturn]] __attribute__((noinline)) static void juno_stack_overflow_report() {
  Serial.println("[juno-thread] stack overflow");
  juno_panic();
}

[[noreturn]] static void juno_stack_overflow() {
  juno_stack_limit = 0u;
  uint16_t top = static_cast<uint16_t>(
      reinterpret_cast<uintptr_t>(juno_overflow_stack + sizeof juno_overflow_stack - 1u));
  __asm__ volatile(
      "in __tmp_reg__, __SREG__\n"
      "cli\n"
      "out __SP_H__, %B0\n"
      "out __SREG__, __tmp_reg__\n"
      "out __SP_L__, %A0\n"
      :
      : "r"(top)
      : "memory");
  juno_stack_overflow_report();
}

// Called by a generated function whose frame would cross the limit.
extern "C" [[noreturn]] void juno_thread_stack_overflow() {
  juno_stack_overflow();
}

// Pushes r2-r17 and Y on the current stack, stores SP through r25:r24, then runs on the stack
// r23:r22 holds: a thread's first switch finds a frame of zeros whose return address is its
// trampoline. SPH and SPL change with interrupts held off, as avr-gcc's own prologues do.
extern "C" void juno_context_switch(uint16_t* saveSp, uint16_t newSp);
__asm__(
    ".section .text.juno_context_switch,\"ax\",@progbits\n"
    ".global juno_context_switch\n"
    ".type juno_context_switch, @function\n"
    "juno_context_switch:\n"
    "  push r2\n  push r3\n  push r4\n  push r5\n  push r6\n  push r7\n"
    "  push r8\n  push r9\n  push r10\n  push r11\n  push r12\n  push r13\n"
    "  push r14\n  push r15\n  push r16\n  push r17\n  push r28\n  push r29\n"
    "  movw r30, r24\n"
    "  in r26, 0x3d\n"
    "  in r27, 0x3e\n"
    "  st Z, r26\n"
    "  std Z+1, r27\n"
    "  in r0, 0x3f\n"
    "  cli\n"
    "  out 0x3e, r23\n"
    "  out 0x3f, r0\n"
    "  out 0x3d, r22\n"
    "  pop r29\n  pop r28\n  pop r17\n  pop r16\n  pop r15\n  pop r14\n"
    "  pop r13\n  pop r12\n  pop r11\n  pop r10\n  pop r9\n  pop r8\n"
    "  pop r7\n  pop r6\n  pop r5\n  pop r4\n  pop r3\n  pop r2\n"
    "  ret\n"
    ".text\n");

static void juno_thread_body();

extern "C" void juno_thread_trampoline() {
  juno_thread_body();
  for (;;) {}
}

static void juno_port_prepare(uint32_t slot) {
  uint8_t* stack = juno_stacks[slot - 1u];
  memcpy(stack, &JUNO_STACK_CANARY, sizeof JUNO_STACK_CANARY);
  // The CPU pushes a return address low byte first, so it reads most significant byte first
  // upwards from SP; the stack's last byte stays free, as SP names the next free byte.
  uint16_t address = static_cast<uint16_t>(reinterpret_cast<uintptr_t>(&juno_thread_trampoline));
  uint8_t* frame = stack + JUNO_THREAD_STACK_BYTES - 1u - JUNO_RETURN_ADDRESS_BYTES
      - JUNO_SWITCH_FRAME_BYTES;
  memset(frame, 0, JUNO_SWITCH_FRAME_BYTES + JUNO_RETURN_ADDRESS_BYTES + 1u);
  frame[JUNO_SWITCH_FRAME_BYTES + JUNO_RETURN_ADDRESS_BYTES - 1u] = static_cast<uint8_t>(address);
  frame[JUNO_SWITCH_FRAME_BYTES + JUNO_RETURN_ADDRESS_BYTES - 2u] = static_cast<uint8_t>(address >> 8);
  juno_saved_sp[slot] = static_cast<uint16_t>(reinterpret_cast<uintptr_t>(frame) - 1u);
}

// A cancelled task will never be scheduled again; its private stack can be reused.
static void juno_port_cancel(uint32_t slot) {
  static_cast<void>(slot);
}

// A large C frame can allocate straight past the canary without writing it, so the stack pointer itself
// is checked too. Called from deep inside the runtime (every switch and every guarded I/O call).
static void juno_port_check_stack(uint32_t slot) {
  if (slot == 0u) return;
  const uint8_t* bottom = juno_stacks[slot - 1u];
  bool below = SP < reinterpret_cast<uintptr_t>(bottom) + sizeof JUNO_STACK_CANARY;
  if (below || memcmp(bottom, &JUNO_STACK_CANARY, sizeof JUNO_STACK_CANARY) != 0) juno_stack_overflow();
}

static void juno_port_switch(uint32_t from, uint32_t to) {
  juno_port_check_stack(from);
  juno_port_set_limit(to);
  juno_port_malloc_switch(from, to, SP - JUNO_SWITCH_FRAME_BYTES - JUNO_RETURN_ADDRESS_BYTES);
  juno_context_switch(&juno_saved_sp[from], juno_saved_sp[to]);
}

static void juno_port_exit(uint32_t from, uint32_t to) {
  juno_port_set_limit(to);
  juno_port_malloc_switch(from, to, SP - JUNO_SWITCH_FRAME_BYTES - JUNO_RETURN_ADDRESS_BYTES);
  juno_context_switch(&juno_saved_sp[from], juno_saved_sp[to]);
}

static uintptr_t juno_port_stack_top(uint32_t slot) {
  return reinterpret_cast<uintptr_t>(&juno_stacks[slot - 1u][JUNO_THREAD_STACK_BYTES]);
}

static uintptr_t juno_port_saved_sp(uint32_t slot) {
  return juno_saved_sp[slot];
}
