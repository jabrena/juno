// Stand-in for WiFiNINA's WiFiUdp under simavr: a loopback, so every packet sent is the next one received, from
// 10.0.0.7 on the port it was sent to.
#pragma once

#include <stddef.h>
#include <stdint.h>
#include <string.h>

class IPAddress {
public:
  IPAddress(uint8_t a = 0, uint8_t b = 0, uint8_t c = 0, uint8_t d = 0) : octets_{a, b, c, d} {}
  uint8_t operator[](int index) const { return octets_[index]; }

private:
  uint8_t octets_[4];
};

class WiFiUDP {
public:
  uint8_t begin(uint16_t) { return 1; }
  uint8_t beginMulticast(IPAddress, uint16_t) { return 0; }
  void stop() {}
  int beginPacket(IPAddress, uint16_t port) {
    port_ = port;
    pending_ = 0;
    return 1;
  }
  size_t write(const uint8_t* data, size_t size) {
    size_t room = sizeof(buffer_) - pending_;
    size_t count = size < room ? size : room;
    memcpy(buffer_ + pending_, data, count);
    pending_ += count;
    return count;
  }
  int endPacket() {
    length_ = pending_;
    cursor_ = 0;
    return 1;
  }
  int parsePacket() {
    int length = static_cast<int>(length_);
    received_ = length_;
    length_ = 0;
    return length;
  }
  int available() { return static_cast<int>(received_ - cursor_); }
  int read() { return cursor_ < received_ ? buffer_[cursor_++] : -1; }
  int read(unsigned char* data, size_t size) {
    size_t count = 0;
    while (count < size && cursor_ < received_) data[count++] = buffer_[cursor_++];
    return static_cast<int>(count);
  }
  IPAddress remoteIP() { return IPAddress(10, 0, 0, 7); }
  uint16_t remotePort() { return port_; }

private:
  uint8_t buffer_[64];
  size_t pending_ = 0;
  size_t length_ = 0;
  size_t received_ = 0;
  size_t cursor_ = 0;
  uint16_t port_ = 0;
};
