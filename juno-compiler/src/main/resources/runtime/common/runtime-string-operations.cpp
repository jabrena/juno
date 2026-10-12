extern "C" int32_t juno_string_length(int32_t value) {
  return static_cast<int32_t>(strlen(juno_string_pointer(value)));
}

extern "C" int32_t juno_string_char_at(int32_t value, int32_t index) {
  const char* text = juno_string_pointer(value);
  int32_t length = static_cast<int32_t>(strlen(text));
  if (index < 0 || index >= length) juno_panic();
  return static_cast<uint8_t>(text[index]);
}

extern "C" int32_t juno_string_equals(int32_t a, int32_t b) {
  return strcmp(juno_string_pointer(a), juno_string_pointer(b)) == 0 ? 1 : 0;
}

extern "C" int32_t juno_string_concat_new() {
  char* buffer = juno_string_slots[juno_string_slot_cursor];
  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  buffer[0] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
}

static void juno_string_concat_append_byte(int32_t handle, char value) {
  char* buffer = reinterpret_cast<char*>(static_cast<intptr_t>(handle));
  uint32_t length = static_cast<uint32_t>(strlen(buffer));
  if (length + 1u >= JUNO_STRING_SLOT_SIZE) juno_string_overflow();
  buffer[length] = value;
  buffer[length + 1u] = '\0';
}

extern "C" void juno_string_concat_append_string(int32_t handle, const char* value) {
  const char* text = value != nullptr ? value : "null";
  while (*text != '\0') juno_string_concat_append_byte(handle, *text++);
}

extern "C" void juno_string_concat_append_boolean(int32_t handle, int32_t value) {
  juno_string_concat_append_string(handle, value != 0 ? "true" : "false");
}

extern "C" void juno_string_concat_append_char(int32_t handle, int32_t value) {
  juno_string_concat_append_byte(handle, static_cast<char>(value));
}

extern "C" void juno_string_concat_append_int(int32_t handle, int32_t value) {
  char reversed[11];
  uint32_t magnitude = value < 0
      ? 0u - static_cast<uint32_t>(value)
      : static_cast<uint32_t>(value);
  uint32_t count = 0;
  do {
    reversed[count++] = static_cast<char>('0' + magnitude % 10u);
    magnitude /= 10u;
  } while (magnitude != 0u);
  if (value < 0) juno_string_concat_append_byte(handle, '-');
  while (count > 0u) juno_string_concat_append_byte(handle, reversed[--count]);
}

extern "C" void juno_string_concat_append_long(int32_t handle, int64_t value) {
  char reversed[20];
  uint64_t magnitude = value < 0
      ? 0ull - static_cast<uint64_t>(value)
      : static_cast<uint64_t>(value);
  uint32_t count = 0;
  do {
    reversed[count++] = static_cast<char>('0' + magnitude % 10ull);
    magnitude /= 10ull;
  } while (magnitude != 0ull);
  if (value < 0) juno_string_concat_append_byte(handle, '-');
  while (count > 0u) juno_string_concat_append_byte(handle, reversed[--count]);
}

