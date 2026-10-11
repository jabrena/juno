// Stand-in for WiFiNINA under simavr: Wi-Fi is always connected, and a WiFiClient (or a WiFiSSLClient, whose TLS
// the real NINA module runs) connecting to any host receives one canned, chunked HTTP/1.1 response, so the shim's
// HTTP codec runs end to end on the 8-bit target. A WiFiSSLClient only accepts port 443, so a test can tell the
// two apart.
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <string.h>
#include "WiFiUdp.h"

#define WL_CONNECTED 3

static const char juno_harness_http_response[] =
    "HTTP/1.1 201 Created\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n"
    "11\r\n{\"temp\":21.5,\"ok\"\r\na\r\n:true,\"n\":\r\n4\r\n-42}\r\n0\r\n\r\n";

class WiFiClient {
public:
  int connect(const char* host, uint16_t port) {
    cursor_ = 0;
    connected_ = host != nullptr && port != 0;
    return connected_ ? 1 : 0;
  }
  size_t print(const char*) { return 0; }
  size_t print(char) { return 0; }
  size_t print(int) { return 0; }
  size_t print(unsigned long) { return 0; }
  size_t write(const uint8_t*, size_t size) { return size; }
  int available() { return connected_ && cursor_ < sizeof(juno_harness_http_response) - 1 ? 1 : 0; }
  uint8_t connected() { return available() != 0 ? 1 : 0; }
  int read() { return available() != 0 ? static_cast<uint8_t>(juno_harness_http_response[cursor_++]) : -1; }
  void stop() { connected_ = false; }
  operator bool() { return connected_; }

private:
  size_t cursor_ = 0;
  bool connected_ = false;
};

class WiFiSSLClient : public WiFiClient {
public:
  int connect(const char* host, uint16_t port) { return port == 443 ? WiFiClient::connect(host, port) : 0; }
};

class WiFiServer {
public:
  explicit WiFiServer(uint16_t) {}
  void begin() {}
  WiFiClient available() { return WiFiClient(); }
};

struct JunoHarnessWiFi {
  int begin(const char*, const char*) { return WL_CONNECTED; }
  uint8_t beginAP(const char*) { return 7; }
  uint8_t beginAP(const char*, const char*) { return 7; }
  uint8_t status() { return WL_CONNECTED; }
  IPAddress localIP() { return IPAddress(192, 168, 1, 45); }
};

extern JunoHarnessWiFi WiFi;
