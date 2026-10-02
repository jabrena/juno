package io.github.jabrena.juno.backend;

import java.util.Set;

/**
 * Source text of the runtime shim's networking helpers: the allocation-free JSON scanner, the
 * HTTP/1.1 client codec over {@code WiFiClient}/{@code WiFiSSLClient}, the single-connection HTTP
 * server, and SMTP/POP3 — each appended by {@link RuntimeShim} only when the lowered program uses it.
 */
final class NetworkShimLibraries {
    private NetworkShimLibraries() {
    }

    /** One bounded UDP transport shared by the program's UDP intrinsics. */
    static String udpHelpers(String declaration) {
        return """

                ${JUNO_UDP_DECLARATION}
                static bool juno_udp_listening = false;

                static IPAddress juno_udp_discovery_group() {
                  // Limited broadcast. A multicast group would be tidier, but neither board really joins one:
                  // the UNO Q's BridgeUDP::beginMulticast ignores the address and just binds the port, and
                  // the UNO R4 WiFi's modem cannot join at all. A socket bound to the port receives broadcast.
                  return IPAddress(255, 255, 255, 255);
                }

                static bool juno_udp_valid_port(int32_t port) {
                  return port > 0 && port <= 65535;
                }

                static int32_t juno_udp_send_to(const IPAddress& address, int32_t port,
                                                const uint8_t* payload, int32_t length) {
                  if (!juno_udp_listening || !juno_udp_valid_port(port) || payload == nullptr || length < 0) {
                    return -1;
                  }
                  if (juno_udp.beginPacket(address, static_cast<uint16_t>(port)) != 1) return -1;
                  size_t written = juno_udp.write(payload, static_cast<size_t>(length));
                  if (written != static_cast<size_t>(length)) {
                    juno_udp.endPacket();
                    return -1;
                  }
                  return juno_udp.endPacket() == 1 ? length : -1;
                }

                extern "C" int32_t juno_udp_listen(int32_t localPort) {
                  if (!juno_udp_valid_port(localPort)) return 0;
                  if (juno_udp_listening) juno_udp.stop();
                  juno_udp_listening = juno_udp.beginMulticast(
                      juno_udp_discovery_group(), static_cast<uint16_t>(localPort)) == 1;
                  // Not every WiFi stack accepts the multicast call (the UNO R4 WiFi's does not). A plain socket
                  // receives the same broadcast and unicast packets, so fall back to it.
                  if (!juno_udp_listening) {
                    juno_udp_listening = juno_udp.begin(static_cast<uint16_t>(localPort)) == 1;
                  }
                  return juno_udp_listening ? 1 : 0;
                }

                extern "C" int32_t juno_udp_send(const int32_t* address, int32_t remotePort,
                                                  const uint8_t* payload, int32_t length) {
                  if (address == nullptr) return -1;
                  for (int32_t i = 0; i < 4; i++) {
                    if (address[i] < 0 || address[i] > 255) return -1;
                  }
                  IPAddress remote(static_cast<uint8_t>(address[0]), static_cast<uint8_t>(address[1]),
                                   static_cast<uint8_t>(address[2]), static_cast<uint8_t>(address[3]));
                  return juno_udp_send_to(remote, remotePort, payload, length);
                }

                extern "C" int32_t juno_udp_broadcast(int32_t remotePort, const uint8_t* payload,
                                                       int32_t length) {
                  return juno_udp_send_to(juno_udp_discovery_group(), remotePort, payload, length);
                }

                extern "C" int32_t juno_udp_receive(uint8_t* payload, int32_t capacity, int32_t* source) {
                  if (!juno_udp_listening || payload == nullptr || source == nullptr || capacity <= 0) return -1;
                  int32_t packetLength = static_cast<int32_t>(juno_udp.parsePacket());
                  if (packetLength <= 0) return 0;
                  auto remote = juno_udp.remoteIP();
                  source[0] = remote[0];
                  source[1] = remote[1];
                  source[2] = remote[2];
                  source[3] = remote[3];
                  source[4] = static_cast<int32_t>(juno_udp.remotePort());
                  int32_t wanted = packetLength < capacity ? packetLength : capacity;
                  int32_t copied = static_cast<int32_t>(juno_udp.read(payload, wanted));
                  if (copied < 0) return -1;
                  while (juno_udp.available() > 0) juno_udp.read();
                  return copied;
                }

                extern "C" void juno_udp_stop() {
                  if (juno_udp_listening) juno_udp.stop();
                  juno_udp_listening = false;
                }
                """.replace("${JUNO_UDP_DECLARATION}", declaration);
    }

