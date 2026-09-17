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
