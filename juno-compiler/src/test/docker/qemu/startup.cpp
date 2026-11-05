// Bare-metal harness: reset vector, FPU enable, SysTick, semihosting output/exit. JUNO_ENTRY is the generated
// program's entry symbol (the function the .ino wrapper calls from setup()). With JUNO_HARNESS_PSP (run.sh sets it
// for the UNO Q's Zephyr shim) the program runs on the process stack, as every Zephyr thread does, and handlers on
// a main stack of their own.
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
// conversion) it lets virtual time pass. On the UNO Q, whose Zephyr core makes yield() a k_yield(), it also lets the
// stand-in kernel's other threads run (the I/O worker, which real Zephyr would run by priority).
__attribute__((weak)) void yield(void) {
  juno_harness_clock++;
  if (juno_harness_delay_hook != nullptr) juno_harness_delay_hook();
}

[[noreturn]] static void fault(void) {
  juno_harness_write("[juno-fault]\n");
  juno_harness_exit(2);
}

// The shim of a threaded program defines the real one (the R4 port directly, the Q port through the kernel stand-in).
__attribute__((weak)) void SysTick_Handler(void) {}

#if defined(JUNO_HARNESS_PSP)
alignas(8) static uint8_t juno_handler_stack[2048];
#endif

void Reset_Handler(void) {
  // CP10/CP11 full access: the shim is built hard-float, exactly like the UNO R4 core.
  *reinterpret_cast<volatile uint32_t*>(0xE000ED88) |= 0xFu << 20;
  __asm__ volatile("dsb\n isb");
  for (uint32_t* word = &_sbss; word < &_ebss; ++word) *word = 0;
  __libc_init_array();
#if defined(JUNO_HARNESS_PSP)
  // Thread mode moves to the process stack in place (same sp), then handlers get a main stack of their own.
  __asm__ volatile(
      "mrs r0, msp\n"
      "msr psp, r0\n"
      "movs r0, #2\n"
      "msr control, r0\n"
      "isb\n"
      "msr msp, %0\n"
      "isb\n"
      :
      : "r"(juno_handler_stack + sizeof juno_handler_stack)
      : "r0", "memory");
#endif
  JUNO_ENTRY();
  juno_harness_write("[juno-exit]\n");
  juno_harness_exit(0);
}

__attribute__((section(".isr_vector"), used))
void (*const vector_table[16])(void) = {
    reinterpret_cast<void (*)(void)>(&_estack), Reset_Handler, fault, fault, fault, fault, fault, fault,
    fault, fault, fault, fault, fault, fault, fault, SysTick_Handler};
}
