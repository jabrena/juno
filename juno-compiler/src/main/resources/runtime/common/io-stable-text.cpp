extern "C" void juno_io_offload(void (*job)(void*), void* argument);
extern "C" bool juno_io_on_worker();

// Text a job reads after its caller has stopped running: a short runtime String lives in a pool slot
// another thread may reuse meanwhile, so it is copied to the caller's stack. Anything longer than a
// slot holds is a literal or an arena String, which stays put.
static constexpr uint32_t JUNO_IO_TEXT_COPY_BYTES = ${JUNO_SLOT_BYTES}u;
static const char* juno_io_stable_text(const char* text, char* copy) {
  if (text == nullptr) return nullptr;
  for (uint32_t i = 0u; i < JUNO_IO_TEXT_COPY_BYTES; i++) {
    copy[i] = text[i];
    if (text[i] == '\0') return copy;
  }
  return text;
}
