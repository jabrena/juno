extern "C" const uint8_t juno_java_code_start[];
extern "C" const uint8_t juno_java_code_end[];
static volatile uint32_t juno_preempt_due = 0u;
static volatile uint32_t juno_preempt_resume[JUNO_MAX_THREADS];
// How many times a thread was preempted part-way through generated code.
static volatile uint32_t juno_preemptions = 0u;

extern "C" void juno_preempt_trampoline(void);
__asm__(
    ".syntax unified\n"
    ".thumb\n"
    ".section .text.juno_preempt_trampoline,\"ax\",%progbits\n"
    ".global juno_preempt_trampoline\n"
    ".thumb_func\n"
    ".type juno_preempt_trampoline, %function\n"
    "juno_preempt_trampoline:\n"
    "  sub sp, sp, #4\n"
    "  push {r0-r3, r12, lr}\n"
    "  mrs r0, apsr\n"
    "  push {r0, r4}\n"
    "  mov r4, sp\n"
    "  bic r0, r4, #7\n"
    "  mov sp, r0\n"
#if defined(__ARM_FP)
    "  vpush {s0-s15}\n"
    "  vmrs r0, fpscr\n"
    "  push {r0, r1}\n"
#endif
    "  bl juno_thread_preempted\n"
#if defined(__ARM_FP)
    "  pop {r1, r2}\n"
    "  vmsr fpscr, r1\n"
    "  vpop {s0-s15}\n"
#endif
    "  mov sp, r4\n"
    "  str r0, [sp, #32]\n"
    "  pop {r0, r4}\n"
#if defined(__ARM_FEATURE_DSP)
    "  msr APSR_nzcvqg, r0\n"
#else
    "  msr APSR_nzcvq, r0\n"
#endif
    "  pop {r0-r3, r12, lr}\n"
    "  pop {pc}\n"
    ".text\n");

// Interrupt context: preempt the running thread now if it is in generated code, else soon; a
// cooperative subtask is never redirected, it takes the due switch at its next backedge.
extern "C" void juno_preempt_tick(uint32_t* frame) {
  if (juno_live_threads == 0u) return;
  uint32_t pc = frame[6];
  bool inJava = pc >= static_cast<uint32_t>(reinterpret_cast<uintptr_t>(juno_java_code_start))
      && pc < static_cast<uint32_t>(reinterpret_cast<uintptr_t>(juno_java_code_end));
  bool resumable = (frame[7] & 0x0600FC00u) == 0u;
  uint32_t slot = juno_current_slot;
  if (inJava && resumable && !juno_slot_cooperative(slot) && juno_preempt_resume[slot] == 0u) {
    juno_preempt_resume[slot] = pc | 1u;
    frame[6] = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(&juno_preempt_trampoline)) & ~1u;
  } else {
    juno_preempt_due = 1u;
  }
}

// Thread context, from the trampoline: give the other threads a turn, then tell the trampoline
// where the interrupted code resumes.
extern "C" uint32_t juno_thread_preempted() {
  uint32_t slot = juno_current_slot;
  uint32_t resume = juno_preempt_resume[slot];
  juno_preempt_resume[slot] = 0u;
  juno_preempt_due = 0u;
  juno_preemptions++;
  juno_sched_block();
  return resume;
}

// Every loop backedge: keep the core serviced, and take a switch the tick could not make itself
// because it struck outside generated code.
extern "C" void juno_thread_backedge() {
  yield();
  if (juno_preempt_due == 0u || juno_live_threads == 0u) return;
  juno_preempt_due = 0u;
  juno_sched_block();
}
