// The ESP32 core provides yield(), delay() and delayMicroseconds() as plain C symbols the
// generated assembly calls directly. The Nano ESP32 builds with BOARD_HAS_PIN_REMAP, where
// pinMode()/digitalWrite()/... are macros mapping the printed pin number to its GPIO; these
// wrappers are compiled with those macros in effect, so Java code keeps the D-pin numbers.
// Juno passes the classic Arduino modes (0 INPUT, 1 OUTPUT, 2 INPUT_PULLUP); the ESP32 core's
// constants are bit masks (INPUT 0x01, OUTPUT 0x03, INPUT_PULLUP 0x05), so 1 would mean INPUT.
extern "C" void juno_pin_mode(int32_t pin, int32_t mode) {
  pinMode(pin, mode == 1 ? OUTPUT : mode == 2 ? INPUT_PULLUP : INPUT);
}

extern "C" void juno_digital_write(int32_t pin, int32_t value) {
  digitalWrite(pin, value);
}

extern "C" int32_t juno_digital_read(int32_t pin) {
  return digitalRead(pin);
}

extern "C" int32_t juno_analog_read(int32_t pin) {
  return analogRead(pin);
}

extern "C" void juno_analog_write(int32_t pin, int32_t value) {
  analogWrite(pin, value);
}

