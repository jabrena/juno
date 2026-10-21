// The ESP32 core provides yield(), delay() and delayMicroseconds() as plain C symbols the
// generated assembly calls directly. The Nano ESP32 builds with BOARD_HAS_PIN_REMAP, where
// pinMode()/digitalWrite()/... are macros mapping the printed pin number to its GPIO; these
// wrappers are compiled with those macros in effect, so Java code keeps the D-pin numbers.
// Juno passes the classic Arduino modes (0 INPUT, 1 OUTPUT, 2 INPUT_PULLUP); the ESP32 core's
// constants are bit masks (INPUT 0x01, OUTPUT 0x03, INPUT_PULLUP 0x05), so 1 would mean INPUT.
// Juno's pin identifiers are D0-D13 (0-13), then A0-A5 (14-19). Use the variant's named pins:
// its Arduino numbering reserves 14-16 for RGB LEDs, and its GPIO numbering is not contiguous.
// The constants also keep the same physical pins when the core's pin-numbering menu changes.
static int32_t juno_nano_pin(int32_t pin) {
  static constexpr uint8_t pins[] = {
    D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, D10, D11, D12, D13,
    A0, A1, A2, A3, A4, A5
  };
  return pin >= 0 && pin < 20 ? pins[pin] : pin;
}

extern "C" void juno_pin_mode(int32_t pin, int32_t mode) {
  pinMode(juno_nano_pin(pin), mode == 1 ? OUTPUT : mode == 2 ? INPUT_PULLUP : INPUT);
}

extern "C" void juno_digital_write(int32_t pin, int32_t value) {
  digitalWrite(juno_nano_pin(pin), value);
}

extern "C" int32_t juno_digital_read(int32_t pin) {
  return digitalRead(juno_nano_pin(pin));
}

extern "C" int32_t juno_analog_read(int32_t pin) {
  return analogRead(juno_nano_pin(pin));
}

extern "C" void juno_analog_write(int32_t pin, int32_t value) {
  analogWrite(juno_nano_pin(pin), value);
}

