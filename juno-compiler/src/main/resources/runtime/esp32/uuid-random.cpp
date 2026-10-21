// The SDK's hardware random-number generator; it cannot fail, so the 16-byte request is the only check.
extern "C" void esp_fill_random(void* buffer, size_t length);

extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length) {
  if (length != 16u) return false;
  esp_fill_random(target, length);
  return true;
}
