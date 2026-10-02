// Declarations-only stand-in for the Zephyr kernel API the UNO Q thread port uses, so the generated shim can be
// syntax-checked offline.
#pragma once

#include <stddef.h>
#include <stdint.h>

struct k_thread {
  int unused;
};
struct k_sem {
  int unused;
};
struct k_timeout_t {
  int64_t ticks;
};
typedef void (*k_thread_entry_t)(void*, void*, void*);
typedef struct k_thread* k_tid_t;

#define K_FOREVER (k_timeout_t{-1})
#define K_NO_WAIT (k_timeout_t{0})
#define K_PRIO_PREEMPT(prio) (prio)
#define K_THREAD_STACK_ARRAY_DEFINE(name, count, size) static char name[count][size]
#define K_THREAD_STACK_SIZEOF(sym) sizeof(sym)
#define K_THREAD_STACK_BUFFER(sym) (sym)

inline k_tid_t k_thread_create(struct k_thread*, char*, size_t, k_thread_entry_t, void*, void*, void*, int,
                               uint32_t, k_timeout_t) {
  return nullptr;
}
inline int k_thread_join(struct k_thread*, k_timeout_t) { return 0; }
inline void k_thread_abort(struct k_thread*) {}
inline int k_sem_init(struct k_sem*, unsigned int, unsigned int) { return 0; }
inline void k_sem_give(struct k_sem*) {}
inline int k_sem_take(struct k_sem*, k_timeout_t) { return 0; }
