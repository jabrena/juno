
struct JunoUuid {
  uint32_t magic;
  uint32_t mostLow;
  uint32_t mostHigh;
  uint32_t leastLow;
  uint32_t leastHigh;
};

static constexpr uint32_t JUNO_UUID_MAGIC = 0x55554944u;

static int32_t juno_uuid_handle(const JunoUuid* value) {
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(value));
}

static JunoUuid* juno_uuid_pointer(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<JunoUuid*>(static_cast<uintptr_t>(static_cast<uint32_t>(handle)));
}

static bool juno_uuid_is_handle(int32_t handle) {
  if (handle == 0) return false;
  uintptr_t pointer = static_cast<uintptr_t>(static_cast<uint32_t>(handle));
  uintptr_t base = reinterpret_cast<uintptr_t>(juno_arena);
  if (pointer < base + 4u || pointer > base + juno_arena_used - sizeof(JunoUuid)) return false;
  uint32_t headerOffset = static_cast<uint32_t>(pointer - base - 4u);
  if (!juno_gc_is_block_start(headerOffset)) return false;
  if (juno_gc_block_size(*juno_gc_header_at(headerOffset)) < sizeof(JunoUuid)) return false;
  return reinterpret_cast<JunoUuid*>(pointer)->magic == JUNO_UUID_MAGIC;
}

static int64_t juno_uuid_long(uint32_t low, uint32_t high) {
  return static_cast<int64_t>((static_cast<uint64_t>(high) << 32) | static_cast<uint64_t>(low));
}

extern "C" int32_t juno_uuid_new(int64_t most, int64_t least) {
  auto* value = static_cast<JunoUuid*>(juno_alloc(sizeof(JunoUuid), 4u));
  value->magic = JUNO_UUID_MAGIC;
  value->mostLow = static_cast<uint32_t>(most);
  value->mostHigh = static_cast<uint32_t>(static_cast<uint64_t>(most) >> 32);
  value->leastLow = static_cast<uint32_t>(least);
  value->leastHigh = static_cast<uint32_t>(static_cast<uint64_t>(least) >> 32);
  return juno_uuid_handle(value);
}

extern "C" int64_t juno_uuid_get_most_significant_bits(int32_t handle) {
  JunoUuid* value = juno_uuid_pointer(handle);
  return juno_uuid_long(value->mostLow, value->mostHigh);
}

extern "C" int64_t juno_uuid_get_least_significant_bits(int32_t handle) {
  JunoUuid* value = juno_uuid_pointer(handle);
  return juno_uuid_long(value->leastLow, value->leastHigh);
}

extern "C" int32_t juno_uuid_version(int32_t handle) {
  return static_cast<int32_t>((juno_uuid_pointer(handle)->mostLow >> 12) & 15u);
}

extern "C" int32_t juno_uuid_variant(int32_t handle) {
  uint32_t high = juno_uuid_pointer(handle)->leastHigh;
  if ((high & 0x80000000u) == 0u) return 0;
  if ((high & 0x40000000u) == 0u) return 2;
  return (high & 0x20000000u) == 0u ? 6 : 7;
}

extern "C" int32_t juno_uuid_equals(int32_t leftHandle, int32_t rightHandle) {
  if (leftHandle == rightHandle) return 1;
  if (!juno_uuid_is_handle(rightHandle)) return 0;
  JunoUuid* left = juno_uuid_pointer(leftHandle);
  JunoUuid* right = juno_uuid_pointer(rightHandle);
  return left->mostLow == right->mostLow && left->mostHigh == right->mostHigh
      && left->leastLow == right->leastLow && left->leastHigh == right->leastHigh;
}

