// ---- Juno named secrets behind the Pico 2 W's TrustZone secure firmware (juno-third-parties/juno-pico-secure). The
// firmware takes the 12 KB boot slot, fences off the store and starts this sketch non-secure; the sketch reaches the
// slots only through its gateway at 0x10002F00: int juno_secure_call(int op, int a, int b, int c). ----
#include <hardware/boot_lock.h>
#include <hardware/irq.h>
#include <hardware/resets.h>
#include <pico/bootrom.h>
#ifdef __FREERTOS
#include <FreeRTOS.h>
#include <task.h>
#endif

// A call runs with this core's interrupts masked. The secure side keeps one stack per core, and an RTOS preempting a
// task inside the gateway (FreeRTOS SMP on two carriers saves no secure context) would let another task enter on the
// same stack; a store write also takes the flash out of XIP mode, where an interrupt handler run from flash would
// fault. The other core waits on the firmware's own lock instead.
static int32_t juno_pico_tz_call(uint32_t op, uint32_t a, uint32_t b, uint32_t c) {
  typedef int32_t (*juno_pico_tz_gateway)(uint32_t, uint32_t, uint32_t, uint32_t);
  uint32_t interrupts = save_and_disable_interrupts();
  int32_t result = reinterpret_cast<juno_pico_tz_gateway>(0x10002F01u)(op, a, b, c);
  restore_interrupts(interrupts);
  return result;
}

// A store write rewrites flash, which takes the flash out of XIP mode: the other core, once started, is idled for it
// the way arduino-pico's EEPROM library idles it for its own flash writes.
static int32_t juno_pico_tz_flash_call(uint32_t op, uint32_t a, uint32_t b, uint32_t c) {
#ifndef __FREERTOS
  noInterrupts();
#endif
  rp2040.idleOtherCore();
  int32_t result = juno_pico_tz_call(op, a, b, c);
  rp2040.resumeOtherCore();
#ifndef __FREERTOS
  interrupts();
#endif
  return result;
}

