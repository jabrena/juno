#pragma once

#include <stdint.h>

constexpr int LOW = 0;
constexpr int HIGH = 1;
constexpr int INPUT = 0;
constexpr int OUTPUT = 1;
constexpr int INPUT_PULLUP = 2;

inline void noInterrupts() {}
inline void pinMode(int32_t, int32_t) {}
inline void digitalWrite(int32_t, int32_t) {}
inline int32_t digitalRead(int32_t) { return LOW; }
inline int32_t analogRead(int32_t) { return 0; }
inline void analogWrite(int32_t, int32_t) {}
inline void delay(unsigned long) {}
inline void delayMicroseconds(unsigned int) {}
inline unsigned long millis() { return 0; }
inline unsigned long micros() { return 0; }
inline void randomSeed(unsigned long) {}
inline long random(long bound) { return bound > 0 ? bound - 1 : 0; }
inline long random(long origin, long bound) { return bound > origin ? bound - 1 : origin; }

struct JunoSerial {
  void begin(unsigned long) {}
  void println() {}
  void print(int32_t) {}
  void println(int32_t) {}
  void print(uint32_t) {}
  void println(uint32_t) {}
  void print(unsigned long) {}
  void println(unsigned long) {}
  void print(long long) {}
  void println(long long) {}
  void print(double) {}
  void println(double) {}
  void print(const char*) {}
  void println(const char*) {}
  explicit operator bool() const { return true; }
};
inline JunoSerial Serial;

// The renesas_uno core's pin table and RA port registers, which the ParallelBus helpers write directly.
typedef uint16_t bsp_io_port_pin_t;
struct PinMuxCfg_t {
  bsp_io_port_pin_t pin;
};
extern const PinMuxCfg_t g_pin_cfg[];
struct R_PORT0_Type {
  volatile uint32_t PCNTR1;
  volatile uint32_t PCNTR2;
  volatile uint32_t PCNTR3;
};
#define R_PORT0_BASE 0x40040000UL
#define R_PORT1_BASE 0x40040020UL