    /**
     * A bounded, non-allocating JSON scanner, exposed to the generated assembly as {@code extern "C"}
     * entry points.
     */
    static String jsonHelpers() {
        return """

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
                  return c == ' ' || c == '\\t' || c == '\\r' || c == '\\n';
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
                    if (c != '\\\\') continue;
                    if (p >= end) return false;
                    char escape = *p++;
                    if (escape == '"' || escape == '\\\\' || escape == '/' || escape == 'b'
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
                  if (*cursor == '\\0') {
                    *valueStart = currentStart;
                    *valueEnd = currentEnd;
                    return 1;
                  }

                  while (*cursor != '\\0') {
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
                      while (*cursor != '\\0' && *cursor != '.' && *cursor != '[') cursor++;
                      int32_t segmentLength = static_cast<int32_t>(cursor - segmentStart);
                      if (segmentLength == 0) return -1;
                      if (!juno_json_scan_object(currentStart, currentEnd, segmentStart, segmentLength,
                                                 &currentStart, &currentEnd)) return 0;
                    }

                    if (*cursor == '\\0') break;
                    if (*cursor == '[') continue;
                    if (*cursor != '.') return -1;
                    cursor++;
                    if (*cursor == '\\0' || *cursor == '.' || *cursor == '[') return -1;
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

                static bool juno_json_parse_double(const char* start, const char* end, double* result) {
                  const char* parsedEnd;
                  if (!juno_json_parse_number(start, end, &parsedEnd) || parsedEnd != end) return false;
                  bool negative = *start == '-';
                  const char* p = start + (negative ? 1 : 0);
                  double significand = 0.0;
                  int32_t significantDigits = 0;
                  int64_t decimalExponent = 0;
                  while (p < end && *p >= '0' && *p <= '9') {
                    int32_t digit = *p++ - '0';
                    if (significantDigits < 18 && (significantDigits > 0 || digit != 0)) {
                      significand = significand * 10.0 + digit;
                      significantDigits++;
                    } else if (significantDigits >= 18) {
                      decimalExponent++;
                    }
                  }
                  if (p < end && *p == '.') {
                    p++;
                    while (p < end && *p >= '0' && *p <= '9') {
                      int32_t digit = *p++ - '0';
                      if (significantDigits == 0 && digit == 0) {
                        decimalExponent--;
                      } else if (significantDigits < 18) {
                        significand = significand * 10.0 + digit;
                        significantDigits++;
                        decimalExponent--;
                      }
                    }
                  }
                  if (p < end && (*p == 'e' || *p == 'E')) {
                    p++;
                    bool exponentNegative = false;
                    if (*p == '+' || *p == '-') { exponentNegative = *p == '-'; p++; }
                    int32_t exponent = 0;
                    while (p < end && *p >= '0' && *p <= '9') {
                      int32_t digit = *p++ - '0';
                      if (exponent < 100000) exponent = exponent * 10 + digit;
                    }
                    decimalExponent += exponentNegative ? -static_cast<int64_t>(exponent) : exponent;
                  }
                  if (significand == 0.0) { *result = negative ? -0.0 : 0.0; return true; }
                  if (decimalExponent > 400) return false;
                  if (decimalExponent < -400) { *result = negative ? -0.0 : 0.0; return true; }
                  double scale = 1.0;
                  int32_t scalePower = static_cast<int32_t>(decimalExponent < 0 ? -decimalExponent : decimalExponent);
                  for (int32_t i = 0; i < scalePower; i++) scale *= 10.0;
                  double value = decimalExponent < 0 ? significand / scale : significand * scale;
                  if (!isfinite(value)) return false;
                  *result = negative ? -value : value;
                  return true;
                }

                extern "C" JUNO_ASM_ABI double juno_json_get_double(const uint8_t* buffer, int32_t length, const char* key) {
                  const char* start;
                  const char* end;
                  if (juno_json_locate(buffer, length, key, &start, &end) != 1) return 0.0;
                  double value;
                  return juno_json_parse_double(start, end, &value) ? value : 0.0;
                }

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
                    if (c != '\\\\') {
                      if (written >= outLength) break;
                      out[written++] = c;
                      continue;
                    }
                    char escape = *p++;
                    if (escape != 'u') {
                      uint8_t decoded = static_cast<uint8_t>(escape);
                      if (escape == 'b') decoded = '\\b';
                      else if (escape == 'f') decoded = '\\f';
                      else if (escape == 'n') decoded = '\\n';
                      else if (escape == 'r') decoded = '\\r';
                      else if (escape == 't') decoded = '\\t';
                      if (written >= outLength) break;
                      out[written++] = decoded;
                      continue;
                    }
                    uint32_t codePoint = juno_json_hex4(p);
                    p += 4;
                    if (codePoint >= 0xd800u && codePoint <= 0xdbffu) {
                      if (contentEnd - p < 6 || p[0] != '\\\\' || p[1] != 'u') return 0;
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

                """;
    }

    /**
     * {@code Json.getString(byte[], int, String)}'s shim — separate from {@link #jsonHelpers()}
     * since it needs the runtime-string pool ({@link ShimLibraries#runtimeStringHelpers()}), only guaranteed
     * present when {@code JSON_GET_STRING_VALUE} is itself the reason {@link ShimFeature#RUNTIME_STRINGS} is
     * true (a program using only e.g. {@code Json.getInt} never gets that pool at all).
     */
    static String jsonStringValueHelpers() {
        return """

                // Any scalar's raw JSON text, not just JSON strings — a JSON string's surrounding
                // quotes are stripped, but nothing is escape-decoded (unlike juno_json_get_string),
                // since a number/boolean/null literal never has escapes to begin with.
                extern "C" int32_t juno_json_get_string_value(const uint8_t* buffer, int32_t length, const char* key) {
                  const char* start;
                  const char* end;
                  if (juno_json_locate(buffer, length, key, &start, &end) != 1 || start >= end
                      || *start == '{' || *start == '[') return 0;
                  const char* contentStart = start;
                  const char* contentEnd = end;
                  if (*start == '"') {
                    if (end - start < 2) return 0;
                    contentStart = start + 1;
                    contentEnd = end - 1;
                  }
                  int32_t contentLength = static_cast<int32_t>(contentEnd - contentStart);
                  if (contentLength < 0 || static_cast<uint32_t>(contentLength) >= JUNO_STRING_SLOT_SIZE) return 0;
                  char* slot = juno_string_slots[juno_string_slot_cursor];
                  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
                  for (int32_t i = 0; i < contentLength; i++) slot[i] = contentStart[i];
                  slot[contentLength] = '\\0';
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(slot));
                }

                """;
    }

