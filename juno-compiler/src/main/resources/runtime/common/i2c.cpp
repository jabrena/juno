
extern "C" void juno_i2c_begin() {
  ${JUNO_I2C_PIN_SETUP}${JUNO_I2C_BUS}.begin();
}

extern "C" void juno_i2c_write_register(int32_t address, int32_t reg, int32_t value) {
  ${JUNO_I2C_BUS}.beginTransmission(static_cast<uint8_t>(address));
  ${JUNO_I2C_BUS}.write(static_cast<uint8_t>(reg));
  ${JUNO_I2C_BUS}.write(static_cast<uint8_t>(value));
  ${JUNO_I2C_BUS}.endTransmission();
}

// Reads count (1 or 2) bytes from reg, little endian; -1 if the device did not answer.
static int32_t juno_i2c_read(int32_t address, int32_t reg, uint8_t count) {
  ${JUNO_I2C_BUS}.beginTransmission(static_cast<uint8_t>(address));
  ${JUNO_I2C_BUS}.write(static_cast<uint8_t>(reg));
  if (${JUNO_I2C_BUS}.endTransmission(false) != 0) {
    return -1;
  }
  if (${JUNO_I2C_BUS}.requestFrom(static_cast<uint8_t>(address), count) != count) {
    return -1;
  }
  int32_t value = 0;
  for (uint8_t i = 0; i < count; i++) {
    value |= static_cast<int32_t>(${JUNO_I2C_BUS}.read() & 0xFF) << (8 * i);
  }
  return value;
}

// Raw transfer: sends length bytes with no register address; length 0 only probes the address. Returns length, or -1
// when the device did not acknowledge.
extern "C" int32_t juno_i2c_write(int32_t address, int8_t* data, int32_t length) {
  ${JUNO_I2C_BUS}.beginTransmission(static_cast<uint8_t>(address));
  for (int32_t i = 0; i < length; i++) {
    ${JUNO_I2C_BUS}.write(static_cast<uint8_t>(data[i]));
  }
  return ${JUNO_I2C_BUS}.endTransmission() == 0 ? length : -1;
}

// Raw read: fills length bytes of buffer from the device; -1 if it did not answer, leaving buffer untouched.
extern "C" int32_t juno_i2c_read(int32_t address, int8_t* buffer, int32_t length) {
  if (${JUNO_I2C_BUS}.requestFrom(static_cast<uint8_t>(address), static_cast<uint8_t>(length)) != length) {
    return -1;
  }
  for (int32_t i = 0; i < length; i++) {
    buffer[i] = static_cast<int8_t>(${JUNO_I2C_BUS}.read());
  }
  return length;
}

extern "C" int32_t juno_i2c_read_register(int32_t address, int32_t reg) {
  return juno_i2c_read(address, reg, 1);
}

extern "C" int32_t juno_i2c_read_register16(int32_t address, int32_t reg) {
  return juno_i2c_read(address, reg, 2);
}
