#pragma once

#include <stddef.h>
#include <stdint.h>

class IPAddress {
public:
  IPAddress(uint8_t = 0, uint8_t = 0, uint8_t = 0, uint8_t = 0) {}
  uint8_t operator[](int) const { return 0; }
};

class WiFiUDP {
public:
  uint8_t begin(uint16_t) { return 1; }
  uint8_t beginMulticast(IPAddress, uint16_t) { return 1; }
  void stop() {}
  int beginPacket(IPAddress, uint16_t) { return 1; }
  int endPacket() { return 1; }
  size_t write(const uint8_t*, size_t size) { return size; }
  int parsePacket() { return 0; }
  int available() { return 0; }
  int read() { return -1; }
  int read(uint8_t*, size_t) { return 0; }
  IPAddress remoteIP() { return IPAddress(); }
  uint16_t remotePort() { return 0; }
};
