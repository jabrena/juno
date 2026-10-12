
static constexpr uint32_t JUNO_STRING_BUILDER_HEADER_WORDS = 2; // length, capacity

extern "C" int32_t juno_string_builder_new(int32_t capacity) {
  if (capacity < 0) juno_panic();
  uint32_t bytes = JUNO_STRING_BUILDER_HEADER_WORDS * sizeof(int32_t) + static_cast<uint32_t>(capacity);
  auto* header = reinterpret_cast<int32_t*>(juno_alloc(bytes, alignof(int32_t)));
  header[0] = 0;         // length
  header[1] = capacity;  // capacity
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(header));
}

static uint8_t* juno_string_builder_buffer(int32_t handle) {
  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
  return reinterpret_cast<uint8_t*>(header + JUNO_STRING_BUILDER_HEADER_WORDS);
}

extern "C" int32_t juno_string_builder_append_char(int32_t handle, int32_t value) {
  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
  if (header[0] < header[1]) {
    juno_string_builder_buffer(handle)[header[0]] = static_cast<uint8_t>(value);
    header[0]++;
  }
  return handle;
}

extern "C" int32_t juno_string_builder_append_string(int32_t handle, const char* text) {
  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
  uint8_t* buffer = juno_string_builder_buffer(handle);
  for (const char* c = text; *c != '\0' && header[0] < header[1]; c++) {
    buffer[header[0]] = static_cast<uint8_t>(*c);
    header[0]++;
  }
  return handle;
}

extern "C" int32_t juno_string_builder_to_string(int32_t handle) {
  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
  int32_t length = header[0];
  if (static_cast<uint32_t>(length) >= JUNO_STRING_SLOT_SIZE) juno_string_overflow();
  char* slot = juno_string_slots[juno_string_slot_cursor];
  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  const uint8_t* buffer = juno_string_builder_buffer(handle);
  for (int32_t i = 0; i < length; i++) slot[i] = static_cast<char>(buffer[i]);
  slot[length] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(slot));
}
