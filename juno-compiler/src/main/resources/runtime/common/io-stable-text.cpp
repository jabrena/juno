extern "C" void juno_io_offload(void (*job)(void*), void* argument);
extern "C" bool juno_io_on_worker();

// Text a job reads after its caller has stopped running. Literals and arena Strings stay put while their caller
// waits; short text is still copied to the caller's stack, so a job never depends on that.
static constexpr uint32_t JUNO_IO_TEXT_COPY_BYTES = ${JUNO_IO_TEXT_COPY_BYTES}u;
static const char* juno_io_stable_text(const char* text, char* copy) {
  if (text == nullptr) return nullptr;
  for (uint32_t i = 0u; i < JUNO_IO_TEXT_COPY_BYTES; i++) {
    copy[i] = text[i];
    if (text[i] == '\0') return copy;
  }
  return text;
}
