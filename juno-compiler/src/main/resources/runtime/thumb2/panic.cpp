
${JUNO_YIELD_FUNCTION}extern "C" [[noreturn]] void juno_panic() {
  // The watchdog diagnostic (if any) must print before noInterrupts(): USB CDC
  // Serial relies on interrupts/yield() to actually flush bytes out, so anything
  // printed after would be silently lost, same as the arena/bounds/etc. panics that
  // already print their own diagnostic before calling this function.
  ${JUNO_WATCHDOG_PANIC_MESSAGE}
  noInterrupts();
  for (;;) {}
}
// Called by the generated juno_array_check when an index is outside the length stored in front of an array's
// elements. `site` is the check's return address, printed relative to juno_panic so it can be matched against the
// linked image's symbol table.
extern "C" [[noreturn]] void juno_array_index_panic(int32_t index, int32_t length, uintptr_t site) {
  int32_t offset = static_cast<int32_t>(site - reinterpret_cast<uintptr_t>(&juno_panic));
  Serial.print("[juno] index ");
  Serial.print(index);
  Serial.print(" out of bounds for length ");
  Serial.print(length);
  Serial.print(" at juno_panic");
  Serial.println(offset);
  juno_panic();
}
${JUNO_WATCHDOG_BEGIN_FUNCTION}
