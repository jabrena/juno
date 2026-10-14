#include <Wire.h>

static constexpr uint8_t JUNO_ATECC608_ADDRESS = 0x60u;
static constexpr uint8_t JUNO_ATECC608_RANDOM = 0x1bu;

static uint16_t juno_atecc608_crc(const uint8_t* data, uint8_t length) {
  uint16_t crc = 0;
  for (uint8_t index = 0; index < length; index++) {
    uint8_t mask = 1u;
    for (uint8_t bit = 0; bit < 8u; bit++) {
      bool dataBit = (data[index] & mask) != 0;
      bool crcBit = (crc & 0x8000u) != 0;
      crc <<= 1;
      if (dataBit != crcBit) crc ^= 0x8005u;
      mask <<= 1;
    }
  }
  return crc;
}

static bool juno_atecc608_read(uint8_t* response, uint8_t length) {
  if (Wire.requestFrom(static_cast<uint8_t>(JUNO_ATECC608_ADDRESS), static_cast<size_t>(length)) != length) {
    return false;
  }
  for (uint8_t index = 0; index < length; index++) {
    if (Wire.available() == 0) return false;
    response[index] = static_cast<uint8_t>(Wire.read());
  }
  uint16_t expected = static_cast<uint16_t>(response[length - 2u])
      | (static_cast<uint16_t>(response[length - 1u]) << 8);
  return juno_atecc608_crc(response, static_cast<uint8_t>(length - 2u)) == expected;
}

extern "C" bool juno_uuid_random_bytes(uint8_t* target, uint32_t length) {
  if (length != 16u) return false;
  Wire.begin();
  Wire.beginTransmission(static_cast<uint8_t>(0));
  Wire.endTransmission();
  delay(3u);
  uint8_t wake[4];
  if (!juno_atecc608_read(wake, sizeof(wake))) return false;

  uint8_t command[7] = {7u, JUNO_ATECC608_RANDOM, 0u, 0u, 0u, 0u, 0u};
  uint16_t commandCrc = juno_atecc608_crc(command, 5u);
  command[5] = static_cast<uint8_t>(commandCrc);
  command[6] = static_cast<uint8_t>(commandCrc >> 8);
  Wire.beginTransmission(static_cast<uint8_t>(JUNO_ATECC608_ADDRESS));
  Wire.write(command, sizeof(command));
  if (Wire.endTransmission() != 0) return false;
  delay(25u);
  uint8_t response[35];
  if (!juno_atecc608_read(response, sizeof(response)) || response[0] != sizeof(response)) return false;
  for (uint8_t index = 0; index < length; index++) {
    target[index] = response[index + 1u];
  }
  Wire.beginTransmission(static_cast<uint8_t>(JUNO_ATECC608_ADDRESS));
  Wire.write(static_cast<uint8_t>(0x01u));
  Wire.endTransmission();
  return true;
}
