// The ESP32 core's yield() is weak. Override it only in @Watchdog sketches, feed the task watchdog, then retain the
// core scheduler handoff through its strong __yield() implementation.
extern "C" void __yield();

extern "C" void yield() {
  ${JUNO_WATCHDOG_REFRESH}
  __yield();
}
