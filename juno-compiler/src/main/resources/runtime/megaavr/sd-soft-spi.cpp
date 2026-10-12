class JunoSdSoftSpiDriver : public SdSpiBaseClass {
 public:
  void begin(SdSpiConfig config) override {
    static_cast<void>(config);
    pinMode(12, INPUT);
    pinMode(11, OUTPUT);
    pinMode(13, OUTPUT);
    digitalWrite(11, HIGH);
    digitalWrite(13, LOW);
  }

  uint8_t receive() override { return transfer(0xFF); }

  uint8_t receive(uint8_t* buffer, size_t count) override {
    for (size_t i = 0; i < count; i++) buffer[i] = receive();
    return 0;
  }

  void send(uint8_t value) override { static_cast<void>(transfer(value)); }

  void send(const uint8_t* buffer, size_t count) override {
    for (size_t i = 0; i < count; i++) send(buffer[i]);
  }

 private:
  static uint8_t transfer(uint8_t output) {
    uint8_t input = 0;
    for (uint8_t mask = 0x80; mask != 0; mask >>= 1) {
      digitalWrite(11, (output & mask) != 0 ? HIGH : LOW);
      digitalWrite(13, HIGH);
      if (digitalRead(12) != LOW) input |= mask;
      digitalWrite(13, LOW);
    }
    return input;
  }
};

static JunoSdSoftSpiDriver juno_sd_spi;