    /** Backs the HTTP/HTTPS APIs with a shared HTTP/1.1 codec over plain or TLS WiFi clients. */
    static String httpHelpers(Set<ShimFeature> features, String httpsClientDeclaration) {
        StringBuilder helpers = new StringBuilder("""

                template <typename Client>
                static int32_t juno_http_request(Client& client, const char* method, const char* host, int32_t port,
                                                  const char* path, const char* body,
                                                  uint8_t* responseBuffer, int32_t responseBufferLength,
                                                  uint8_t* headersBuffer, int32_t headersBufferLength,
                                                  int32_t* statusAndHeadersLength) {
                  if (!client.connect(host, static_cast<uint16_t>(port))) {
                    statusAndHeadersLength[0] = 0;
                    statusAndHeadersLength[1] = 0;
                    return -1;
                  }
                  client.print(method);
                  client.print(' ');
                  client.print(path);
                  client.print(" HTTP/1.1\\r\\nHost: ");
                  client.print(host);
                  client.print("\\r\\nConnection: close\\r\\n");
                  if (body != nullptr) {
                    client.print("Content-Type: application/json\\r\\nContent-Length: ");
                    client.print(static_cast<unsigned long>(strlen(body)));
                    client.print("\\r\\n\\r\\n");
                    client.print(body);
                  } else {
                    client.print("\\r\\n");
                  }

                  const unsigned long deadline = millis() + 5000;
                  // The status line ("HTTP/1.1 200 OK\\r\\n") is parsed for its numeric status code
                  // only; everything else on it (the HTTP version and the reason phrase) is skipped.
                  bool inStatusLine = true;
                  int32_t statusLinePhase = 0; // 0 = before the version's trailing space, 1 = digits, 2 = skip rest
                  int32_t statusValue = 0;
                  bool inBody = false;
                  int32_t headersWritten = 0;
                  char recent[4] = {0, 0, 0, 0};
                  bool chunked = false;
                  int32_t chunkedMatch = 0;
                  const char* chunkedMarker = "chunked";

                  // A non-chunked response's length is usually known up front via Content-Length,
                  // detected the same rolling-match way as "chunked" above (safe for the same reason:
                  // no internal repeated-character overlap). Reading exactly that many body bytes lets
                  // the loop return as soon as the response is complete, instead of only ever stopping
                  // via the connection closing or the fixed deadline below — relying solely on the
                  // latter risks abandoning the socket mid-response, which can wedge the WiFi module's
                  // TLS state for the next request on some servers/networks.
                  bool hasContentLength = false;
                  bool inContentLengthValue = false;
                  int32_t contentLength = 0;
                  int32_t contentLengthMatch = 0;
                  const char* contentLengthMarker = "content-length:";

                  int32_t written = 0;
                  int32_t chunkState = 0; // 0 = reading hex size, 1 = chunk data, 2 = trailing CRLF, 3 = done
                  int32_t chunkRemaining = 0;
                  int32_t chunkSizeValue = 0;
                  bool chunkExtension = false;

                  while (millis() < deadline) {
                    if (!client.available()) {
                      if (!client.connected()) break;
                      delay(1);
                      continue;
                    }
                    int value = client.read();
                    if (value < 0) break;
                    char c = static_cast<char>(value);

                    if (inStatusLine) {
                      if (statusLinePhase == 0) {
                        if (c == ' ') statusLinePhase = 1;
                      } else if (statusLinePhase == 1) {
                        if (c >= '0' && c <= '9') statusValue = statusValue * 10 + (c - '0');
                        else statusLinePhase = 2;
                      }
                      if (c == '\\n') inStatusLine = false;
                      continue;
                    }

                    if (!inBody) {
                      char lower = (c >= 'A' && c <= 'Z') ? static_cast<char>(c - 'A' + 'a') : c;
                      if (lower == chunkedMarker[chunkedMatch]) {
                        chunkedMatch++;
                        if (chunkedMarker[chunkedMatch] == 0) chunked = true;
                      } else {
                        chunkedMatch = (lower == chunkedMarker[0]) ? 1 : 0;
                      }
                      if (inContentLengthValue) {
                        if (c >= '0' && c <= '9') contentLength = contentLength * 10 + (c - '0');
                        else if (c != ' ') inContentLengthValue = false;
                      } else if (lower == contentLengthMarker[contentLengthMatch]) {
                        contentLengthMatch++;
                        if (contentLengthMarker[contentLengthMatch] == 0) {
                          inContentLengthValue = true;
                          hasContentLength = true;
                          contentLength = 0;
                        }
                      } else {
                        contentLengthMatch = (lower == contentLengthMarker[0]) ? 1 : 0;
                      }
                      recent[0] = recent[1];
                      recent[1] = recent[2];
                      recent[2] = recent[3];
                      recent[3] = c;
                      if (recent[0] == '\\r' && recent[1] == '\\n' && recent[2] == '\\r' && recent[3] == '\\n') {
                        inBody = true;
                      }
                      if (headersWritten < headersBufferLength) headersBuffer[headersWritten] = static_cast<uint8_t>(c);
                      headersWritten++;
                      continue;
                    }

                    if (!chunked) {
                      if (written < responseBufferLength) responseBuffer[written] = static_cast<uint8_t>(c);
                      written++;
                      if (hasContentLength && written >= contentLength) break;
                      continue;
                    }

                    switch (chunkState) {
                      case 0:
                        if (c == '\\r') break;
                        if (c == '\\n') {
                          chunkRemaining = chunkSizeValue;
                          chunkSizeValue = 0;
                          chunkExtension = false;
                          chunkState = (chunkRemaining == 0) ? 3 : 1;
                          break;
                        }
                        if (chunkExtension) break;
                        if (c >= '0' && c <= '9') chunkSizeValue = chunkSizeValue * 16 + (c - '0');
                        else if (c >= 'a' && c <= 'f') chunkSizeValue = chunkSizeValue * 16 + (c - 'a' + 10);
                        else if (c >= 'A' && c <= 'F') chunkSizeValue = chunkSizeValue * 16 + (c - 'A' + 10);
                        else chunkExtension = true;
                        break;
                      case 1:
                        if (written < responseBufferLength) responseBuffer[written] = static_cast<uint8_t>(c);
                        written++;
                        chunkRemaining--;
                        if (chunkRemaining == 0) chunkState = 2;
                        break;
                      case 2:
                        if (c == '\\n') chunkState = 0;
                        break;
                      default:
                        break;
                    }
                    if (chunkState == 3) break;
                  }
                  client.stop();
                  statusAndHeadersLength[0] = statusValue;
                  statusAndHeadersLength[1] = headersWritten < headersBufferLength ? headersWritten : headersBufferLength;
                  return written < responseBufferLength ? written : responseBufferLength;
                }

                """);
        if (features.contains(ShimFeature.HTTP)) {
            helpers.append("""

                    extern "C" int32_t juno_http_get(const char* host, int32_t port, const char* path,
                                                      uint8_t* responseBuffer, int32_t responseBufferLength,
                                                      uint8_t* headersBuffer, int32_t headersBufferLength,
                                                      int32_t* statusAndHeadersLength) {
                      WiFiClient client;
                      return juno_http_request(client, "GET", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_http_post(const char* host, int32_t port, const char* path, const char* body,
                                                       uint8_t* responseBuffer, int32_t responseBufferLength,
                                                       uint8_t* headersBuffer, int32_t headersBufferLength,
                                                       int32_t* statusAndHeadersLength) {
                      WiFiClient client;
                      return juno_http_request(client, "POST", host, port, path, body,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_http_delete(const char* host, int32_t port, const char* path,
                                                         uint8_t* responseBuffer, int32_t responseBufferLength,
                                                         uint8_t* headersBuffer, int32_t headersBufferLength,
                                                         int32_t* statusAndHeadersLength) {
                      WiFiClient client;
                      return juno_http_request(client, "DELETE", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_http_patch(const char* host, int32_t port, const char* path, const char* body,
                                                        uint8_t* responseBuffer, int32_t responseBufferLength,
                                                        uint8_t* headersBuffer, int32_t headersBufferLength,
                                                        int32_t* statusAndHeadersLength) {
                      WiFiClient client;
                      return juno_http_request(client, "PATCH", host, port, path, body,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_http_query(const char* host, int32_t port, const char* path, const char* body,
                                                        uint8_t* responseBuffer, int32_t responseBufferLength,
                                                        uint8_t* headersBuffer, int32_t headersBufferLength,
                                                        int32_t* statusAndHeadersLength) {
                      WiFiClient client;
                      return juno_http_request(client, "QUERY", host, port, path, body,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    """);
        }
        if (features.contains(ShimFeature.HTTPS)) {
            helpers.append("""

                    extern "C" int32_t juno_https_get(const char* host, int32_t port, const char* path,
                                                       uint8_t* responseBuffer, int32_t responseBufferLength,
                                                       uint8_t* headersBuffer, int32_t headersBufferLength,
                                                       int32_t* statusAndHeadersLength) {
                      ${JUNO_HTTPS_CLIENT_DECLARATION}
                      return juno_http_request(client, "GET", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_https_post(const char* host, int32_t port, const char* path, const char* body,
                                                        uint8_t* responseBuffer, int32_t responseBufferLength,
                                                        uint8_t* headersBuffer, int32_t headersBufferLength,
                                                        int32_t* statusAndHeadersLength) {
                      ${JUNO_HTTPS_CLIENT_DECLARATION}
                      return juno_http_request(client, "POST", host, port, path, body,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_https_delete(const char* host, int32_t port, const char* path,
                                                          uint8_t* responseBuffer, int32_t responseBufferLength,
                                                          uint8_t* headersBuffer, int32_t headersBufferLength,
                                                          int32_t* statusAndHeadersLength) {
                      ${JUNO_HTTPS_CLIENT_DECLARATION}
                      return juno_http_request(client, "DELETE", host, port, path, nullptr,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_https_patch(const char* host, int32_t port, const char* path, const char* body,
                                                         uint8_t* responseBuffer, int32_t responseBufferLength,
                                                         uint8_t* headersBuffer, int32_t headersBufferLength,
                                                         int32_t* statusAndHeadersLength) {
                      ${JUNO_HTTPS_CLIENT_DECLARATION}
                      return juno_http_request(client, "PATCH", host, port, path, body,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    extern "C" int32_t juno_https_query(const char* host, int32_t port, const char* path, const char* body,
                                                         uint8_t* responseBuffer, int32_t responseBufferLength,
                                                         uint8_t* headersBuffer, int32_t headersBufferLength,
                                                         int32_t* statusAndHeadersLength) {
                      ${JUNO_HTTPS_CLIENT_DECLARATION}
                      return juno_http_request(client, "QUERY", host, port, path, body,
                                               responseBuffer, responseBufferLength,
                                               headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    """);
        }
        if (features.contains(ShimFeature.HTTPS_PATH_BUFFER)) {
            helpers.append("""

                    static const int32_t JUNO_HTTP_PATH_BUFFER_CAPACITY = 256;

                    extern "C" int32_t juno_https_get_path_buffer(const char* host, int32_t port,
                                                                   const uint8_t* pathBuffer, int32_t pathLength,
                                                                   uint8_t* responseBuffer, int32_t responseBufferLength,
                                                                   uint8_t* headersBuffer, int32_t headersBufferLength,
                                                                   int32_t* statusAndHeadersLength) {
                      statusAndHeadersLength[0] = 0;
                      statusAndHeadersLength[1] = 0;
                      if (pathLength < 0 || pathLength >= JUNO_HTTP_PATH_BUFFER_CAPACITY) {
                        return -1;
                      }
                      char path[JUNO_HTTP_PATH_BUFFER_CAPACITY];
                      memcpy(path, pathBuffer, (size_t) pathLength);
                      path[pathLength] = '\\0';
                      return juno_https_get(host, port, path, responseBuffer, responseBufferLength,
                                            headersBuffer, headersBufferLength, statusAndHeadersLength);
                    }

                    """);
        }
        return helpers.toString().replace("${JUNO_HTTPS_CLIENT_DECLARATION}", httpsClientDeclaration);
    }

