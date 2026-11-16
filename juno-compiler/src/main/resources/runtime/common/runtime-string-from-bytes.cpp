// Bytes [start, end) as a new arena String, or null (0) for an invalid range.
extern "C" int32_t juno_string_from_bytes(const uint8_t* bytes, int32_t start, int32_t end) {
  int32_t length = end - start;
  if (bytes == nullptr || start < 0 || length < 0) return 0;
  return juno_string_copy(reinterpret_cast<const char*>(bytes + start), static_cast<uint32_t>(length));
}