extern "C" {
// Under the secure firmware the SDK's own start-up must skip what belongs to the secure world: the boot ROM state
// reset and boot locks, which the firmware has already done or the non-secure side cannot reach, and the resets of
// JTAG, DMA, IO_QSPI and PADS_QSPI, whose reset-done bits read as 0 from the non-secure side, so waiting on them would
// never finish (and a DMA reset would hand every channel back to the secure world). Started without the firmware the
// sketch runs secure, and each override does exactly what the SDK does.
static uint32_t juno_pico_tz_visible_resets(uint32_t mask) {
  return pico_processor_state_is_nonsecure()
      ? mask & ~((1u << RESET_JTAG) | (1u << RESET_DMA) | (1u << RESET_IO_QSPI) | (1u << RESET_PADS_QSPI))
      : mask;
}

static void juno_pico_tz_rom_state_reset(uint32_t flags) {
  rom_bootrom_state_reset_fn reset = (rom_bootrom_state_reset_fn) rom_func_lookup(ROM_FUNC_BOOTROM_STATE_RESET);
  reset(flags);
}

void runtime_init_bootrom_reset(void) {
  if (!pico_processor_state_is_nonsecure()) juno_pico_tz_rom_state_reset(BOOTROM_STATE_RESET_GLOBAL_STATE);
}

void runtime_init_per_core_bootrom_reset(void) {
  if (!pico_processor_state_is_nonsecure()) juno_pico_tz_rom_state_reset(BOOTROM_STATE_RESET_CURRENT_CORE);
}

void runtime_init_boot_locks_reset(void) {
  if (!pico_processor_state_is_nonsecure()) boot_locks_reset();
}

void runtime_init_bootrom_locking_enable(void) {
  if (!pico_processor_state_is_nonsecure()) bootrom_acquire_lock_blocking(BOOTROM_LOCK_ENABLE);
}

void runtime_init_early_resets(void) {
  reset_block_mask(juno_pico_tz_visible_resets(~((1u << RESET_IO_QSPI) | (1u << RESET_PADS_QSPI)
      | (1u << RESET_PLL_USB) | (1u << RESET_USBCTRL) | (1u << RESET_SYSCFG) | (1u << RESET_PLL_SYS))));
  unreset_block_mask_wait_blocking(juno_pico_tz_visible_resets(RESETS_RESET_BITS & ~((1u << RESET_HSTX)
      | (1u << RESET_ADC) | (1u << RESET_SPI0) | (1u << RESET_SPI1) | (1u << RESET_UART0) | (1u << RESET_UART1)
      | (1u << RESET_USBCTRL))));
}

void runtime_init_post_clock_resets(void) {
  unreset_block_mask_wait_blocking(juno_pico_tz_visible_resets(RESETS_RESET_BITS));
}

// GPIO interrupts. The firmware hands every user GPIO to the non-secure world (GPIO_NSMASK), and the RP2350 signals a
// non-secure GPIO's interrupt on IO_IRQ_BANK0_NS, not IO_IRQ_BANK0. The SDK only ever installs and enables its GPIO
// handler on IO_IRQ_BANK0, so without this the CYW43's host-wake interrupt on GPIO 24 never arrives: the radio still
// joins, as the driver polls while it sends commands, but received packets, DHCP's answer among them, are never read.
// Whatever handler the SDK has on IO_IRQ_BANK0 is run from IO_IRQ_BANK0_NS. A GPIO interrupt is only enabled after
// the SDK installs that handler, so there is always one to run.
static void juno_pico_tz_gpio_ns_irq(void) {
  if (irq_has_handler(IO_IRQ_BANK0)) irq_get_vtable_handler(IO_IRQ_BANK0)();
}

__attribute__((constructor)) static void juno_pico_tz_route_gpio_irq(void) {
  if (!pico_processor_state_is_nonsecure()) return;
  irq_set_exclusive_handler(IO_IRQ_BANK0_NS, juno_pico_tz_gpio_ns_irq);
  irq_set_enabled(IO_IRQ_BANK0_NS, true);
}

// The second core. The firmware starts core 1 into a relay that answers the SDK's launch handshake on the non-secure
// FIFO, so multicore_launch_core1, FreeRTOS SMP's included, starts it as usual. What the two cores then signal each
// other with, the SIO doorbells (FreeRTOS's cross-core yield, arduino-pico's idleOtherCore) and the inter-core FIFO,
// interrupts non-secure code on SIO_IRQ_BELL_NS and SIO_IRQ_FIFO_NS, while the SDK only ever enables SIO_IRQ_BELL and
// SIO_IRQ_FIFO. The sketch is linked with --wrap=irq_set_enabled,--wrap=irq_set_priority, so on whichever core the SDK
// enables or prioritises one of those, the non-secure line follows, running the handler the SDK installed.
void __real_irq_set_enabled(uint num, bool enabled);
void __real_irq_set_priority(uint num, uint8_t hardware_priority);

static void juno_pico_tz_bell_ns_irq(void) {
  irq_get_vtable_handler(SIO_IRQ_BELL)();
}

static void juno_pico_tz_fifo_ns_irq(void) {
  irq_get_vtable_handler(SIO_IRQ_FIFO)();
}

static int juno_pico_tz_ns_line(uint num) {
  if (!pico_processor_state_is_nonsecure()) return -1;
  if (num == SIO_IRQ_BELL) return SIO_IRQ_BELL_NS;
  if (num == SIO_IRQ_FIFO) return SIO_IRQ_FIFO_NS;
  return -1;
}

void __wrap_irq_set_enabled(uint num, bool enabled) {
  __real_irq_set_enabled(num, enabled);
  int line = juno_pico_tz_ns_line(num);
  if (line < 0) return;
  if (enabled && !irq_has_handler(static_cast<uint>(line))) {
    irq_set_exclusive_handler(static_cast<uint>(line),
                              line == SIO_IRQ_BELL_NS ? juno_pico_tz_bell_ns_irq : juno_pico_tz_fifo_ns_irq);
  }
  __real_irq_set_enabled(static_cast<uint>(line), enabled);
}

void __wrap_irq_set_priority(uint num, uint8_t hardware_priority) {
  __real_irq_set_priority(num, hardware_priority);
  int line = juno_pico_tz_ns_line(num);
  if (line >= 0) __real_irq_set_priority(static_cast<uint>(line), hardware_priority);
}

#ifdef __FREERTOS
// arduino-pico builds FreeRTOS secure-only (configRUN_FREERTOS_SECURE_ONLY), which seeds every new task's stack with
// an EXC_RETURN back to the secure state; started non-secure, the first switch to a task would fault. The sketch is
// linked with --wrap=pxPortInitialiseStack, and the seed, just above the PSPLIM slot the port returns, becomes a
// return to the non-secure state. Later switches save the EXC_RETURN the hardware gave, so only the seed is wrong.
StackType_t* __real_pxPortInitialiseStack(StackType_t* top, StackType_t* end, TaskFunction_t code, void* parameters);

StackType_t* __wrap_pxPortInitialiseStack(StackType_t* top, StackType_t* end, TaskFunction_t code, void* parameters) {
  StackType_t* stack = __real_pxPortInitialiseStack(top, end, code, parameters);
  if (pico_processor_state_is_nonsecure() && stack[1] == 0xFFFFFFFDu) stack[1] = 0xFFFFFFBCu;
  return stack;
}
#endif

// Only a non-secure sketch has the firmware behind it: flashed without it, the sketch runs secure and the gateway
// address holds arduino-pico's OTA stage, not an entry, so it is never called.
int32_t juno_pico_tz_begin() {
  return pico_processor_state_is_nonsecure() && juno_pico_tz_call(0u, 0u, 0u, 0u) == 0x4A554E4F ? 1 : 0;
}

int32_t juno_pico_tz_read(int32_t slot, int8_t* buffer, int32_t capacity) {
  if (capacity <= 0) return -1;
  return juno_pico_tz_call(1u, static_cast<uint32_t>(slot), reinterpret_cast<uint32_t>(buffer),
                           static_cast<uint32_t>(capacity));
}

int32_t juno_pico_tz_write(int32_t slot, int8_t* value, int32_t length) {
  if (length < 0) return -1;
  return juno_pico_tz_flash_call(2u, static_cast<uint32_t>(slot), reinterpret_cast<uint32_t>(value),
                                 static_cast<uint32_t>(length));
}

int32_t juno_pico_tz_clear_all() {
  return juno_pico_tz_flash_call(4u, 0u, 0u, 0u) == 0 ? 1 : 0;
}

// Flash writes. The sketch is linked with --wrap=flash_range_erase,--wrap=flash_range_program, so the SDK's own calls,
// BTstack's bond storage and the EEPROM library among them, land here. Non-secure, the firmware does the erase or
// program for the flash the sketch owns; the SDK's routines would need the flash controller, the boot RAM and boot-ROM
// calls only the secure world has. Started without the firmware, the SDK's routines run as usual.
void __real_flash_range_erase(uint32_t flash_offs, size_t count);
void __real_flash_range_program(uint32_t flash_offs, const uint8_t* data, size_t count);

void __wrap_flash_range_erase(uint32_t flash_offs, size_t count) {
  if (pico_processor_state_is_nonsecure()) {
    juno_pico_tz_call(5u, flash_offs, static_cast<uint32_t>(count), 0u);
  } else {
    __real_flash_range_erase(flash_offs, count);
  }
}

void __wrap_flash_range_program(uint32_t flash_offs, const uint8_t* data, size_t count) {
  if (pico_processor_state_is_nonsecure()) {
    juno_pico_tz_call(6u, flash_offs, reinterpret_cast<uint32_t>(data), static_cast<uint32_t>(count));
  } else {
    __real_flash_range_program(flash_offs, data, count);
  }
}
}
