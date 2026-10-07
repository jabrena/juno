// Bare-metal stand-in for the slice of the Zephyr kernel the UNO Q thread port uses (threads, semaphores,
// join, abort), so the generated Q shim can run under QEMU on the Cortex-M4 harness. It is cooperative: a thread
// runs until it blocks on a semaphore, joins, or ends, and then the next runnable one (round robin) takes over.
// That matches how the Juno port uses Zephyr (a baton semaphore, one thread running at a time) but it is not a
// model of Zephyr's preemptive scheduler, priorities or timeouts; every timeout is treated as K_FOREVER.
#pragma once

#include <stddef.h>
#include <stdint.h>

extern "C" void juno_harness_write(const char* text);
extern "C" [[noreturn]] void juno_harness_exit(int code);

typedef void (*k_thread_entry_t)(void*, void*, void*);
struct k_sem;

enum : uint32_t { JUNO_KT_NONE = 0u, JUNO_KT_READY, JUNO_KT_WAITING, JUNO_KT_DEAD };

struct k_thread {
  uint32_t sp;  // saved stack pointer while switched out
  uint32_t state;
  k_thread_entry_t entry;
  void* arg1;
  void* arg2;
  void* arg3;
  k_sem* waiting;
};
struct k_sem {
  unsigned int count;
  unsigned int limit;
};
struct k_timeout_t {
  int64_t ticks;
};
typedef struct k_thread* k_tid_t;

#define K_FOREVER (k_timeout_t{-1})
#define K_NO_WAIT (k_timeout_t{0})
#define K_PRIO_PREEMPT(prio) (prio)
#define K_THREAD_STACK_ARRAY_DEFINE(name, count, size) alignas(8) static char name[count][size]
#define K_THREAD_STACK_SIZEOF(sym) sizeof(sym)
#define K_THREAD_STACK_BUFFER(sym) (sym)

constexpr uint32_t JUNO_KERNEL_MAX_THREADS = 8u;
constexpr uint32_t JUNO_KERNEL_FRAME_WORDS = 16u + 9u;

// Same frame layout as the R4 port's switch: r4-r11, lr and s16-s31 saved on the outgoing stack.
extern "C" void juno_kernel_switch(uint32_t* saveSp, uint32_t newSp);
__asm__(
    ".syntax unified\n"
    ".thumb\n"
    ".section .text.juno_kernel_switch,\"ax\",%progbits\n"
    ".global juno_kernel_switch\n"
    ".thumb_func\n"
    ".type juno_kernel_switch, %function\n"
    "juno_kernel_switch:\n"
    "  push {r4-r11, lr}\n"
    "  vpush {s16-s31}\n"
    "  str sp, [r0]\n"
    "  mov sp, r1\n"
    "  vpop {s16-s31}\n"
    "  pop {r4-r11, pc}\n"
    ".text\n");

inline k_thread juno_kernel_main = {0u, JUNO_KT_READY, nullptr, nullptr, nullptr, nullptr, nullptr};
inline k_thread* juno_kernel_current = &juno_kernel_main;
inline k_thread* juno_kernel_threads[JUNO_KERNEL_MAX_THREADS] = {&juno_kernel_main};

// Runs the next ready thread after the current one (the current one last), or returns if that is the current one.
// The caller has already set the current thread's state.
inline void juno_kernel_reschedule() {
  k_thread* from = juno_kernel_current;
  uint32_t start = 0u;
  for (uint32_t index = 0u; index < JUNO_KERNEL_MAX_THREADS; index++) {
    if (juno_kernel_threads[index] == from) start = index;
  }
  for (uint32_t step = 1u; step <= JUNO_KERNEL_MAX_THREADS; step++) {
    k_thread* next = juno_kernel_threads[(start + step) % JUNO_KERNEL_MAX_THREADS];
    if (next == nullptr || next->state != JUNO_KT_READY) continue;
    if (next == from) return;
    juno_kernel_current = next;
    juno_kernel_switch(&from->sp, next->sp);
    return;
  }
  juno_harness_write("[juno-deadlock]\n");
  juno_harness_exit(4);
}

extern "C" inline void juno_kernel_trampoline() {
  k_thread* self = juno_kernel_current;
  self->entry(self->arg1, self->arg2, self->arg3);
  self->state = JUNO_KT_DEAD;
  juno_kernel_reschedule();
  for (;;) {}
}

inline k_tid_t k_thread_create(struct k_thread* thread, char* stack, size_t size, k_thread_entry_t entry, void* a,
                               void* b, void* c, int, uint32_t, k_timeout_t) {
  uint32_t* top = reinterpret_cast<uint32_t*>(stack + size);
  uint32_t* frame = top - 2u - JUNO_KERNEL_FRAME_WORDS;
  for (uint32_t index = 0u; index < JUNO_KERNEL_FRAME_WORDS; index++) frame[index] = 0u;
  frame[JUNO_KERNEL_FRAME_WORDS - 1u] = static_cast<uint32_t>(
      reinterpret_cast<uintptr_t>(&juno_kernel_trampoline));
  thread->sp = static_cast<uint32_t>(reinterpret_cast<uintptr_t>(frame));
  thread->entry = entry;
  thread->arg1 = a;
  thread->arg2 = b;
  thread->arg3 = c;
  thread->waiting = nullptr;
  thread->state = JUNO_KT_READY;
  uint32_t free = JUNO_KERNEL_MAX_THREADS;
  for (uint32_t index = 0u; index < JUNO_KERNEL_MAX_THREADS; index++) {
    if (juno_kernel_threads[index] == thread) return thread;
    if (free == JUNO_KERNEL_MAX_THREADS && (juno_kernel_threads[index] == nullptr)) free = index;
  }
  if (free == JUNO_KERNEL_MAX_THREADS) {
    juno_harness_write("[juno-kernel] too many threads\n");
    juno_harness_exit(4);
  }
  juno_kernel_threads[free] = thread;
  return thread;
}

inline int k_thread_join(struct k_thread* thread, k_timeout_t) {
  while (thread->state != JUNO_KT_DEAD && thread->state != JUNO_KT_NONE) juno_kernel_reschedule();
  return 0;
}

inline void k_thread_abort(struct k_thread* thread) {
  thread->state = JUNO_KT_DEAD;
  if (thread == juno_kernel_current) {
    juno_kernel_reschedule();
    for (;;) {}
  }
}

inline int k_sem_init(struct k_sem* sem, unsigned int initial, unsigned int limit) {
  sem->count = initial;
  sem->limit = limit;
  return 0;
}

inline void k_sem_give(struct k_sem* sem) {
  if (sem->count < sem->limit) sem->count++;
  for (uint32_t index = 0u; index < JUNO_KERNEL_MAX_THREADS; index++) {
    k_thread* waiter = juno_kernel_threads[index];
    if (waiter != nullptr && waiter->state == JUNO_KT_WAITING && waiter->waiting == sem) {
      waiter->state = JUNO_KT_READY;
      waiter->waiting = nullptr;
      return;
    }
  }
}

inline int k_sem_take(struct k_sem* sem, k_timeout_t) {
  while (sem->count == 0u) {
    juno_kernel_current->state = JUNO_KT_WAITING;
    juno_kernel_current->waiting = sem;
    juno_kernel_reschedule();
  }
  sem->count--;
  return 0;
}
