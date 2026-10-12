
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
  slot[contentLength] = '\0';
  return static_cast<int32_t>(reinterpret_cast<intptr_t>(slot));
}

