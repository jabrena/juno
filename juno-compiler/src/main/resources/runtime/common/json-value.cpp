extern "C" int32_t juno_json_get_bool(const uint8_t* buffer, int32_t length, const char* key) {
  const char* start;
  const char* end;
  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0;
  return (end - start == 4 && strncmp(start, "true", 4) == 0) ? 1 : 0;
}

static int32_t juno_json_hex_value(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  return c - 'A' + 10;
}

static uint32_t juno_json_hex4(const char* p) {
  uint32_t value = 0;
  for (int32_t i = 0; i < 4; i++) value = value * 16u + static_cast<uint32_t>(juno_json_hex_value(p[i]));
  return value;
}

static int32_t juno_json_utf8_length(uint32_t codePoint) {
  if (codePoint <= 0x7fu) return 1;
  if (codePoint <= 0x7ffu) return 2;
  if (codePoint <= 0xffffu) return 3;
  return 4;
}

static void juno_json_write_utf8(uint32_t codePoint, uint8_t* out, int32_t* written) {
  int32_t p = *written;
  if (codePoint <= 0x7fu) {
    out[p++] = static_cast<uint8_t>(codePoint);
  } else if (codePoint <= 0x7ffu) {
    out[p++] = static_cast<uint8_t>(0xc0u | (codePoint >> 6));
    out[p++] = static_cast<uint8_t>(0x80u | (codePoint & 0x3fu));
  } else if (codePoint <= 0xffffu) {
    out[p++] = static_cast<uint8_t>(0xe0u | (codePoint >> 12));
    out[p++] = static_cast<uint8_t>(0x80u | ((codePoint >> 6) & 0x3fu));
    out[p++] = static_cast<uint8_t>(0x80u | (codePoint & 0x3fu));
  } else {
    out[p++] = static_cast<uint8_t>(0xf0u | (codePoint >> 18));
    out[p++] = static_cast<uint8_t>(0x80u | ((codePoint >> 12) & 0x3fu));
    out[p++] = static_cast<uint8_t>(0x80u | ((codePoint >> 6) & 0x3fu));
    out[p++] = static_cast<uint8_t>(0x80u | (codePoint & 0x3fu));
  }
  *written = p;
}

extern "C" int32_t juno_json_get_string(const uint8_t* buffer, int32_t length, const char* key,
                                          uint8_t* out, int32_t outLength) {
  const char* start;
  const char* end;
  if (out == nullptr || outLength <= 0
      || juno_json_locate(buffer, length, key, &start, &end) != 1
      || start >= end || *start != '"') return 0;
  const char* p = start + 1;
  const char* contentEnd = end - 1;
  int32_t written = 0;
  while (p < contentEnd) {
    unsigned char c = static_cast<unsigned char>(*p++);
    if (c != '\\') {
      if (written >= outLength) break;
      out[written++] = c;
      continue;
    }
    char escape = *p++;
    if (escape != 'u') {
      uint8_t decoded = static_cast<uint8_t>(escape);
      if (escape == 'b') decoded = '\b';
      else if (escape == 'f') decoded = '\f';
      else if (escape == 'n') decoded = '\n';
      else if (escape == 'r') decoded = '\r';
      else if (escape == 't') decoded = '\t';
      if (written >= outLength) break;
      out[written++] = decoded;
      continue;
    }
    uint32_t codePoint = juno_json_hex4(p);
    p += 4;
    if (codePoint >= 0xd800u && codePoint <= 0xdbffu) {
      if (contentEnd - p < 6 || p[0] != '\\' || p[1] != 'u') return 0;
      uint32_t low = juno_json_hex4(p + 2);
      if (low < 0xdc00u || low > 0xdfffu) return 0;
      codePoint = 0x10000u + ((codePoint - 0xd800u) << 10) + (low - 0xdc00u);
      p += 6;
    } else if (codePoint >= 0xdc00u && codePoint <= 0xdfffu) {
      return 0;
    }
    int32_t utf8Length = juno_json_utf8_length(codePoint);
    if (written > outLength - utf8Length) break;
    juno_json_write_utf8(codePoint, out, &written);
  }
  return written;
}

extern "C" int32_t juno_json_array_size(const uint8_t* buffer, int32_t length, const char* key) {
  const char* start;
  const char* end;
  if (juno_json_locate(buffer, length, key, &start, &end) != 1 || *start != '[') return -1;
  const char* p = start + 1;
  juno_json_skip_space(&p, end);
  int32_t size = 0;
  while (p < end && *p != ']') {
    const char* elementEnd;
    if (!juno_json_parse_value(p, end, &elementEnd, 1)) return -1;
    if (size == INT32_MAX) return -1;
    size++;
    p = elementEnd;
    juno_json_skip_space(&p, end);
    if (p < end && *p == ',') { p++; juno_json_skip_space(&p, end); }
  }
  return size;
}

