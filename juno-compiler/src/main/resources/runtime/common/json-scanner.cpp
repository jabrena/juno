
static constexpr int32_t JUNO_JSON_MISSING = 0;
static constexpr int32_t JUNO_JSON_NULL = 1;
static constexpr int32_t JUNO_JSON_BOOLEAN = 2;
static constexpr int32_t JUNO_JSON_NUMBER = 3;
static constexpr int32_t JUNO_JSON_STRING = 4;
static constexpr int32_t JUNO_JSON_OBJECT = 5;
static constexpr int32_t JUNO_JSON_ARRAY = 6;
static constexpr int32_t JUNO_JSON_INVALID = 7;
static constexpr int32_t JUNO_JSON_MAX_DEPTH = 32;

static bool juno_json_is_space(char c) {
  return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

static void juno_json_skip_space(const char** cursor, const char* end) {
  while (*cursor < end && juno_json_is_space(**cursor)) (*cursor)++;
}

static bool juno_json_is_hex(char c) {
  return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
}

static bool juno_json_parse_string(const char* start, const char* end, const char** after) {
  if (start >= end || *start != '"') return false;
  const char* p = start + 1;
  while (p < end) {
    unsigned char c = static_cast<unsigned char>(*p++);
    if (c == '"') { *after = p; return true; }
    if (c < 0x20) return false;
    if (c != '\\') continue;
    if (p >= end) return false;
    char escape = *p++;
    if (escape == '"' || escape == '\\' || escape == '/' || escape == 'b'
        || escape == 'f' || escape == 'n' || escape == 'r' || escape == 't') continue;
    if (escape != 'u' || end - p < 4) return false;
    for (int32_t i = 0; i < 4; i++) if (!juno_json_is_hex(p[i])) return false;
    p += 4;
  }
  return false;
}

static bool juno_json_parse_number(const char* start, const char* end, const char** after) {
  const char* p = start;
  if (p < end && *p == '-') p++;
  if (p >= end) return false;
  if (*p == '0') {
    p++;
    if (p < end && *p >= '0' && *p <= '9') return false;
  } else {
    if (*p < '1' || *p > '9') return false;
    do { p++; } while (p < end && *p >= '0' && *p <= '9');
  }
  if (p < end && *p == '.') {
    p++;
    if (p >= end || *p < '0' || *p > '9') return false;
    do { p++; } while (p < end && *p >= '0' && *p <= '9');
  }
  if (p < end && (*p == 'e' || *p == 'E')) {
    p++;
    if (p < end && (*p == '+' || *p == '-')) p++;
    if (p >= end || *p < '0' || *p > '9') return false;
    do { p++; } while (p < end && *p >= '0' && *p <= '9');
  }
  *after = p;
  return true;
}

static bool juno_json_parse_value(const char* start, const char* end,
                                   const char** after, int32_t depth);

static bool juno_json_parse_object(const char* start, const char* end,
                                    const char** after, int32_t depth) {
  const char* p = start + 1;
  juno_json_skip_space(&p, end);
  if (p < end && *p == '}') { *after = p + 1; return true; }
  while (p < end) {
    const char* keyEnd;
    if (!juno_json_parse_string(p, end, &keyEnd)) return false;
    p = keyEnd;
    juno_json_skip_space(&p, end);
    if (p >= end || *p++ != ':') return false;
    juno_json_skip_space(&p, end);
    if (!juno_json_parse_value(p, end, &p, depth + 1)) return false;
    juno_json_skip_space(&p, end);
    if (p < end && *p == '}') { *after = p + 1; return true; }
    if (p >= end || *p++ != ',') return false;
    juno_json_skip_space(&p, end);
    if (p < end && *p == '}') return false;
  }
  return false;
}

static bool juno_json_parse_array(const char* start, const char* end,
                                   const char** after, int32_t depth) {
  const char* p = start + 1;
  juno_json_skip_space(&p, end);
  if (p < end && *p == ']') { *after = p + 1; return true; }
  while (p < end) {
    if (!juno_json_parse_value(p, end, &p, depth + 1)) return false;
    juno_json_skip_space(&p, end);
    if (p < end && *p == ']') { *after = p + 1; return true; }
    if (p >= end || *p++ != ',') return false;
    juno_json_skip_space(&p, end);
    if (p < end && *p == ']') return false;
  }
  return false;
}

static bool juno_json_parse_value(const char* start, const char* end,
                                   const char** after, int32_t depth) {
  if (depth > JUNO_JSON_MAX_DEPTH) return false;
  const char* p = start;
  juno_json_skip_space(&p, end);
  if (p >= end) return false;
  if (*p == '"') return juno_json_parse_string(p, end, after);
  if (*p == '{') return juno_json_parse_object(p, end, after, depth);
  if (*p == '[') return juno_json_parse_array(p, end, after, depth);
  if (end - p >= 4 && strncmp(p, "true", 4) == 0) { *after = p + 4; return true; }
  if (end - p >= 5 && strncmp(p, "false", 5) == 0) { *after = p + 5; return true; }
  if (end - p >= 4 && strncmp(p, "null", 4) == 0) { *after = p + 4; return true; }
  return juno_json_parse_number(p, end, after);
}

static bool juno_json_scan_object(const char* objectStart, const char* objectEnd,
                                   const char* key, int32_t keyLength,
                                   const char** valueStart, const char** valueEnd) {
  const char* p = objectStart + 1;
  juno_json_skip_space(&p, objectEnd);
  while (p < objectEnd && *p != '}') {
    const char* contentStart = p + 1;
    const char* keyEnd;
    if (!juno_json_parse_string(p, objectEnd, &keyEnd)) return false;
    bool matches = (keyEnd - contentStart - 1) == keyLength
        && strncmp(contentStart, key, static_cast<size_t>(keyLength)) == 0;
    p = keyEnd;
    juno_json_skip_space(&p, objectEnd);
    if (p >= objectEnd || *p++ != ':') return false;
    juno_json_skip_space(&p, objectEnd);
    const char* candidateStart = p;
    const char* candidateEnd;
    if (!juno_json_parse_value(p, objectEnd, &candidateEnd, 1)) return false;
    if (matches) {
      *valueStart = candidateStart;
      *valueEnd = candidateEnd;
      return true;
    }
    p = candidateEnd;
    juno_json_skip_space(&p, objectEnd);
    if (p < objectEnd && *p == ',') { p++; juno_json_skip_space(&p, objectEnd); }
  }
  return false;
}

static bool juno_json_scan_array(const char* arrayStart, const char* arrayEnd, int32_t index,
                                  const char** valueStart, const char** valueEnd) {
  const char* p = arrayStart + 1;
  juno_json_skip_space(&p, arrayEnd);
  int32_t current = 0;
  while (p < arrayEnd && *p != ']') {
    const char* candidateStart = p;
    const char* candidateEnd;
    if (!juno_json_parse_value(p, arrayEnd, &candidateEnd, 1)) return false;
    if (current == index) {
      *valueStart = candidateStart;
      *valueEnd = candidateEnd;
      return true;
    }
    current++;
    p = candidateEnd;
    juno_json_skip_space(&p, arrayEnd);
    if (p < arrayEnd && *p == ',') { p++; juno_json_skip_space(&p, arrayEnd); }
  }
  return false;
}

static int32_t juno_json_locate(const uint8_t* buffer, int32_t length, const char* path,
                                 const char** valueStart, const char** valueEnd) {
  if (buffer == nullptr || path == nullptr || length < 0) return -1;
  const char* documentStart = reinterpret_cast<const char*>(buffer);
  const char* documentEnd = documentStart + length;
  const char* rootStart = documentStart;
  juno_json_skip_space(&rootStart, documentEnd);
  const char* rootEnd;
  if (!juno_json_parse_value(rootStart, documentEnd, &rootEnd, 0)) return -1;
  const char* trailing = rootEnd;
  juno_json_skip_space(&trailing, documentEnd);
  if (trailing != documentEnd) return -1;

  const char* currentStart = rootStart;
  const char* currentEnd = rootEnd;
  const char* cursor = path;
  if (*cursor == '\0') {
    *valueStart = currentStart;
    *valueEnd = currentEnd;
    return 1;
  }

  while (*cursor != '\0') {
    if (*cursor == '[') {
      if (*currentStart != '[') return 0;
      cursor++;
      if (*cursor < '0' || *cursor > '9') return -1;
      int32_t index = 0;
      if (*cursor == '0' && cursor[1] >= '0' && cursor[1] <= '9') return -1;
      while (*cursor >= '0' && *cursor <= '9') {
        int32_t digit = *cursor++ - '0';
        if (index > (INT32_MAX - digit) / 10) return -1;
        index = index * 10 + digit;
      }
      if (*cursor++ != ']') return -1;
      if (!juno_json_scan_array(currentStart, currentEnd, index, &currentStart, &currentEnd)) return 0;
    } else {
      if (*currentStart != '{') return 0;
      const char* segmentStart = cursor;
      while (*cursor != '\0' && *cursor != '.' && *cursor != '[') cursor++;
      int32_t segmentLength = static_cast<int32_t>(cursor - segmentStart);
      if (segmentLength == 0) return -1;
      if (!juno_json_scan_object(currentStart, currentEnd, segmentStart, segmentLength,
                                 &currentStart, &currentEnd)) return 0;
    }

    if (*cursor == '\0') break;
    if (*cursor == '[') continue;
    if (*cursor != '.') return -1;
    cursor++;
    if (*cursor == '\0' || *cursor == '.' || *cursor == '[') return -1;
  }
  *valueStart = currentStart;
  *valueEnd = currentEnd;
  return 1;
}

extern "C" int32_t juno_json_type(const uint8_t* buffer, int32_t length, const char* path) {
  const char* start;
  const char* end;
  int32_t status = juno_json_locate(buffer, length, path, &start, &end);
  if (status < 0) return JUNO_JSON_INVALID;
  if (status == 0) return JUNO_JSON_MISSING;
  if (*start == 'n') return JUNO_JSON_NULL;
  if (*start == 't' || *start == 'f') return JUNO_JSON_BOOLEAN;
  if (*start == '"') return JUNO_JSON_STRING;
  if (*start == '{') return JUNO_JSON_OBJECT;
  if (*start == '[') return JUNO_JSON_ARRAY;
  return JUNO_JSON_NUMBER;
}

static bool juno_json_parse_long(const char* start, const char* end, int64_t* result) {
  const char* parsedEnd;
  if (!juno_json_parse_number(start, end, &parsedEnd) || parsedEnd != end) return false;
  bool negative = *start == '-';
  const char* p = start + (negative ? 1 : 0);
  uint64_t limit = negative ? static_cast<uint64_t>(INT64_MAX) + 1u
                            : static_cast<uint64_t>(INT64_MAX);
  uint64_t value = 0;
  while (p < end) {
    if (*p < '0' || *p > '9') return false;
    uint64_t digit = static_cast<uint64_t>(*p++ - '0');
    if (value > (limit - digit) / 10u) return false;
    value = value * 10u + digit;
  }
  if (negative && value == static_cast<uint64_t>(INT64_MAX) + 1u) {
    *result = INT64_MIN;
  } else {
    *result = negative ? -static_cast<int64_t>(value) : static_cast<int64_t>(value);
  }
  return true;
}

extern "C" int32_t juno_json_get_int(const uint8_t* buffer, int32_t length, const char* key) {
  const char* start;
  const char* end;
  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0;
  int64_t value;
  if (!juno_json_parse_long(start, end, &value) || value < INT32_MIN || value > INT32_MAX) return 0;
  return static_cast<int32_t>(value);
}

extern "C" int64_t juno_json_get_long(const uint8_t* buffer, int32_t length, const char* key) {
  const char* start;
  const char* end;
  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0;
  int64_t value;
  return juno_json_parse_long(start, end, &value) ? value : 0;
}

