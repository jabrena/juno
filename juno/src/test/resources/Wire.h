#pragma once

#include <stddef.h>
#include <stdint.h>

struct JunoWire {
  void begin() {}
  void beginTransmission(uint8_t) {}
  size_t write(uint8_t) { return 1; }
  uint8_t endTransmission(bool = true) { return 0; }
  uint8_t requestFrom(uint8_t, uint8_t count) { return count; }
  int read() { return 0; }
};

static JunoWire Wire;
