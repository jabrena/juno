// Bytes [start, end) as a runtime String, or null (0) for an invalid range. A copy that fits one slot goes into the
// rotating pool; a longer one gets an arena block of exactly its length, so the text is never cut short.
extern "C" int32_t juno_string_from_bytes(const uint8_t* bytes, int32_t start, int32_t end) {
  int32_t length = end - start;
  if (bytes == nullptr || start < 0 || length < 0) return 0;
  char* text;
  if (static_cast<uint32_t>(length) < JUNO_STRING_SLOT_SIZE) {
    text = juno_string_slots[juno_string_slot_cursor];
    juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  } else {
    text = static_cast<char*>(juno_alloc(static_cast<uint32_t>(length) + 1u, 1u));
  }
  for (int32_t i = 0; i < length; i++) text[i] = static_cast<char>(bytes[start + i]);
  text[length] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(text));
}
