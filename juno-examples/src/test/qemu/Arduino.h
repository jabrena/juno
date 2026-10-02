// Stand-in for the Arduino core, just enough for the generated runtime shim of Serial-only programs.
// Serial goes to QEMU's semihosting console; noInterrupts() is reached only by juno_panic(), so it reports
// the panic and stops the machine with a failing status.
#pragma once

#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

extern "C" void juno_harness_write(const char* text);
extern "C" [[noreturn]] void juno_harness_exit(int code);

constexpr int LOW = 0;
constexpr int HIGH = 1;
constexpr int INPUT = 0;
constexpr int OUTPUT = 1;
constexpr int INPUT_PULLUP = 2;

[[noreturn]] inline void noInterrupts() {
  juno_harness_write("[juno-panic]\n");
  juno_harness_exit(3);
}
inline void pinMode(int32_t, int32_t) {}
inline void digitalWrite(int32_t, int32_t) {}
inline int32_t digitalRead(int32_t) { return LOW; }
inline int32_t analogRead(int32_t) { return 0; }
inline void analogWrite(int32_t, int32_t) {}
// A virtual clock: delay() advances by its requested duration, and the Serial service hook below advances on
// yield() so tight loop backedges model hardware time progressing without introducing a host-time dependency.
inline unsigned long juno_harness_clock = 0;
inline void delay(unsigned long ms) { juno_harness_clock += ms; }
inline void delayMicroseconds(unsigned int) {}
inline unsigned long millis() { return juno_harness_clock; }
inline unsigned long micros() { return 0; }
inline void randomSeed(unsigned long) {}
inline long random(long bound) { return bound > 0 ? bound - 1 : 0; }
inline long random(long origin, long bound) { return bound > origin ? bound - 1 : origin; }

struct HarnessSerial {
  void begin(unsigned long) {}
  void print(const char* text) { juno_harness_write(text); }
  void print(unsigned long long value) {
    char digits[24];
    char* end = digits + sizeof(digits) - 1;
    *end = '\0';
    do {
      *--end = static_cast<char>('0' + value % 10u);
      value /= 10u;
    } while (value != 0u);
    juno_harness_write(end);
  }
  void print(long long value) {
    if (value < 0) {
      juno_harness_write("-");
      print(0ull - static_cast<unsigned long long>(value));
    } else {
      print(static_cast<unsigned long long>(value));
    }
  }
  void print(int value) { print(static_cast<long long>(value)); }
  void print(long value) { print(static_cast<long long>(value)); }
  void print(unsigned value) { print(static_cast<unsigned long long>(value)); }
  void print(unsigned long value) { print(static_cast<unsigned long long>(value)); }
  // Arduino prints two decimals by default.
  void print(double value) {
    if (value < 0) {
      juno_harness_write("-");
      value = -value;
    }
    unsigned long long hundredths = static_cast<unsigned long long>(value * 100.0 + 0.5);
    print(hundredths / 100u);
    juno_harness_write(".");
    if (hundredths % 100u < 10u) juno_harness_write("0");
    print(hundredths % 100u);
  }
  void println() { juno_harness_write("\n"); }
  template <typename T> void println(T value) {
    print(value);
    println();
  }
  explicit operator bool() const {
    juno_harness_clock++;
    return true;
  }
};
inline HarnessSerial Serial;
