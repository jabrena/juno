// Bare-metal harness: runs the generated entry point (JUNO_ENTRY, the function the .ino wrapper calls from setup())
// and stops the simulation, which simavr does once the CPU sleeps with interrupts disabled.
#include "Arduino.h"
#include "SdFat.h"
#include "WiFiNINA.h"

unsigned long juno_harness_clock = 0;
volatile unsigned long juno_harness_ticks = 0;

// Timer0 overflows every 1024 us at 16 MHz with a /64 prescaler, the real core's millis() tick.
ISR(TIMER0_OVF_vect) { juno_harness_ticks++; }
HarnessSerial Serial;
JunoHarnessWiFi WiFi;
JunoHarnessFile juno_harness_files[4] = {{"config.txt", "ssid=juno\nport = 8080\n# comment\nname:Rev2\n", 42, true}};
PORT_t juno_harness_ports[6];

extern "C" {
void JUNO_ENTRY(void);

void juno_harness_write(const char* text) {
  for (; *text != '\0'; text++) {
    while ((UCSR0A & (1 << UDRE0)) == 0) {
    }
    UDR0 = static_cast<uint8_t>(*text);
  }
}

[[noreturn]] void juno_harness_exit(void) {
  cli();
  sleep_enable();
  for (;;) {
    sleep_cpu();
  }
}

// The core's weak yield(); like the QEMU harness, it lets virtual time pass.
void yield(void) { juno_harness_clock++; }
}

int main() {
  UCSR0B = 1 << TXEN0;
  TCCR0B = (1 << CS01) | (1 << CS00);
  TIMSK0 = 1 << TOIE0;
  sei();
  JUNO_ENTRY();
  juno_harness_write("[juno-exit]\n");
  juno_harness_exit();
}
