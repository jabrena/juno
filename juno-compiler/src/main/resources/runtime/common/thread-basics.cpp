
extern "C" void yield(void);

extern "C" void juno_thread_sleep(int64_t millis) {
  while (millis > 0) {
    int64_t chunk = millis > 0x3FFFFFFF ? 0x3FFFFFFF : millis;
    ${JUNO_CORE_DELAY}(static_cast<uint32_t>(chunk));
    millis -= chunk;
  }
}

extern "C" void juno_thread_yield() {
  yield();
}
