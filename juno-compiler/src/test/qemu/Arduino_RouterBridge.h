// Stand-in for Arduino_RouterBridge under QEMU: there is no Linux side, so a fake one answers, slowly. Starting the
// bridge takes JUNO_HARNESS_BRIDGE_START_MILLIS and opening a (TLS) connection JUNO_HARNESS_BRIDGE_CONNECT_MILLIS,
// both waited for inside the library the way the real one waits for an RPC reply: by polling with delay(1) in the
// calling thread. A test can then see whether the other threads run meanwhile.
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <string.h>

static constexpr unsigned long JUNO_HARNESS_BRIDGE_START_MILLIS = 30;
static constexpr unsigned long JUNO_HARNESS_BRIDGE_CONNECT_MILLIS = 40;

static const char juno_harness_bridge_response[] =
    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 11\r\n\r\nhello juno!";

struct IPAddress {
  uint8_t octets[4];
  IPAddress(uint8_t a, uint8_t b, uint8_t c, uint8_t d) : octets{a, b, c, d} {}
  uint8_t operator[](int index) const { return octets[index]; }
};

class BridgeClass {
public:
  bool begin() {
    unsigned long started = millis();
    while (millis() - started < JUNO_HARNESS_BRIDGE_START_MILLIS) delay(1);
    started_ = true;
    return true;
  }
  operator bool() const { return started_; }

private:
  bool started_ = false;
};

inline BridgeClass Bridge;

template <size_t BufferSize>
class BridgeTCPClient {
public:
  explicit BridgeTCPClient(BridgeClass&) {}
  bool begin() { return true; }
  int connect(const char* host, uint16_t port) { return connectSSL(host, port, "") == 0 ? 1 : 0; }
  // 0 for success, as the real BridgeTCPClient.
  int connectSSL(const char* host, uint16_t port, const char*) {
    if (host == nullptr || port == 0) return 1;
    unsigned long started = millis();
    while (millis() - started < JUNO_HARNESS_BRIDGE_CONNECT_MILLIS) delay(1);
    connected_ = true;
    cursor_ = 0;
    return 0;
  }
  template <typename T>
  size_t print(T) { return 1; }
  size_t write(const uint8_t*, size_t size) { return size; }
  int available() {
    if (!connected_) return 0;
    return cursor_ < sizeof(juno_harness_bridge_response) - 1u ? 1 : 0;
  }
  bool connected() { return connected_ && cursor_ < sizeof(juno_harness_bridge_response) - 1u; }
  int read() { return available() != 0 ? static_cast<uint8_t>(juno_harness_bridge_response[cursor_++]) : -1; }
  void stop() { connected_ = false; }

private:
  bool connected_ = false;
  size_t cursor_ = 0;
};

template <size_t BufferSize>
class BridgeTCPServer {
public:
  BridgeTCPServer(BridgeClass&, const IPAddress&, uint16_t) {}
  void begin() {}
  BridgeTCPClient<BufferSize> accept() { return BridgeTCPClient<BufferSize>(Bridge); }
  void disconnect() {}
};
