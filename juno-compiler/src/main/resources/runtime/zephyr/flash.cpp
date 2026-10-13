// The sketch is an llext the loader reads from its own flash partition, so the linker has no symbols for it.
// zephyr-sketch-tool stores the image header in the ELF identification padding of that partition: a version
// byte, the image length (offset 8) and the 0x2341 magic (offset 12).
static constexpr uintptr_t JUNO_SKETCH_PARTITION = 0x08100000u;
static constexpr int32_t JUNO_SKETCH_PARTITION_BYTES = 786432;

extern "C" int32_t juno_memory_flash_used() {
  const volatile uint8_t* header = reinterpret_cast<const volatile uint8_t*>(JUNO_SKETCH_PARTITION);
  if (header[12] != 0x41 || header[13] != 0x23) return -1;
  return static_cast<int32_t>(header[8] | (header[9] << 8) | (header[10] << 16)
      | (static_cast<uint32_t>(header[11]) << 24));
}

extern "C" int32_t juno_memory_flash_capacity() {
  return JUNO_SKETCH_PARTITION_BYTES;
}

