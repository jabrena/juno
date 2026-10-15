
static constexpr uint32_t JUNO_LOCAL_DATE_TIME_RETRY_MILLIS = 60000u;
static constexpr uint32_t JUNO_LOCAL_DATE_TIME_REFRESH_MILLIS = 3600000u;
static bool juno_local_date_time_synchronized = false;
static bool juno_local_date_time_attempted = false;
static uint32_t juno_local_date_time_base_millis = 0;
static uint32_t juno_local_date_time_last_attempt_millis = 0;
static JunoLocalDateTime juno_local_date_time_base = {
    JUNO_LOCAL_DATE_TIME_MAGIC, 1970, 1, 1, 0, 0, 0};

static bool juno_local_date_time_leap_year(int32_t year) {
  return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
}

static int32_t juno_local_date_time_days_in_month(int32_t year, int32_t month) {
  static const uint8_t lengths[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
  return month == 2 && juno_local_date_time_leap_year(year) ? 29 : lengths[month - 1];
}

static JunoLocalDateTime juno_local_date_time_advance(JunoLocalDateTime value, uint32_t seconds) {
  seconds += static_cast<uint32_t>(value.second);
  value.second = static_cast<int32_t>(seconds % 60u);
  seconds /= 60u;
  seconds += static_cast<uint32_t>(value.minute);
  value.minute = static_cast<int32_t>(seconds % 60u);
  seconds /= 60u;
  seconds += static_cast<uint32_t>(value.hour);
  value.hour = static_cast<int32_t>(seconds % 24u);
  uint32_t days = seconds / 24u;
  while (days-- > 0u) {
    value.day++;
    if (value.day > juno_local_date_time_days_in_month(value.year, value.month)) {
      value.day = 1;
      value.month++;
      if (value.month > 12) {
        value.month = 1;
        value.year++;
      }
    }
  }
  return value;
}

static int32_t juno_local_date_time_skip_space(
    const uint8_t* body, int32_t cursor, int32_t end) {
  while (cursor < end && (body[cursor] == ' ' || body[cursor] == '\t'
      || body[cursor] == '\r' || body[cursor] == '\n')) cursor++;
  return cursor;
}

static int32_t juno_local_date_time_object_end(
    const uint8_t* body, int32_t start, int32_t end) {
  int32_t depth = 0;
  bool inString = false;
  bool escaped = false;
  for (int32_t cursor = start; cursor < end; cursor++) {
    uint8_t value = body[cursor];
    if (inString) {
      if (escaped) escaped = false;
      else if (value == '\\') escaped = true;
      else if (value == '"') inString = false;
    } else if (value == '"') {
      inString = true;
    } else if (value == '{') {
      depth++;
    } else if (value == '}' && --depth == 0) {
      return cursor;
    }
  }
  return -1;
}

static int32_t juno_local_date_time_member(
    const uint8_t* body, int32_t start, int32_t end,
    const char* name, int32_t nameLength) {
  for (int32_t index = start; index + nameLength + 2 < end; index++) {
    if (body[index] != '"' || body[index + nameLength + 1] != '"'
        || memcmp(body + index + 1, name, static_cast<size_t>(nameLength)) != 0) continue;
    int32_t cursor = juno_local_date_time_skip_space(body, index + nameLength + 2, end);
    if (cursor >= end || body[cursor] != ':') continue;
    return juno_local_date_time_skip_space(body, cursor + 1, end);
  }
  return -1;
}

static bool juno_local_date_time_json_value(const uint8_t* body, int32_t length,
                                             const char* path, int32_t* start, int32_t* end) {
  int32_t rangeStart = 0;
  int32_t rangeEnd = length;
  const char* segment = path;
  while (*segment != 0) {
    const char* dot = strchr(segment, '.');
    int32_t segmentLength = static_cast<int32_t>(dot == nullptr ? strlen(segment) : dot - segment);
    int32_t valueStart = juno_local_date_time_member(
        body, rangeStart, rangeEnd, segment, segmentLength);
    if (valueStart < 0) return false;
    if (dot == nullptr) {
      if (valueStart >= rangeEnd || body[valueStart] != '"') return false;
      int32_t cursor = valueStart + 1;
      while (cursor < rangeEnd && body[cursor] != '"') {
        if (body[cursor] == '\\') return false;
        cursor++;
      }
      if (cursor >= rangeEnd) return false;
      *start = valueStart + 1;
      *end = cursor;
      return true;
    }
    if (valueStart >= rangeEnd || body[valueStart] != '{') return false;
    int32_t objectEnd = juno_local_date_time_object_end(body, valueStart, rangeEnd);
    if (objectEnd < 0) return false;
    rangeStart = valueStart + 1;
    rangeEnd = objectEnd;
    segment = dot + 1;
  }
  return false;
}

static int32_t juno_local_date_time_two_digits(const uint8_t* text, int32_t index) {
  if (text[index] < '0' || text[index] > '9'
      || text[index + 1] < '0' || text[index + 1] > '9') return -1;
  return (text[index] - '0') * 10 + text[index + 1] - '0';
}

static bool juno_local_date_time_parse_iso(const uint8_t* text, int32_t length,
                                            JunoLocalDateTime* target) {
  if (length < 19 || text[4] != '-' || text[7] != '-'
      || (text[10] != 'T' && text[10] != ' ') || text[13] != ':' || text[16] != ':') return false;
  int32_t century = juno_local_date_time_two_digits(text, 0);
  int32_t year = juno_local_date_time_two_digits(text, 2);
  if (century < 0 || year < 0) return false;
  *target = {JUNO_LOCAL_DATE_TIME_MAGIC, century * 100 + year,
      juno_local_date_time_two_digits(text, 5), juno_local_date_time_two_digits(text, 8),
      juno_local_date_time_two_digits(text, 11), juno_local_date_time_two_digits(text, 14),
      juno_local_date_time_two_digits(text, 17)};
  return true;
}

static bool juno_local_date_time_fetch(JunoLocalDateTime* target) {
  if (juno_wifi_status() != 3) return false;
  uint8_t response[256];
  uint8_t headers[64];
  int32_t statusAndHeadersLength[2] = {0, 0};
  int32_t length = juno_https_get(${JUNO_LOCAL_DATE_TIME_HOST}, ${JUNO_LOCAL_DATE_TIME_PORT},
      ${JUNO_LOCAL_DATE_TIME_PATH}, response, sizeof(response),
      headers, sizeof(headers), statusAndHeadersLength);
  if (length <= 0 || statusAndHeadersLength[0] != 200) return false;
  int32_t valueStart = 0;
  int32_t valueEnd = 0;
  if (!juno_local_date_time_json_value(response, length, ${JUNO_LOCAL_DATE_TIME_JSON_PATH},
          &valueStart, &valueEnd)) return false;
  JunoLocalDateTime value;
  if (!juno_local_date_time_parse_iso(response + valueStart, valueEnd - valueStart, &value)) return false;
  if (value.month < 1 || value.month > 12 || value.day < 1
      || value.day > juno_local_date_time_days_in_month(value.year, value.month)
      || value.hour < 0 || value.hour > 23 || value.minute < 0 || value.minute > 59
      || value.second < 0 || value.second > 59) return false;
  *target = value;
  return true;
}

extern "C" int32_t juno_local_date_time_now() {
  if (juno_wifi_status() != 3) return juno_local_date_time_min();
  uint32_t currentMillis = static_cast<uint32_t>(millis());
  if (!juno_local_date_time_synchronized) {
    if (!juno_local_date_time_attempted
        || currentMillis - juno_local_date_time_last_attempt_millis >= JUNO_LOCAL_DATE_TIME_RETRY_MILLIS) {
      juno_local_date_time_attempted = true;
      juno_local_date_time_last_attempt_millis = currentMillis;
      JunoLocalDateTime fetched;
      if (juno_local_date_time_fetch(&fetched)) {
        juno_local_date_time_base = fetched;
        juno_local_date_time_base_millis = static_cast<uint32_t>(millis());
        juno_local_date_time_synchronized = true;
      }
    }
    if (!juno_local_date_time_synchronized) return juno_local_date_time_min();
    currentMillis = static_cast<uint32_t>(millis());
  }

  uint32_t elapsedMillis = currentMillis - juno_local_date_time_base_millis;
  JunoLocalDateTime current = juno_local_date_time_advance(
      juno_local_date_time_base, elapsedMillis / 1000u);
  if (elapsedMillis >= JUNO_LOCAL_DATE_TIME_REFRESH_MILLIS) {
    JunoLocalDateTime fetched;
    if (juno_local_date_time_fetch(&fetched)) current = fetched;
    juno_local_date_time_base = current;
    juno_local_date_time_base_millis = static_cast<uint32_t>(millis());
  }
  return juno_local_date_time_new(current);
}
