// The WiFi Shield's WiFiUDP: it has no multicast, so juno_udp_listen falls back to a plain socket, which receives the
// same broadcast and unicast packets. Each write is one SPI command the firmware reads into a 100-byte buffer, so a
// payload goes over in pieces; the firmware stores them and sends them as one datagram on endPacket.
class JunoShieldUdp : public WiFiUDP {
 public:
  using Print::write;

  uint8_t beginMulticast(IPAddress, uint16_t) {
    return 0;
  }

  size_t write(const uint8_t* buffer, size_t size) override {
    size_t sent = 0;
    while (sent < size) {
      size_t piece = size - sent < 64 ? size - sent : 64;
      WiFiUDP::write(buffer + sent, piece);
      sent += piece;
    }
    return sent;
  }
};
