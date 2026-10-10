package io.github.jabrena.juno.backend;

/**
 * The {@code AtomicInteger}/{@code AtomicBoolean}/{@code AtomicLong} cells: a word (or a low/high word pair) in the
 * arena. The cooperative scheduler only switches threads at loop backedges and sleep/yield/join, never inside one
 * of these functions, so a plain read-modify-write is already atomic and needs no monitor. An {@code AtomicBoolean}
 * is an {@code AtomicInteger} holding 0 or 1. A {@code long} travels as two {@code uint32_t} words, which keeps the
 * C signatures free of AAPCS 64-bit register alignment.
 */
final class AtomicRuntime {
    private AtomicRuntime() {
    }

    static String helpers() {
        return """

                static int32_t* juno_atomic_cell(int32_t handle) {
                  if (handle == 0) juno_panic();
                  return reinterpret_cast<int32_t*>(static_cast<uintptr_t>(static_cast<uint32_t>(handle)));
                }

                static int32_t juno_atomic_alloc(uint32_t words, int32_t low, int32_t high) {
                  int32_t* cell = static_cast<int32_t*>(juno_alloc(words * sizeof(int32_t), sizeof(int32_t)));
                  cell[0] = low;
                  if (words > 1u) cell[1] = high;
                  return static_cast<int32_t>(reinterpret_cast<intptr_t>(cell));
                }

                static int32_t juno_atomic_int_add(int32_t handle, int32_t delta, bool returnOld) {
                  int32_t* cell = juno_atomic_cell(handle);
                  int32_t old = *cell;
                  *cell = static_cast<int32_t>(static_cast<uint32_t>(old) + static_cast<uint32_t>(delta));
                  return returnOld ? old : *cell;
                }

                extern "C" int32_t juno_atomic_int_new_default() { return juno_atomic_alloc(1u, 0, 0); }
                extern "C" int32_t juno_atomic_int_new(int32_t value) { return juno_atomic_alloc(1u, value, 0); }
                extern "C" int32_t juno_atomic_int_get(int32_t handle) { return *juno_atomic_cell(handle); }
                extern "C" void juno_atomic_int_set(int32_t handle, int32_t value) { *juno_atomic_cell(handle) = value; }

                extern "C" int32_t juno_atomic_int_get_and_set(int32_t handle, int32_t value) {
                  int32_t* cell = juno_atomic_cell(handle);
                  int32_t old = *cell;
                  *cell = value;
                  return old;
                }

                extern "C" int32_t juno_atomic_int_compare_and_set(int32_t handle, int32_t expected, int32_t value) {
                  int32_t* cell = juno_atomic_cell(handle);
                  if (*cell != expected) return 0;
                  *cell = value;
                  return 1;
                }

                extern "C" int32_t juno_atomic_int_increment_and_get(int32_t handle) {
                  return juno_atomic_int_add(handle, 1, false);
                }
                extern "C" int32_t juno_atomic_int_decrement_and_get(int32_t handle) {
                  return juno_atomic_int_add(handle, -1, false);
                }
                extern "C" int32_t juno_atomic_int_get_and_increment(int32_t handle) {
                  return juno_atomic_int_add(handle, 1, true);
                }
                extern "C" int32_t juno_atomic_int_get_and_decrement(int32_t handle) {
                  return juno_atomic_int_add(handle, -1, true);
                }
                extern "C" int32_t juno_atomic_int_add_and_get(int32_t handle, int32_t delta) {
                  return juno_atomic_int_add(handle, delta, false);
                }
                extern "C" int32_t juno_atomic_int_get_and_add(int32_t handle, int32_t delta) {
                  return juno_atomic_int_add(handle, delta, true);
                }

                static uint64_t juno_atomic_long_load(int32_t handle) {
                  int32_t* cell = juno_atomic_cell(handle);
                  return (static_cast<uint64_t>(static_cast<uint32_t>(cell[1])) << 32)
                      | static_cast<uint64_t>(static_cast<uint32_t>(cell[0]));
                }

                static void juno_atomic_long_store(int32_t handle, uint64_t value) {
                  int32_t* cell = juno_atomic_cell(handle);
                  cell[0] = static_cast<int32_t>(static_cast<uint32_t>(value));
                  cell[1] = static_cast<int32_t>(static_cast<uint32_t>(value >> 32));
                }

                static uint64_t juno_atomic_long_join(uint32_t low, uint32_t high) {
                  return (static_cast<uint64_t>(high) << 32) | static_cast<uint64_t>(low);
                }

                static int64_t juno_atomic_long_add(int32_t handle, uint64_t delta, bool returnOld) {
                  uint64_t old = juno_atomic_long_load(handle);
                  juno_atomic_long_store(handle, old + delta);
                  return static_cast<int64_t>(returnOld ? old : old + delta);
                }

                extern "C" int32_t juno_atomic_long_new_default() { return juno_atomic_alloc(2u, 0, 0); }
                extern "C" int32_t juno_atomic_long_new(uint32_t low, uint32_t high) {
                  return juno_atomic_alloc(2u, static_cast<int32_t>(low), static_cast<int32_t>(high));
                }
                extern "C" int64_t juno_atomic_long_get(int32_t handle) {
                  return static_cast<int64_t>(juno_atomic_long_load(handle));
                }
                extern "C" void juno_atomic_long_set(int32_t handle, uint32_t low, uint32_t high) {
                  juno_atomic_long_store(handle, juno_atomic_long_join(low, high));
                }

                extern "C" int64_t juno_atomic_long_get_and_set(int32_t handle, uint32_t low, uint32_t high) {
                  uint64_t old = juno_atomic_long_load(handle);
                  juno_atomic_long_store(handle, juno_atomic_long_join(low, high));
                  return static_cast<int64_t>(old);
                }

                extern "C" int32_t juno_atomic_long_compare_and_set(int32_t handle, uint32_t expectedLow,
                    uint32_t expectedHigh, uint32_t low, uint32_t high) {
                  if (juno_atomic_long_load(handle) != juno_atomic_long_join(expectedLow, expectedHigh)) return 0;
                  juno_atomic_long_store(handle, juno_atomic_long_join(low, high));
                  return 1;
                }

                extern "C" int64_t juno_atomic_long_increment_and_get(int32_t handle) {
                  return juno_atomic_long_add(handle, 1u, false);
                }
                extern "C" int64_t juno_atomic_long_decrement_and_get(int32_t handle) {
                  return juno_atomic_long_add(handle, ~static_cast<uint64_t>(0), false);
                }
                extern "C" int64_t juno_atomic_long_get_and_increment(int32_t handle) {
                  return juno_atomic_long_add(handle, 1u, true);
                }
                extern "C" int64_t juno_atomic_long_get_and_decrement(int32_t handle) {
                  return juno_atomic_long_add(handle, ~static_cast<uint64_t>(0), true);
                }
                extern "C" int64_t juno_atomic_long_add_and_get(int32_t handle, uint32_t low, uint32_t high) {
                  return juno_atomic_long_add(handle, juno_atomic_long_join(low, high), false);
                }
                extern "C" int64_t juno_atomic_long_get_and_add(int32_t handle, uint32_t low, uint32_t high) {
                  return juno_atomic_long_add(handle, juno_atomic_long_join(low, high), true);
                }
                """;
    }
}
