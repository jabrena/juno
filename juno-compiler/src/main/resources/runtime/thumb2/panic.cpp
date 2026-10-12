
${JUNO_YIELD_FUNCTION}extern "C" [[noreturn]] void juno_panic() {
  // The watchdog diagnostic (if any) must print before noInterrupts(): USB CDC
  // Serial relies on interrupts/yield() to actually flush bytes out, so anything
  // printed after would be silently lost, same as the arena/bounds/etc. panics that
  // already print their own diagnostic before calling this function.
  ${JUNO_WATCHDOG_PANIC_MESSAGE}
  noInterrupts();
  for (;;) {}
}
${JUNO_WATCHDOG_BEGIN_FUNCTION}
