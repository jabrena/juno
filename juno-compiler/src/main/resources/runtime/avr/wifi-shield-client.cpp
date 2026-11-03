// The WiFi Shield firmware reads each SPI command into a 100-byte buffer and drops a longer one as an overrun, which
// also breaks the socket for the next connection; a request line longer than about 90 bytes is sent in pieces.
class JunoShieldClient : public WiFiClient {
 public:
  using Print::write;

  // The shield's bulk read can return short, so a reply is read one byte at a time, as before the bulk read.
  int read(uint8_t* buffer, size_t size) override {
    size_t got = 0;
    while (got < size && WiFiClient::available() > 0) {
      int value = WiFiClient::read();
      if (value < 0) break;
      buffer[got++] = static_cast<uint8_t>(value);
    }
    return static_cast<int>(got);
  }

  size_t write(const uint8_t* buffer, size_t size) override {
    size_t sent = 0;
    while (sent < size) {
      size_t piece = size - sent < 64 ? size - sent : 64;
      if (WiFiClient::write(buffer + sent, piece) != piece) return sent;
      sent += piece;
    }
    return sent;
  }
};