    /**
     * Backs {@link io.github.jabrena.juno.api.io.net.http.HttpServer}: a single static {@code
     * WiFiServer} (constructed via placement-new into static storage once {@code begin(port)}
     * runs, since {@code WiFiServer} has no default constructor and this backend never calls the
     * heap allocator {@code new} for anything Java-visible) plus a single "current" {@code
     * WiFiClient} held across one {@code accept()}/{@code respond()} pair — this hardware can only
     * meaningfully serve one request at a time, so there is no connection-handle scheme.
     * {@code accept()} mirrors {@link #httpHelpers()}'s response-parsing state machine, reversed
     * to parse a request line and headers instead of a status line.
     */
    static String httpServerHelpers() {
        return """

                alignas(WiFiServer) static unsigned char juno_http_server_storage[sizeof(WiFiServer)];
                static WiFiServer* juno_http_server_instance = nullptr;
                static WiFiClient juno_http_server_client;
                static char juno_http_server_method_buf[8];
                static char juno_http_server_path_buf[96];

                extern "C" void juno_http_server_begin(int32_t port) {
                  juno_http_server_instance = new (juno_http_server_storage) WiFiServer(static_cast<uint16_t>(port));
                  juno_http_server_instance->begin();
                }

                extern "C" int32_t juno_http_server_accept(uint8_t* bodyBuffer, int32_t bodyBufferLength) {
                  if (juno_http_server_instance == nullptr) return -1;
                  WiFiClient client = juno_http_server_instance->available();
                  if (!client) return -1;
                  juno_http_server_client = client;

                  int32_t methodLength = 0;
                  int32_t pathLength = 0;
                  int32_t requestLinePhase = 0; // 0 = method, 1 = path, 2 = skip rest of the line
                  bool gotRequestLine = false;
                  bool inBody = false;
                  char recent[4] = {0, 0, 0, 0};
                  bool hasContentLength = false;
                  bool inContentLengthValue = false;
                  int32_t contentLength = 0;
                  int32_t contentLengthMatch = 0;
                  const char* contentLengthMarker = "content-length:";
                  int32_t written = 0;

                  const unsigned long deadline = millis() + 5000;
                  while (millis() < deadline) {
                    if (!client.available()) {
                      if (!client.connected()) break;
                      delay(1);
                      continue;
                    }
                    int value = client.read();
                    if (value < 0) break;
                    char c = static_cast<char>(value);

                    if (!gotRequestLine) {
                      if (requestLinePhase == 0) {
                        if (c == ' ') {
                          requestLinePhase = 1;
                        } else if (methodLength < static_cast<int32_t>(sizeof(juno_http_server_method_buf)) - 1) {
                          juno_http_server_method_buf[methodLength++] = c;
                        }
                      } else if (requestLinePhase == 1) {
                        if (c == ' ') {
                          requestLinePhase = 2;
                        } else if (pathLength < static_cast<int32_t>(sizeof(juno_http_server_path_buf)) - 1) {
                          juno_http_server_path_buf[pathLength++] = c;
                        }
                      } else if (c == '\\n') {
                        gotRequestLine = true;
                      }
                      continue;
                    }

                    if (!inBody) {
                      char lower = (c >= 'A' && c <= 'Z') ? static_cast<char>(c - 'A' + 'a') : c;
                      if (inContentLengthValue) {
                        if (c >= '0' && c <= '9') contentLength = contentLength * 10 + (c - '0');
                        else if (c != ' ') inContentLengthValue = false;
                      } else if (lower == contentLengthMarker[contentLengthMatch]) {
                        contentLengthMatch++;
                        if (contentLengthMarker[contentLengthMatch] == 0) {
                          inContentLengthValue = true;
                          hasContentLength = true;
                          contentLength = 0;
                        }
                      } else {
                        contentLengthMatch = (lower == contentLengthMarker[0]) ? 1 : 0;
                      }
                      recent[0] = recent[1];
                      recent[1] = recent[2];
                      recent[2] = recent[3];
                      recent[3] = c;
                      if (recent[0] == '\\r' && recent[1] == '\\n' && recent[2] == '\\r' && recent[3] == '\\n') {
                        inBody = true;
                        if (!hasContentLength) break;
                      }
                      continue;
                    }

                    if (written < bodyBufferLength) bodyBuffer[written] = static_cast<uint8_t>(c);
                    written++;
                    if (written >= contentLength) break;
                  }
                  juno_http_server_method_buf[methodLength] = '\\0';
                  juno_http_server_path_buf[pathLength] = '\\0';
                  return written < bodyBufferLength ? written : bodyBufferLength;
                }

                static int32_t juno_http_server_pool_copy(const char* text, int32_t length) {
                  if (length < 0 || static_cast<uint32_t>(length) >= JUNO_STRING_SLOT_SIZE) return 0;
                  char* slot = juno_string_slots[juno_string_slot_cursor];
                  juno_string_slot_cursor = (juno_string_slot_cursor + 1u) % JUNO_STRING_SLOT_COUNT;
                  for (int32_t i = 0; i < length; i++) slot[i] = text[i];
                  slot[length] = '\\0';
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(slot));
                }

                extern "C" int32_t juno_http_server_method() {
                  return juno_http_server_pool_copy(juno_http_server_method_buf,
                      static_cast<int32_t>(strlen(juno_http_server_method_buf)));
                }

                extern "C" int32_t juno_http_server_path() {
                  return juno_http_server_pool_copy(juno_http_server_path_buf,
                      static_cast<int32_t>(strlen(juno_http_server_path_buf)));
                }

                static void juno_http_server_respond_bytes(int32_t status, const char* contentType,
                                                             const char* body, int32_t bodyLength) {
                  WiFiClient& client = juno_http_server_client;
                  client.print("HTTP/1.1 ");
                  client.print(status);
                  client.print(" \\r\\nContent-Type: ");
                  client.print(contentType);
                  client.print("\\r\\nContent-Length: ");
                  client.print(bodyLength);
                  client.print("\\r\\nConnection: close\\r\\n\\r\\n");
                  client.write(reinterpret_cast<const uint8_t*>(body), static_cast<size_t>(bodyLength));
                  client.stop();
                }

                extern "C" void juno_http_server_respond(int32_t status, const char* contentType, const char* body) {
                  juno_http_server_respond_bytes(status, contentType, body, static_cast<int32_t>(strlen(body)));
                }

                extern "C" void juno_http_server_respond_builder(int32_t status, const char* contentType,
                                                                   int32_t bodyHandle) {
                  auto* header = reinterpret_cast<int32_t*>(static_cast<intptr_t>(bodyHandle));
                  int32_t length = header[0];
                  const uint8_t* buffer = juno_string_builder_buffer(bodyHandle);
                  juno_http_server_respond_bytes(status, contentType, reinterpret_cast<const char*>(buffer), length);
                }

                """;
    }

