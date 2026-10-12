// Bytes [start, end) as a runtime String, or null (0) when they do not fit one slot: the caller asked for a bounded
// copy, so unlike a growing String this never stops the program.
extern "C" int32_t juno_string_from_bytes(const uint8_t* bytes, int32_t start, int32_t end) {
  int32_t length = end - start;
  if (bytes == nullptr || start < 0 || length < 0 || static_cast<uint32_t>(length) >= JUNO_STRING_SLOT_SIZE) return 0;
  char* slot = juno_string_slots[juno_string_slot_cursor];
  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  for (int32_t i = 0; i < length; i++) slot[i] = static_cast<char>(bytes[start + i]);
  slot[length] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(slot));
}
