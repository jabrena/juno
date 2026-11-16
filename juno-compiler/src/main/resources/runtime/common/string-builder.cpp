
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

// A literal or a runtime String; a null one appends "null", as on the JVM.
extern "C" int32_t juno_string_builder_append_string(int32_t handle, const char* text) {
  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
  uint8_t* buffer = juno_string_builder_buffer(handle);
  for (const char* c = text != nullptr ? text : "null"; *c != '\0' && header[0] < header[1]; c++) {
    buffer[header[0]] = static_cast<uint8_t>(*c);
    header[0]++;
  }
  return handle;
}

// The result lives on the collected heap, not in the runtime-string slot ring: a program keeps a toString() result
// (a secret, a JSON field, a name read from SD) across a loop, and later concatenations would wrap the ring onto it.
// Sized to the content, it is as long as the builder's own capacity allows rather than one slot.
extern "C" int32_t juno_string_builder_to_string(int32_t handle) {
  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(handle));
  int32_t length = header[0];
  char* text = static_cast<char*>(juno_alloc(static_cast<uint32_t>(length) + 1u, 1u));
  const uint8_t* buffer = juno_string_builder_buffer(handle);
  for (int32_t i = 0; i < length; i++) text[i] = static_cast<char>(buffer[i]);
  text[length] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(text));
}
