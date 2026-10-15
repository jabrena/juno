
struct JunoLocalDateTime {
  uint32_t magic;
  int32_t year;
  int32_t month;
  int32_t day;
  int32_t hour;
  int32_t minute;
  int32_t second;
};

static constexpr uint32_t JUNO_LOCAL_DATE_TIME_MAGIC = 0x4c44544du;
static JunoLocalDateTime juno_local_date_time_minimum = {
    JUNO_LOCAL_DATE_TIME_MAGIC, -999999999, 1, 1, 0, 0, 0};

static int32_t juno_local_date_time_handle(const JunoLocalDateTime* value) {
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(value));
}

static JunoLocalDateTime* juno_local_date_time_pointer(int32_t handle) {
  if (handle == 0) juno_panic();
  return reinterpret_cast<JunoLocalDateTime*>(static_cast<uintptr_t>(static_cast<uint32_t>(handle)));
}

static bool juno_local_date_time_is_handle(int32_t handle) {
  if (handle == juno_local_date_time_handle(&juno_local_date_time_minimum)) return true;
  if (handle == 0) return false;
  uintptr_t pointer = static_cast<uintptr_t>(static_cast<uint32_t>(handle));
  uintptr_t base = reinterpret_cast<uintptr_t>(juno_arena);
  if (pointer < base + 4u || pointer > base + juno_arena_used - sizeof(JunoLocalDateTime)) return false;
  uint32_t headerOffset = static_cast<uint32_t>(pointer - base - 4u);
  if (!juno_gc_is_block_start(headerOffset)) return false;
  if (juno_gc_block_size(*juno_gc_header_at(headerOffset)) < sizeof(JunoLocalDateTime)) return false;
  return reinterpret_cast<JunoLocalDateTime*>(pointer)->magic == JUNO_LOCAL_DATE_TIME_MAGIC;
}

static int32_t juno_local_date_time_new(const JunoLocalDateTime& source) {
  auto* value = static_cast<JunoLocalDateTime*>(juno_alloc(sizeof(JunoLocalDateTime), 4u));
  *value = source;
  return juno_local_date_time_handle(value);
}

extern "C" int32_t juno_local_date_time_min() {
  return juno_local_date_time_handle(&juno_local_date_time_minimum);
}

extern "C" int32_t juno_local_date_time_get_year(int32_t handle) {
  return juno_local_date_time_pointer(handle)->year;
}

extern "C" int32_t juno_local_date_time_get_month_value(int32_t handle) {
  return juno_local_date_time_pointer(handle)->month;
}

extern "C" int32_t juno_local_date_time_get_day_of_month(int32_t handle) {
  return juno_local_date_time_pointer(handle)->day;
}

extern "C" int32_t juno_local_date_time_get_hour(int32_t handle) {
  return juno_local_date_time_pointer(handle)->hour;
}

extern "C" int32_t juno_local_date_time_get_minute(int32_t handle) {
  return juno_local_date_time_pointer(handle)->minute;
}

extern "C" int32_t juno_local_date_time_get_second(int32_t handle) {
  return juno_local_date_time_pointer(handle)->second;
}

extern "C" int32_t juno_local_date_time_equals(int32_t leftHandle, int32_t rightHandle) {
  if (leftHandle == rightHandle) return 1;
  if (!juno_local_date_time_is_handle(rightHandle)) return 0;
  JunoLocalDateTime* left = juno_local_date_time_pointer(leftHandle);
  JunoLocalDateTime* right = juno_local_date_time_pointer(rightHandle);
  return left->year == right->year && left->month == right->month && left->day == right->day
      && left->hour == right->hour && left->minute == right->minute && left->second == right->second;
}

static uint32_t juno_local_date_time_digits(char* target, uint32_t value, uint32_t minimumDigits) {
  char reversed[10];
  uint32_t count = 0;
  do {
    reversed[count++] = static_cast<char>('0' + value % 10u);
    value /= 10u;
  } while (value != 0u);
  while (count < minimumDigits) reversed[count++] = '0';
  for (uint32_t index = 0; index < count; index++) target[index] = reversed[count - index - 1u];
  return count;
}

extern "C" int32_t juno_local_date_time_to_string(int32_t handle) {
  JunoLocalDateTime* value = juno_local_date_time_pointer(handle);
  char* text = static_cast<char*>(juno_alloc(32u, 1u));
  uint32_t index = 0;
  if (value->year < 0) {
    text[index++] = '-';
    uint32_t magnitude = 0u - static_cast<uint32_t>(value->year);
    index += juno_local_date_time_digits(text + index, magnitude, magnitude < 10000u ? 4u : 1u);
  } else {
    uint32_t year = static_cast<uint32_t>(value->year);
    if (year > 9999u) text[index++] = '+';
    index += juno_local_date_time_digits(text + index, year, 4u);
  }
  text[index++] = '-';
  index += juno_local_date_time_digits(text + index, static_cast<uint32_t>(value->month), 2u);
  text[index++] = '-';
  index += juno_local_date_time_digits(text + index, static_cast<uint32_t>(value->day), 2u);
  text[index++] = 'T';
  index += juno_local_date_time_digits(text + index, static_cast<uint32_t>(value->hour), 2u);
  text[index++] = ':';
  index += juno_local_date_time_digits(text + index, static_cast<uint32_t>(value->minute), 2u);
  if (value->second != 0) {
    text[index++] = ':';
    index += juno_local_date_time_digits(text + index, static_cast<uint32_t>(value->second), 2u);
  }
  text[index] = 0;
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(text));
}

${JUNO_LOCAL_DATE_TIME_CONFIGURATION}

${JUNO_LOCAL_DATE_TIME_NOW}
