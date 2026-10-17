// avr-libc normally bounds malloc by the current SP. A Juno task's stack lives in .bss,
// below the native heap, so that check would reject even WiFiNINA's 64-byte receive buffer.
// While main is parked, use its stack boundary instead; restore the caller's allocator
// configuration whenever main resumes. Explicit application-provided heap ends are preserved.
extern "C" {
extern char* __malloc_heap_end;
extern size_t __malloc_margin;
}
static char* juno_main_malloc_heap_end = nullptr;

static void juno_port_malloc_switch(uint32_t from, uint32_t to, uintptr_t parkedMainStack) {
  if (from == 0u && to != 0u) {
    juno_main_malloc_heap_end = __malloc_heap_end;
    if (__malloc_heap_end == nullptr) {
      uintptr_t limit = parkedMainStack > __malloc_margin ? parkedMainStack - __malloc_margin : 1u;
      __malloc_heap_end = reinterpret_cast<char*>(limit);
    }
  } else if (from != 0u && to == 0u) {
    __malloc_heap_end = juno_main_malloc_heap_end;
  }
}
