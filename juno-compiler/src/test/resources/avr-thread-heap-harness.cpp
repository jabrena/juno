#include <cassert>
#include <cstddef>
#include <cstdint>

extern "C" {
char* __malloc_heap_end = nullptr;
size_t __malloc_margin = 32;
}

// AVR_THREAD_HEAP

int main() {
  char ram[2048] = {};
  uintptr_t mainStack = reinterpret_cast<uintptr_t>(ram + 1800);
  uintptr_t taskStack = reinterpret_cast<uintptr_t>(ram + 400);
  char* heapStart = ram + 1000;
  // A static task stack cannot serve as avr-libc's native heap ceiling.
  assert(reinterpret_cast<char*>(taskStack - __malloc_margin) < heapStart);
  juno_port_malloc_switch(0, 1, mainStack);
  assert(__malloc_heap_end == ram + 1768);
  assert(__malloc_heap_end - heapStart >= 64);
  juno_port_malloc_switch(1, 2, taskStack);
  assert(__malloc_heap_end == ram + 1768);
  juno_port_malloc_switch(2, 0, taskStack);
  assert(__malloc_heap_end == nullptr);

  // Recompute after main moves deeper, and restore on both yielding and exiting tasks.
  juno_port_malloc_switch(0, 2, mainStack - 100);
  assert(__malloc_heap_end == ram + 1668);
  juno_port_malloc_switch(2, 0, taskStack);
  assert(__malloc_heap_end == nullptr);

  // Respect an explicitly bounded native heap, without changing it on task switches.
  __malloc_heap_end = ram + 1400;
  juno_port_malloc_switch(0, 1, mainStack);
  assert(__malloc_heap_end == ram + 1400);
  juno_port_malloc_switch(1, 2, taskStack);
  assert(__malloc_heap_end == ram + 1400);
  juno_port_malloc_switch(2, 0, taskStack);
  assert(__malloc_heap_end == ram + 1400);
  juno_port_malloc_switch(0, 0, mainStack);
  assert(__malloc_heap_end == ram + 1400);
}