    /**
     * {@code Smtp}/{@code Pop3Client}'s shim. {@code juno_read_line} is shared and templated over
     * the unrelated client types involved: {@code ESP_SSLClient} ({@code Smtp}'s STARTTLS
     * upgrade wrapper around a plain {@code WiFiClient}) and the core-selected implicit-TLS
     * client used by {@code Pop3Client} — the same template-over-client-type approach as
     * {@link #httpHelpers()}'s {@code juno_http_request}, reused here because there is no common
     * base class between the two client types worth naming.
     */
    static String emailHelpers(Set<ShimFeature> features, String tlsClientDeclaration) {
        StringBuilder helpers = new StringBuilder();
        helpers.append("""

                template <typename Client>
                static int32_t juno_read_line(Client& client, char* buffer, int32_t bufferCapacity, unsigned long deadline) {
                  int32_t length = 0;
                  while (true) {
                    if (!client.available()) {
                      if (!client.connected()) return -1;
                      if (millis() >= deadline) return -1;
                      delay(1);
                      continue;
                    }
                    int value = client.read();
                    if (value < 0) return -1;
                    char c = static_cast<char>(value);
                    if (c == '\\n') break;
                    if (c != '\\r' && length < bufferCapacity - 1) buffer[length] = c;
                    if (c != '\\r') length++;
                  }
                  int32_t written = length < bufferCapacity - 1 ? length : bufferCapacity - 1;
                  buffer[written] = 0;
                  return written;
                }

                """);
        if (features.contains(ShimFeature.SMTP) || features.contains(ShimFeature.SMTP_TLS)) {
            helpers.append("""
                    static int32_t juno_base64_encode(const char* data, int32_t length, char* out, int32_t outCapacity) {
                      static const char table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
                      int32_t needed = ((length + 2) / 3) * 4;
                      if (needed + 1 > outCapacity) return -1;
                      int32_t o = 0;
                      for (int32_t i = 0; i < length; i += 3) {
                        uint32_t b0 = static_cast<uint8_t>(data[i]);
                        uint32_t b1 = (i + 1 < length) ? static_cast<uint8_t>(data[i + 1]) : 0;
                        uint32_t b2 = (i + 2 < length) ? static_cast<uint8_t>(data[i + 2]) : 0;
                        uint32_t triple = (b0 << 16) | (b1 << 8) | b2;
                        out[o++] = table[(triple >> 18) & 0x3F];
                        out[o++] = table[(triple >> 12) & 0x3F];
                        out[o++] = (i + 1 < length) ? table[(triple >> 6) & 0x3F] : '=';
                        out[o++] = (i + 2 < length) ? table[triple & 0x3F] : '=';
                      }
                      out[o] = 0;
                      return o;
                    }

                    template <typename Client>
                    static int32_t juno_smtp_read_reply(Client& client, unsigned long deadline) {
                      char line[128];
                      int32_t code = -1;
                      while (true) {
                        int32_t lineLength = juno_read_line(client, line, sizeof(line), deadline);
                        if (lineLength < 4) return -1;
                        code = (line[0] - '0') * 100 + (line[1] - '0') * 10 + (line[2] - '0');
                        if (line[3] != '-') break;
                      }
                      return code;
                    }

                    """);
        }
        if (features.contains(ShimFeature.SMTP)) {
            helpers.append("""

                    extern "C" int32_t juno_smtp_send(const char* host, int32_t port,
                                                       const char* username, const char* password,
                                                       const char* from, const char* to,
                                                       const char* subject, const char* body) {
                      WiFiClient plainClient;
                      ESP_SSLClient client;
                      client.setInsecure();
                      client.setBufferSizes(2048, 512);
                      client.setClient(&plainClient, false);

                      if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
                      unsigned long deadline = millis() + 15000;

                      if (juno_smtp_read_reply(client, deadline) != 220) { client.stop(); return -2; }

                      client.print("EHLO juno\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -3; }

                      client.print("STARTTLS\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 220) { client.stop(); return -11; }

                      if (!client.connectSSL()) { client.stop(); return -12; }

                      // RFC 3207: capabilities must be re-read with a fresh EHLO once TLS is up.
                      client.print("EHLO juno\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -3; }

                      client.print("AUTH LOGIN\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 334) { client.stop(); return -4; }

                      char encoded[196];
                      if (juno_base64_encode(username, static_cast<int32_t>(strlen(username)), encoded, sizeof(encoded)) < 0) {
                        client.stop();
                        return -10;
                      }
                      client.print(encoded);
                      client.print("\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 334) { client.stop(); return -4; }

                      if (juno_base64_encode(password, static_cast<int32_t>(strlen(password)), encoded, sizeof(encoded)) < 0) {
                        client.stop();
                        return -10;
                      }
                      client.print(encoded);
                      client.print("\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 235) { client.stop(); return -5; }

                      client.print("MAIL FROM:<");
                      client.print(from);
                      client.print(">\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -6; }

                      client.print("RCPT TO:<");
                      client.print(to);
                      client.print(">\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -7; }

                      client.print("DATA\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 354) { client.stop(); return -8; }

                      client.print("From: "); client.print(from); client.print("\\r\\n");
                      client.print("To: "); client.print(to); client.print("\\r\\n");
                      client.print("Subject: "); client.print(subject); client.print("\\r\\n\\r\\n");
                      client.print(body);
                      client.print("\\r\\n.\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -9; }

                      client.print("QUIT\\r\\n");
                      client.stop();
                      return 0;
                    }

                    """);
        }
        if (features.contains(ShimFeature.SMTP_TLS)) {
            helpers.append("""
                    extern "C" int32_t juno_smtp_send_tls(const char* host, int32_t port,
                                                           const char* username, const char* password,
                                                           const char* from, const char* to,
                                                           const char* subject, const char* body) {
                      ${JUNO_TLS_CLIENT_DECLARATION}
                      if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
                      unsigned long deadline = millis() + 15000;

                      if (juno_smtp_read_reply(client, deadline) != 220) { client.stop(); return -2; }

                      client.print("EHLO juno\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -3; }

                      client.print("AUTH LOGIN\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 334) { client.stop(); return -4; }

                      char encoded[196];
                      if (juno_base64_encode(username, static_cast<int32_t>(strlen(username)), encoded, sizeof(encoded)) < 0) {
                        client.stop();
                        return -10;
                      }
                      client.print(encoded);
                      client.print("\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 334) { client.stop(); return -4; }

                      if (juno_base64_encode(password, static_cast<int32_t>(strlen(password)), encoded, sizeof(encoded)) < 0) {
                        client.stop();
                        return -10;
                      }
                      client.print(encoded);
                      client.print("\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 235) { client.stop(); return -5; }

                      client.print("MAIL FROM:<");
                      client.print(from);
                      client.print(">\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -6; }

                      client.print("RCPT TO:<");
                      client.print(to);
                      client.print(">\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -7; }

                      client.print("DATA\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 354) { client.stop(); return -8; }

                      client.print("From: "); client.print(from); client.print("\\r\\n");
                      client.print("To: "); client.print(to); client.print("\\r\\n");
                      client.print("Subject: "); client.print(subject); client.print("\\r\\n\\r\\n");
                      client.print(body);
                      client.print("\\r\\n.\\r\\n");
                      if (juno_smtp_read_reply(client, deadline) != 250) { client.stop(); return -9; }

                      client.print("QUIT\\r\\n");
                      client.stop();
                      return 0;
                    }

                    """);
        }
        if (features.contains(ShimFeature.POP3)) {
            helpers.append("""
                    template <typename Client>
                    static int32_t juno_pop3_expect_ok(Client& client, unsigned long deadline) {
                      char line[64];
                      int32_t lineLength = juno_read_line(client, line, sizeof(line), deadline);
                      return (lineLength >= 3 && line[0] == '+' && line[1] == 'O' && line[2] == 'K') ? 1 : 0;
                    }

                    static int32_t juno_pop3_parse_count(const char* line, int32_t lineLength) {
                      int32_t i = 3;
                      while (i < lineLength && line[i] == ' ') i++;
                      int32_t count = 0;
                      bool any = false;
                      while (i < lineLength && line[i] >= '0' && line[i] <= '9') {
                        count = count * 10 + (line[i] - '0');
                        any = true;
                        i++;
                      }
                      return any ? count : -1;
                    }

                    template <typename Client>
                    static int32_t juno_pop3_login(Client& client, const char* username, const char* password,
                                                    unsigned long deadline) {
                      if (!juno_pop3_expect_ok(client, deadline)) return -2;
                      client.print("USER "); client.print(username); client.print("\\r\\n");
                      if (!juno_pop3_expect_ok(client, deadline)) return -3;
                      client.print("PASS "); client.print(password); client.print("\\r\\n");
                      if (!juno_pop3_expect_ok(client, deadline)) return -4;
                      return 0;
                    }

                    extern "C" int32_t juno_pop3_message_count(const char* host, int32_t port,
                                                                const char* username, const char* password) {
                      ${JUNO_TLS_CLIENT_DECLARATION}
                      if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
                      unsigned long deadline = millis() + 10000;
                      int32_t loginResult = juno_pop3_login(client, username, password, deadline);
                      if (loginResult != 0) { client.stop(); return loginResult; }
                      client.print("STAT\\r\\n");
                      char line[64];
                      int32_t lineLength = juno_read_line(client, line, sizeof(line), deadline);
                      if (lineLength < 3 || line[0] != '+' || line[1] != 'O' || line[2] != 'K') { client.stop(); return -5; }
                      int32_t count = juno_pop3_parse_count(line, lineLength);
                      client.print("QUIT\\r\\n");
                      client.stop();
                      return count;
                    }

                    static bool juno_starts_with_ci(const char* line, int32_t lineLength, const char* prefix) {
                      int32_t i = 0;
                      while (prefix[i] != 0) {
                        if (i >= lineLength) return false;
                        char a = line[i];
                        char b = prefix[i];
                        char la = (a >= 'A' && a <= 'Z') ? static_cast<char>(a - 'A' + 'a') : a;
                        char lb = (b >= 'A' && b <= 'Z') ? static_cast<char>(b - 'A' + 'a') : b;
                        if (la != lb) return false;
                        i++;
                      }
                      return true;
                    }

                    static void juno_pop3_append_header(uint8_t* headersBuffer, int32_t headersBufferLength, int32_t* headersWritten,
                                                         const char* label, const char* value, int32_t valueLength) {
                      int32_t written = *headersWritten;
                      for (int32_t i = 0; label[i] != 0; i++) {
                        if (written < headersBufferLength) headersBuffer[written] = static_cast<uint8_t>(label[i]);
                        written++;
                      }
                      for (int32_t i = 0; i < valueLength; i++) {
                        if (written < headersBufferLength) headersBuffer[written] = static_cast<uint8_t>(value[i]);
                        written++;
                      }
                      if (written < headersBufferLength) headersBuffer[written] = '\\n';
                      written++;
                      *headersWritten = written;
                    }

                    static void juno_pop3_extract_header(const char* line, int32_t lineLength, int32_t prefixLength, const char* label,
                                                          uint8_t* headersBuffer, int32_t headersBufferLength, int32_t* headersWritten) {
                      int32_t valueStart = prefixLength;
                      if (valueStart < lineLength && line[valueStart] == ' ') valueStart++;
                      juno_pop3_append_header(headersBuffer, headersBufferLength, headersWritten, label,
                                               line + valueStart, lineLength - valueStart);
                    }

                    extern "C" int32_t juno_pop3_read_latest(const char* host, int32_t port,
                                                              const char* username, const char* password,
                                                              uint8_t* headersBuffer, int32_t headersBufferLength,
                                                              uint8_t* bodyBuffer, int32_t bodyBufferLength,
                                                              int32_t* status) {
                      status[0] = 0;
                      ${JUNO_TLS_CLIENT_DECLARATION}
                      if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
                      unsigned long deadline = millis() + 20000;

                      int32_t loginResult = juno_pop3_login(client, username, password, deadline);
                      if (loginResult != 0) { client.stop(); return loginResult; }

                      client.print("STAT\\r\\n");
                      char statLine[64];
                      int32_t statLineLength = juno_read_line(client, statLine, sizeof(statLine), deadline);
                      if (statLineLength < 3 || statLine[0] != '+' || statLine[1] != 'O' || statLine[2] != 'K') {
                        client.stop();
                        return -5;
                      }
                      int32_t count = juno_pop3_parse_count(statLine, statLineLength);
                      if (count <= 0) {
                        client.print("QUIT\\r\\n");
                        client.stop();
                        return -6;
                      }

                      client.print("RETR ");
                      client.print(count);
                      client.print("\\r\\n");
                      if (!juno_pop3_expect_ok(client, deadline)) { client.stop(); return -7; }

                      bool inHeaders = true;
                      int32_t headersWritten = 0;
                      int32_t bodyWritten = 0;
                      char lineBuf[256];
                      while (true) {
                        int32_t lineLength = juno_read_line(client, lineBuf, sizeof(lineBuf), deadline);
                        if (lineLength < 0) { client.stop(); return -8; }
                        const char* effectiveLine = lineBuf;
                        int32_t effectiveLength = lineLength;
                        if (lineLength > 0 && lineBuf[0] == '.') {
                          effectiveLine = lineBuf + 1;
                          effectiveLength = lineLength - 1;
                          if (effectiveLength == 0) break;
                        }
                        if (inHeaders) {
                          if (effectiveLength == 0) {
                            inHeaders = false;
                            continue;
                          }
                          if (juno_starts_with_ci(effectiveLine, effectiveLength, "from:")) {
                            juno_pop3_extract_header(effectiveLine, effectiveLength, 5, "From: ",
                                                      headersBuffer, headersBufferLength, &headersWritten);
                          } else if (juno_starts_with_ci(effectiveLine, effectiveLength, "subject:")) {
                            juno_pop3_extract_header(effectiveLine, effectiveLength, 8, "Subject: ",
                                                      headersBuffer, headersBufferLength, &headersWritten);
                          }
                          continue;
                        }
                        for (int32_t i = 0; i < effectiveLength; i++) {
                          if (bodyWritten < bodyBufferLength) bodyBuffer[bodyWritten] = static_cast<uint8_t>(effectiveLine[i]);
                          bodyWritten++;
                        }
                        if (bodyWritten < bodyBufferLength) bodyBuffer[bodyWritten] = '\\n';
                        bodyWritten++;
                      }

                      client.print("QUIT\\r\\n");
                      client.stop();
                      status[0] = headersWritten < headersBufferLength ? headersWritten : headersBufferLength;
                      return bodyWritten < bodyBufferLength ? bodyWritten : bodyBufferLength;
                    }

                    extern "C" int32_t juno_pop3_read_subject(const char* host, int32_t port,
                                                               const char* username, const char* password,
                                                               int32_t messageNumber,
                                                               uint8_t* subjectBuffer, int32_t subjectBufferLength) {
                      ${JUNO_TLS_CLIENT_DECLARATION}
                      if (!client.connect(host, static_cast<uint16_t>(port))) return -1;
                      unsigned long deadline = millis() + 15000;

                      int32_t loginResult = juno_pop3_login(client, username, password, deadline);
                      if (loginResult != 0) { client.stop(); return loginResult; }

                      client.print("TOP ");
                      client.print(messageNumber);
                      client.print(" 0\\r\\n");
                      if (!juno_pop3_expect_ok(client, deadline)) { client.stop(); return -5; }

                      bool foundSubject = false;
                      int32_t subjectWritten = 0;
                      char lineBuf[256];
                      while (true) {
                        int32_t lineLength = juno_read_line(client, lineBuf, sizeof(lineBuf), deadline);
                        if (lineLength < 0) { client.stop(); return -6; }
                        const char* effectiveLine = lineBuf;
                        int32_t effectiveLength = lineLength;
                        if (lineLength > 0 && lineBuf[0] == '.') {
                          effectiveLine = lineBuf + 1;
                          effectiveLength = lineLength - 1;
                          if (effectiveLength == 0) break;
                        }
                        if (!foundSubject && juno_starts_with_ci(effectiveLine, effectiveLength, "subject:")) {
                          foundSubject = true;
                          int32_t valueStart = 8;
                          if (valueStart < effectiveLength && effectiveLine[valueStart] == ' ') valueStart++;
                          for (int32_t i = valueStart; i < effectiveLength; i++) {
                            if (subjectWritten < subjectBufferLength) subjectBuffer[subjectWritten] = static_cast<uint8_t>(effectiveLine[i]);
                            subjectWritten++;
                          }
                        }
                      }

                      client.print("QUIT\\r\\n");
                      client.stop();
                      return subjectWritten < subjectBufferLength ? subjectWritten : subjectBufferLength;
                    }

                    """);
        }
        return helpers.toString().replace("${JUNO_TLS_CLIENT_DECLARATION}", tlsClientDeclaration);
    }
}
