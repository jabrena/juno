// The Zephyr core provides yield() itself (the generated assembly's `bl yield` reaches
// it directly) but inlines delay() and delayMicroseconds(), so the assembly calls them
// through these wrappers instead.
extern "C" void juno_delay(uint32_t ms) {
  delay(ms);
}

extern "C" void juno_delay_microseconds(uint32_t us) {
  delayMicroseconds(us);
}