extern "C" int32_t juno_uuid_compare_to(int32_t leftHandle, int32_t rightHandle) {
  JunoUuid* left = juno_uuid_pointer(leftHandle);
  JunoUuid* right = juno_uuid_pointer(rightHandle);
  int64_t leftMost = juno_uuid_long(left->mostLow, left->mostHigh);
  int64_t rightMost = juno_uuid_long(right->mostLow, right->mostHigh);
  if (leftMost < rightMost) return -1;
  if (leftMost > rightMost) return 1;
  int64_t leftLeast = juno_uuid_long(left->leastLow, left->leastHigh);
  int64_t rightLeast = juno_uuid_long(right->leastLow, right->leastHigh);
  return leftLeast < rightLeast ? -1 : leftLeast > rightLeast ? 1 : 0;
}

extern "C" int32_t juno_uuid_hash_code(int32_t handle) {
  JunoUuid* value = juno_uuid_pointer(handle);
  uint32_t high = value->mostHigh ^ value->leastHigh;
  uint32_t low = value->mostLow ^ value->leastLow;
  return static_cast<int32_t>(high ^ low);
}

static char juno_uuid_hex(uint32_t nibble) {
  return static_cast<char>(nibble < 10u ? '0' + nibble : 'a' + nibble - 10u);
}

static void juno_uuid_write_hex(char* target, uint64_t value, uint32_t digits) {
  for (uint32_t index = 0; index < digits; index++) {
    uint32_t shift = (digits - index - 1u) * 4u;
    target[index] = juno_uuid_hex(static_cast<uint32_t>(value >> shift) & 15u);
  }
}

extern "C" int32_t juno_uuid_to_string(int32_t handle) {
  JunoUuid* value = juno_uuid_pointer(handle);
  uint64_t most = static_cast<uint64_t>(juno_uuid_long(value->mostLow, value->mostHigh));
  uint64_t least = static_cast<uint64_t>(juno_uuid_long(value->leastLow, value->leastHigh));
  char* text = static_cast<char*>(juno_alloc(37u, 1u));
  juno_uuid_write_hex(text, most >> 32, 8u);
  text[8] = '-';
  juno_uuid_write_hex(text + 9, most >> 16, 4u);
  text[13] = '-';
  juno_uuid_write_hex(text + 14, most, 4u);
  text[18] = '-';
  juno_uuid_write_hex(text + 19, least >> 48, 4u);
  text[23] = '-';
  juno_uuid_write_hex(text + 24, least, 12u);
  text[36] = 0;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(text));
}

static int32_t juno_uuid_digit(char value) {
  if (value >= '0' && value <= '9') return value - '0';
  if (value >= 'a' && value <= 'f') return value - 'a' + 10;
  if (value >= 'A' && value <= 'F') return value - 'A' + 10;
  return -1;
}

static int32_t juno_uuid_invalid() {
  auto* exception = static_cast<int32_t*>(juno_alloc(8u, 4u));
  exception[0] = ${JUNO_UUID_ILLEGAL_ARGUMENT_CLASS_ID};
  exception[1] = static_cast<int32_t>(reinterpret_cast<intptr_t>("Invalid UUID string"));
  juno_throw_raise(static_cast<int32_t>(reinterpret_cast<intptr_t>(exception)));
  return 0;
}

extern "C" int32_t juno_uuid_from_string(const char* text) {
  if (text == nullptr) return juno_uuid_invalid();
  uint64_t most = 0;
  uint64_t least = 0;
  uint32_t digits = 0;
  for (uint32_t index = 0; index < 36u; index++) {
    bool hyphen = index == 8u || index == 13u || index == 18u || index == 23u;
    if (hyphen) {
      if (text[index] != '-') return juno_uuid_invalid();
      continue;
    }
    int32_t digit = juno_uuid_digit(text[index]);
    if (digit < 0) return juno_uuid_invalid();
    if (digits < 16u) {
      most = (most << 4) | static_cast<uint32_t>(digit);
    } else {
      least = (least << 4) | static_cast<uint32_t>(digit);
    }
    digits++;
  }
  if (text[36] != 0) return juno_uuid_invalid();
  return juno_uuid_new(static_cast<int64_t>(most), static_cast<int64_t>(least));
}
