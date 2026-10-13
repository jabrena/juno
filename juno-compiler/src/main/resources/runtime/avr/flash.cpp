// avr-libc's linker scripts end the flash image with the initial values of .data at __data_load_end. The capacity is
// the board's upload limit, the chip's flash less its bootloader.
extern "C" const uint8_t __data_load_end[];

extern "C" int32_t juno_memory_flash_used() {
  return static_cast<int32_t>(reinterpret_cast<uintptr_t>(__data_load_end));
}

extern "C" int32_t juno_memory_flash_capacity() {
  return ${JUNO_FLASH_CAPACITY};
}

