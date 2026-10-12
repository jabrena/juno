
extern "C" void juno_i2c_begin() {
  Wire.begin();
}

extern "C" void juno_i2c_write_register(int32_t address, int32_t reg, int32_t value) {
  Wire.beginTransmission(static_cast<uint8_t>(address));
  Wire.write(static_cast<uint8_t>(reg));
  Wire.write(static_cast<uint8_t>(value));
  Wire.endTransmission();
}

// Reads count (1 or 2) bytes from reg, little endian; -1 if the device did not answer.
static int32_t juno_i2c_read(int32_t address, int32_t reg, uint8_t count) {
  Wire.beginTransmission(static_cast<uint8_t>(address));
  Wire.write(static_cast<uint8_t>(reg));
  if (Wire.endTransmission(false) != 0) {
    return -1;
  }
  if (Wire.requestFrom(static_cast<uint8_t>(address), count) != count) {
    return -1;
  }
  int32_t value = 0;
  for (uint8_t i = 0; i < count; i++) {
    value |= static_cast<int32_t>(Wire.read() & 0xFF) << (8 * i);
  }
  return value;
}

extern "C" int32_t juno_i2c_read_register(int32_t address, int32_t reg) {
  return juno_i2c_read(address, reg, 1);
}

extern "C" int32_t juno_i2c_read_register16(int32_t address, int32_t reg) {
  return juno_i2c_read(address, reg, 2);
}
