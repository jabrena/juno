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

extern "C" int32_t juno_string_equals_null_safe(int32_t a, int32_t b) {
  if (a == 0 || b == 0) return a == b ? 1 : 0;
  return strcmp(juno_string_pointer(a), juno_string_pointer(b)) == 0 ? 1 : 0;
}

extern "C" int32_t juno_string_hash_code(int32_t value) {
  if (value == 0) return 0;
  const unsigned char* text = reinterpret_cast<const unsigned char*>(juno_string_pointer(value));
  int32_t hash = 0;
  while (*text != '\0') hash = hash * 31 + *text++;
  return hash;
}

// A concatenation is measured before it is built: each measure adds one argument's formatted length to the running
// total, and juno_string_concat_new_sized takes an arena block of exactly that length, which the appends then fill.
extern "C" int32_t juno_string_concat_new_sized(int32_t length) {
  if (length < 0) juno_panic();
  char* buffer = static_cast<char*>(juno_alloc(static_cast<uint32_t>(length) + 1u, 1u));
  buffer[0] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
}

extern "C" int32_t juno_string_concat_measure_string(int32_t length, const char* value) {
  return length + static_cast<int32_t>(strlen(value != nullptr ? value : "null"));
}

extern "C" int32_t juno_string_concat_measure_int(int32_t length, int32_t value) {
  uint32_t magnitude = value < 0 ? 0u - static_cast<uint32_t>(value) : static_cast<uint32_t>(value);
  int32_t digits = value < 0 ? 2 : 1;
  while (magnitude >= 10u) {
    magnitude /= 10u;
    digits++;
  }
  return length + digits;
}

extern "C" int32_t juno_string_concat_measure_long(int32_t length, int64_t value) {
  uint64_t magnitude = value < 0 ? 0ull - static_cast<uint64_t>(value) : static_cast<uint64_t>(value);
  int32_t digits = value < 0 ? 2 : 1;
  while (magnitude >= 10ull) {
    magnitude /= 10ull;
    digits++;
  }
  return length + digits;
}

static void juno_string_concat_append_byte(int32_t handle, char value) {
  char* buffer = reinterpret_cast<char*>(static_cast<intptr_t>(handle));
  uint32_t length = static_cast<uint32_t>(strlen(buffer));
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

