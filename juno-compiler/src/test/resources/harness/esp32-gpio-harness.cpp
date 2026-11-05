#include <cassert>
#include <cstdint>

// Nano ESP32 variant pin definitions and remapping, in the core's two menu modes.
static constexpr int gpio[] = {
  44, 43, 5, 6, 7, 8, 9, 10, 17, 18, 21, 38, 47, 48,
  46, 0, 45, 1, 2, 3, 4, 11, 12, 13, 14
};
static constexpr int pinNumber(int index) { return GPIO_NUMBERS ? gpio[index] : index; }
static constexpr int D0 = pinNumber(0), D1 = pinNumber(1), D2 = pinNumber(2), D3 = pinNumber(3);
static constexpr int D4 = pinNumber(4), D5 = pinNumber(5), D6 = pinNumber(6), D7 = pinNumber(7);
static constexpr int D8 = pinNumber(8), D9 = pinNumber(9), D10 = pinNumber(10), D11 = pinNumber(11);
static constexpr int D12 = pinNumber(12), D13 = pinNumber(13);
static constexpr int A0 = pinNumber(17), A1 = pinNumber(18), A2 = pinNumber(19);
static constexpr int A3 = pinNumber(20), A4 = pinNumber(21), A5 = pinNumber(22);
static constexpr int INPUT = 0x01, OUTPUT = 0x03, INPUT_PULLUP = 0x05;
static int lastPin, lastValue;
static int toGpio(int pin) { return GPIO_NUMBERS ? pin : gpio[pin]; }
static void pinMode(int pin, int mode) { lastPin = toGpio(pin); lastValue = mode; }
static void digitalWrite(int pin, int value) { lastPin = toGpio(pin); lastValue = value; }
static int digitalRead(int pin) { lastPin = toGpio(pin); return 1; }
static int analogRead(int pin) { lastPin = toGpio(pin); return 1234; }
static void analogWrite(int pin, int value) { lastPin = toGpio(pin); lastValue = value; }

// ESP32_CORE_GLUE

int main() {
  const int expected[] = {44, 43, 5, 6, 7, 8, 9, 10, 17, 18, 21, 38, 47, 48, 1, 2, 3, 4, 11, 12};
  const int modes[] = {INPUT, OUTPUT, INPUT_PULLUP};
  for (int pin = 0; pin < 20; ++pin) {
    for (int mode = 0; mode < 3; ++mode) {
      juno_pin_mode(pin, mode);
      assert(lastPin == expected[pin] && lastValue == modes[mode]);
    }
    juno_digital_write(pin, 1);
    assert(lastPin == expected[pin] && lastValue == 1);
    juno_digital_write(pin, 0);
    assert(lastPin == expected[pin] && lastValue == 0);
    assert(juno_digital_read(pin) == 1 && lastPin == expected[pin]);
    assert(juno_analog_read(pin) == 1234 && lastPin == expected[pin]);
    juno_analog_write(pin, 127);
    assert(lastPin == expected[pin] && lastValue == 127);
  }
}
