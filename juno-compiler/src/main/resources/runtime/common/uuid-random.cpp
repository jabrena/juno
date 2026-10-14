extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length);

static uint64_t juno_uuid_read_network_long(const uint8_t* source) {
  uint64_t value = 0;
  for (uint32_t index = 0; index < 8u; index++) {
    value = (value << 8) | source[index];
  }
  return value;
}

extern "C" int32_t juno_uuid_random() {
  uint8_t bytes[16];
  if (!juno_uuid_random_bytes(bytes, sizeof(bytes))) juno_panic();
  bytes[6] = static_cast<uint8_t>((bytes[6] & 0x0fu) | 0x40u);
  bytes[8] = static_cast<uint8_t>((bytes[8] & 0x3fu) | 0x80u);
  return juno_uuid_new(static_cast<int64_t>(juno_uuid_read_network_long(bytes)),
      static_cast<int64_t>(juno_uuid_read_network_long(bytes + 8)));
}
