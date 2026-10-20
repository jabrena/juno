// The WiFi Shield firmware reads each SPI command into a 100-byte buffer and drops a longer one as an overrun, which
// also breaks the socket for the next connection; a request line longer than about 90 bytes is sent in pieces.
class JunoShieldClient : public WiFiClient {
 public:
  using Print::write;

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
