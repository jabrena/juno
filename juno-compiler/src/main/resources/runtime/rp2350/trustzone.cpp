// ---- Juno named secrets behind the Pico 2 W's TrustZone secure firmware (juno-third-parties/juno-pico-secure). The
// firmware takes the 12 KB boot slot, fences off the store and starts this sketch non-secure; the sketch reaches the
// slots only through its gateway at 0x10002F00: int juno_secure_call(int op, int a, int b, int c). ----
#include <hardware/boot_lock.h>
#include <hardware/resets.h>
#include <pico/bootrom.h>

static int32_t juno_pico_tz_call(uint32_t op, uint32_t a, uint32_t b, uint32_t c) {
  typedef int32_t (*juno_pico_tz_gateway)(uint32_t, uint32_t, uint32_t, uint32_t);
  return reinterpret_cast<juno_pico_tz_gateway>(0x10002F01u)(op, a, b, c);
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
  return juno_pico_tz_call(2u, static_cast<uint32_t>(slot), reinterpret_cast<uint32_t>(value),
                           static_cast<uint32_t>(length));
}

int32_t juno_pico_tz_clear_all() {
  return juno_pico_tz_call(4u, 0u, 0u, 0u) == 0 ? 1 : 0;
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
