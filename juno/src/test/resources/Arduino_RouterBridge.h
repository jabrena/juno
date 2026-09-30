#pragma once

#include <stddef.h>
#include "WiFiS3.h"

class BridgeClass {
public:
  bool begin() { return true; }
  operator bool() const { return true; }
};

inline BridgeClass Bridge;

template<size_t BufferSize>
class BridgeUDP : public WiFiUDP {
public:
  explicit BridgeUDP(BridgeClass&) {}
};

template<size_t BufferSize>
class BridgeTCPClient : public WiFiClient {
public:
  explicit BridgeTCPClient(BridgeClass&) {}
  bool begin() { return true; }
  int connectSSL(const char*, uint16_t, const char*) { return 0; }
};
