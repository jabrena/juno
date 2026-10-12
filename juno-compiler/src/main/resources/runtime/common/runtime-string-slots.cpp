
static constexpr uint32_t JUNO_STRING_SLOT_COUNT = ${JUNO_STRING_SLOT_COUNT};
static constexpr uint32_t JUNO_STRING_SLOT_SIZE = ${JUNO_STRING_SLOT_SIZE};
static char juno_string_slots[JUNO_STRING_SLOT_COUNT][JUNO_STRING_SLOT_SIZE];
static uint32_t juno_string_slot_cursor = 0;

// A runtime String never truncates: one that outgrows its slot stops the program, saying why first.
[[noreturn]] static void juno_string_overflow() {
  Serial.println("[juno-string] runtime String longer than ${JUNO_STRING_MAX_LENGTH} characters");
  juno_panic();
}

static const char* juno_string_pointer(int32_t value) {
  if (value == 0) juno_panic();
  return reinterpret_cast<const char*>(static_cast<intptr_t>(value));
}

extern "C" int32_t juno_string_value_of_int(int32_t value) {
  char* buffer = juno_string_slots[juno_string_slot_cursor];
  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
  char reversed[JUNO_STRING_SLOT_SIZE];
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
  buffer[index] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(buffer));
}

