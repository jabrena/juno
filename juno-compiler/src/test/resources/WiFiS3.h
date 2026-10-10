#pragma once

#include <stddef.h>
#include <stdint.h>
#include "WiFiUdp.h"

class WiFiClient {
public:
  bool connect(const char*, uint16_t) { return true; }
  void print(const char*) {}
  void print(char) {}
  void print(int) {}
  void print(unsigned long) {}
  size_t write(const uint8_t*, size_t size) { return size; }
  int available() { return 0; }
  bool connected() { return false; }
  int read() { return -1; }
  void stop() {}
  operator bool() const { return false; }
};

class WiFiServer {
public:
  explicit WiFiServer(uint16_t) {}
  void begin() {}
  WiFiClient available() { return WiFiClient(); }
};

struct JunoIPAddress {
  uint8_t operator[](int) const { return 0; }
};

struct JunoWiFi {
  void begin(const char*, const char*) {}
  uint8_t beginAP(const char*) { return 7; }
  uint8_t beginAP(const char*, const char*) { return 7; }
  int status() { return 3; }
  JunoIPAddress localIP() { return JunoIPAddress(); }
};

inline JunoWiFi WiFi;
