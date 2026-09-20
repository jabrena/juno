#pragma once

#include <stdint.h>

class WiFiClient {
public:
  bool connect(const char*, uint16_t) { return true; }
  void print(const char*) {}
  void print(char) {}
  void print(unsigned long) {}
  int available() { return 0; }
  bool connected() { return false; }
  int read() { return -1; }
  void stop() {}
};

struct JunoWiFi {
  void begin(const char*, const char*) {}
  int status() { return 3; }
};

inline JunoWiFi WiFi;
