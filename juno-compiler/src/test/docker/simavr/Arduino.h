// Stand-in for the arduino:megaavr core, just enough for the generated runtime shim of Serial-only programs, built
// for simavr's ATmega2560 (the same AVR instruction set the UNO WiFi Rev2's ATmega4809 runs, with more RAM to spare).
// Serial goes to USART0, which the runner copies to stdout; noInterrupts() is reached only by juno_panic(), so it
// reports the panic and stops the simulation.
#pragma once

#include <avr/interrupt.h>
#include <avr/io.h>
#include <avr/sleep.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

extern "C" void juno_harness_write(const char* text);
extern "C" [[noreturn]] void juno_harness_exit(void);

// ArduinoCore-API's enums, which megaavr's pin functions take.
typedef enum { LOW = 0, HIGH = 1 } PinStatus;
typedef enum { INPUT = 0, OUTPUT = 1, INPUT_PULLUP = 2 } PinMode;
#define LED_BUILTIN 25

[[noreturn]] inline void noInterrupts() {
  juno_harness_write("[juno-panic]\n");
  juno_harness_exit();
}
inline void pinMode(uint8_t, PinMode) {}
inline void digitalWrite(uint8_t, PinStatus) {}
inline PinStatus digitalRead(uint8_t) { return LOW; }
inline int analogRead(uint8_t) { return 0; }
inline void analogWrite(uint8_t, int) {}
// A virtual clock, as in the QEMU harness: delay() advances it, and so does yield() on every loop backedge. Like the
// real core's, millis() also counts Timer0 overflows (about one per millisecond of simulated CPU time, see
// startup.cpp), so the time slice of a thread that never yields still runs out, deterministically under simavr.
extern unsigned long juno_harness_clock;
extern volatile unsigned long juno_harness_ticks;
inline void delay(unsigned long ms) { juno_harness_clock += ms; }
inline void delayMicroseconds(unsigned int) {}
inline unsigned long millis() {
  uint8_t status = SREG;
  cli();
  unsigned long ticks = juno_harness_ticks;
  SREG = status;
  return juno_harness_clock + ticks;
}
inline unsigned long micros() { return 0; }
inline void randomSeed(unsigned long) {}
inline long random(long bound) { return bound > 0 ? bound - 1 : 0; }
inline long random(long origin, long bound) { return bound > origin ? bound - 1 : origin; }
extern "C" void yield(void);

// megaAVR 0-series ports, which ParallelBus writes through OUTSET/OUTCLR.
struct PORT_t {
  volatile uint8_t OUTSET;
  volatile uint8_t OUTCLR;
};
extern PORT_t juno_harness_ports[6];
#define digitalPinToPortStruct(pin) (&juno_harness_ports[(pin) % 6])
#define digitalPinToBitMask(pin) (static_cast<uint8_t>(1u << ((pin) % 8)))

struct HarnessSerial {
  void begin(unsigned long) {}
  void print(const char* text) { juno_harness_write(text); }
  void print(char value) {
    char text[2] = {value, '\0'};
    juno_harness_write(text);
  }
  void print(unsigned long value) {
    char digits[11];
    char* end = digits + sizeof(digits) - 1;
    *end = '\0';
    do {
      *--end = static_cast<char>('0' + value % 10u);
      value /= 10u;
    } while (value != 0u);
    juno_harness_write(end);
  }
  void print(long value) {
    if (value < 0) {
      juno_harness_write("-");
      print(0ul - static_cast<unsigned long>(value));
    } else {
      print(static_cast<unsigned long>(value));
    }
  }
  void print(int value) { print(static_cast<long>(value)); }
  void print(unsigned value) { print(static_cast<unsigned long>(value)); }
  void println() { juno_harness_write("\n"); }
  template <typename T> void println(T value) {
    print(value);
    println();
  }
};
extern HarnessSerial Serial;
