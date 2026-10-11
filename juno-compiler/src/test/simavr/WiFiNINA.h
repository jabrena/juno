// Stand-in for WiFiNINA under simavr: Wi-Fi is always connected, and the clients talk to small fake servers instead
// of a network, so the shim's protocol code runs end to end on the 8-bit target:
// - a WiFiClient (any port), or a WiFiSSLClient on port 443 (the real NINA module runs the TLS), receives one canned,
//   chunked HTTP/1.1 response;
// - a WiFiSSLClient on port 465 talks to an SMTP server that accepts AUTH LOGIN only for rev2@example.com / secret,
//   so a wrong base64 encoding fails the login;
// - a WiFiSSLClient on port 995 talks to a POP3 server holding two messages.
// Any other WiFiSSLClient port refuses the connection.
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
    if (host == nullptr || port == 0) return 0;
    connected_ = true;
    line_[0] = '\0';
    lineLength_ = 0;
    state_ = 0;
    reply(greeting(port));
    return 1;
  }
  size_t print(const char* text) {
    size_t length = strlen(text);
    for (size_t i = 0; i < length; i++) receive(text[i]);
    return length;
  }
  size_t print(char value) {
    receive(value);
    return 1;
  }
  size_t print(unsigned long value) {
    char digits[11];
    char* end = digits + sizeof(digits) - 1;
    *end = '\0';
    do {
      *--end = static_cast<char>('0' + value % 10u);
      value /= 10u;
    } while (value != 0u);
    return print(end);
  }
  size_t print(long value) {
    if (value < 0) return print('-') + print(0ul - static_cast<unsigned long>(value));
    return print(static_cast<unsigned long>(value));
  }
  size_t print(int value) { return print(static_cast<long>(value)); }
  size_t write(const uint8_t*, size_t size) { return size; }
  int available() { return connected_ && cursor_ < length_ ? 1 : 0; }
  uint8_t connected() { return connected_ && (available() != 0 || stayOpen()) ? 1 : 0; }
  int read() { return available() != 0 ? static_cast<uint8_t>(reply_[cursor_++]) : -1; }
  void stop() { connected_ = false; }
  operator bool() { return connected_; }

protected:
  // The server's first words; a plain client always gets the HTTP response.
  virtual const char* greeting(uint16_t) { return juno_harness_http_response; }
  // The server's answer to one line the client sent, or nullptr for none.
  virtual const char* answer(const char*) { return nullptr; }
  // Whether the connection stays open once the client has read everything (true for line protocols).
  virtual bool stayOpen() { return false; }

  int state_ = 0;

private:
  void reply(const char* text) {
    if (cursor_ >= length_) {
      cursor_ = 0;
      length_ = 0;
    }
    size_t count = strlen(text);
    if (length_ + count > sizeof(reply_)) count = sizeof(reply_) - length_;
    memcpy(reply_ + length_, text, count);
    length_ += count;
  }
  void receive(char c) {
    if (c == '\r') return;
    if (c != '\n') {
      if (lineLength_ + 1 < sizeof(line_)) line_[lineLength_++] = c;
      return;
    }
    line_[lineLength_] = '\0';
    lineLength_ = 0;
    const char* text = answer(line_);
    if (text != nullptr) reply(text);
  }

  char reply_[200];
  size_t length_ = 0;
  size_t cursor_ = 0;
  char line_[64];
  size_t lineLength_ = 0;
  bool connected_ = false;
};

class WiFiSSLClient : public WiFiClient {
public:
  int connect(const char* host, uint16_t port) {
    if (port != 443 && port != 465 && port != 995) return 0;
    port_ = port;
    return WiFiClient::connect(host, port);
  }

protected:
  const char* greeting(uint16_t port) override {
    if (port == 465) return "220 smtp.example.com ready\r\n";
    if (port == 995) return "+OK POP3 ready\r\n";
    return WiFiClient::greeting(port);
  }

  bool stayOpen() override { return port_ != 443; }

  const char* answer(const char* line) override {
    return port_ == 465 ? smtp(line) : port_ == 995 ? pop3(line) : nullptr;
  }

private:
  static bool starts(const char* line, const char* prefix) { return strncmp(line, prefix, strlen(prefix)) == 0; }

  // state_: 0 commands, 1 expecting the base64 user, 2 expecting the base64 password, 3 message data.
  const char* smtp(const char* line) {
    if (state_ == 1) {
      state_ = 2;
      return strcmp(line, "cmV2MkBleGFtcGxlLmNvbQ==") == 0 ? "334 UGFzc3dvcmQ6\r\n" : "535 unknown user\r\n";
    }
    if (state_ == 2) {
      state_ = 0;
      return strcmp(line, "c2VjcmV0") == 0 ? "235 authenticated\r\n" : "535 bad password\r\n";
    }
    if (state_ == 3) {
      if (strcmp(line, ".") != 0) return nullptr;
      state_ = 0;
      return "250 queued\r\n";
    }
    if (starts(line, "EHLO ")) return "250-smtp.example.com\r\n250 AUTH LOGIN\r\n";
    if (strcmp(line, "AUTH LOGIN") == 0) {
      state_ = 1;
      return "334 VXNlcm5hbWU6\r\n";
    }
    if (starts(line, "MAIL FROM:<") || starts(line, "RCPT TO:<")) return "250 ok\r\n";
    if (strcmp(line, "DATA") == 0) {
      state_ = 3;
      return "354 end with .\r\n";
    }
    if (strcmp(line, "QUIT") == 0) return "221 bye\r\n";
    return "500 unknown command\r\n";
  }

  const char* pop3(const char* line) {
    if (starts(line, "USER ")) return "+OK\r\n";
    if (starts(line, "PASS ")) return strcmp(line, "PASS secret") == 0 ? "+OK 2 messages\r\n" : "-ERR denied\r\n";
    if (strcmp(line, "STAT") == 0) return "+OK 2 320\r\n";
    if (strcmp(line, "RETR 2") == 0) {
      return "+OK message follows\r\nFrom: Alice <alice@example.com>\r\nSubject: Hello Rev2\r\n\r\n"
             "Line one\r\n..dot line\r\n.\r\n";
    }
    if (strcmp(line, "TOP 1 0") == 0) return "+OK\r\nFrom: Bob\r\nSubject: First\r\n\r\n.\r\n";
    if (strcmp(line, "QUIT") == 0) return "+OK bye\r\n";
    return "-ERR unknown command\r\n";
  }

  uint16_t port_ = 0;
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
