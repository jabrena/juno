// Bare-metal harness: reset vector, FPU enable, SysTick, semihosting output/exit. JUNO_ENTRY is the generated
// program's entry symbol (the function the .ino wrapper calls from setup()).
#include <stdint.h>
#include "Arduino.h"

extern "C" {
extern uint32_t _sbss, _ebss, _estack;
// mps2-an386's CPU clock; the R4 thread port programs SysTick from it.
uint32_t SystemCoreClock = 25000000u;
void JUNO_ENTRY(void);
void __libc_init_array(void);

void _init(void) {}
void _fini(void) {}

// Cortex-M semihosting: the host sees r0 = operation, r1 = argument.
static void semihost(int operation, const void* argument) {
  register int r0 __asm__("r0") = operation;
  register const void* r1 __asm__("r1") = argument;
  __asm__ volatile("bkpt 0xAB" : "+r"(r0) : "r"(r1) : "memory");
}

void juno_harness_write(const char* text) { semihost(0x04, text); }  // SYS_WRITE0

// Clock.millis()/micros() call the core's C symbols directly; Arduino.h only has C++ inlines, so export aliases.
unsigned long juno_harness_millis(void) { return millis(); }
unsigned long juno_harness_micros(void) { return micros(); }
__asm__(".global millis\n.set millis, juno_harness_millis\n.global micros\n.set micros, juno_harness_micros\n");

[[noreturn]] void juno_harness_exit(int code) {
  // SYS_EXIT: ADP_Stopped_ApplicationExit exits QEMU with status 0, anything else with 1.
  semihost(0x18, reinterpret_cast<const void*>(code == 0 ? 0x20026 : 0x20023));
  for (;;) {}
}

// The real core's service hook. Weak: the R4 shim defines its own. Like that one (through Serial's bool
// conversion) it lets virtual time pass.
__attribute__((weak)) void yield(void) {
  juno_harness_clock++;
}

[[noreturn]] static void fault(void) {
  juno_harness_write("[juno-fault]\n");
  juno_harness_exit(2);
}

// The shim of a threaded program defines the real one (the R4 port).
__attribute__((weak)) void SysTick_Handler(void) {}

void Reset_Handler(void) {
  // CP10/CP11 full access: the shim is built hard-float, exactly like the UNO R4 core.
  *reinterpret_cast<volatile uint32_t*>(0xE000ED88) |= 0xFu << 20;
  __asm__ volatile("dsb\n isb");
  for (uint32_t* word = &_sbss; word < &_ebss; ++word) *word = 0;
  __libc_init_array();
  JUNO_ENTRY();
  juno_harness_write("[juno-exit]\n");
  juno_harness_exit(0);
}

__attribute__((section(".isr_vector"), used))
void (*const vector_table[16])(void) = {
    reinterpret_cast<void (*)(void)>(&_estack), Reset_Handler, fault, fault, fault, fault, fault, fault,
    fault, fault, fault, fault, fault, fault, fault, SysTick_Handler};
}
