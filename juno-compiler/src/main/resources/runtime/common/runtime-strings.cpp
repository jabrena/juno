
// A runtime String is NUL-terminated text in an arena block of exactly its length, so it lives as long as
// something refers to it, as in Java. Numbers are formatted into a stack buffer of this size first.
static constexpr uint32_t JUNO_NUMBER_TEXT_BYTES = ${JUNO_NUMBER_TEXT_BYTES};

static const char* juno_string_pointer(int32_t value) {
  if (value == 0) juno_panic();
  return reinterpret_cast<const char*>(static_cast<intptr_t>(value));
}

// Copies length bytes of text into a new arena String.
static int32_t juno_string_copy(const char* text, uint32_t length) {
  char* copy = static_cast<char*>(juno_alloc(length + 1u, 1u));
  for (uint32_t i = 0; i < length; i++) copy[i] = text[i];
  copy[length] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(copy));
}

extern "C" int32_t juno_string_value_of_int(int32_t value) {
  char buffer[JUNO_NUMBER_TEXT_BYTES];
  char reversed[JUNO_NUMBER_TEXT_BYTES];
  uint32_t magnitude = value < 0
      ? 0u - static_cast<uint32_t>(value)
      : static_cast<uint32_t>(value);
  uint32_t count = 0;
  do {
    reversed[count++] = static_cast<char>('0' + magnitude % 10u);
    magnitude /= 10u;
  } while (magnitude != 0u);
  uint32_t index = 0;
  if (value < 0) buffer[index++] = '-';
  while (count > 0u) buffer[index++] = reversed[--count];
  return juno_string_copy(buffer, index);
}
