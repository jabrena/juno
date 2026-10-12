package io.github.jabrena.juno.backend;

import java.util.Set;

/**
 * Hardware calls that wait without holding up the other threads. The runtime is never preempted part-way, so a
 * library loop that waits on a device ({@code delay(1)} until a response arrives) used to stall every thread for as
 * long as it waited. Such loops call {@code juno_io_wait} instead: with threads it sleeps the calling thread, so the
 * others run meanwhile; without threads it is the plain {@code delay} it replaced.
 *
 * <p>A thread that sleeps inside a library call can find another thread entering the same library, which none of the
 * Arduino libraries tolerate halfway through a transaction. So each waiting entry point holds a device guard
 * ({@code JunoIoGuard}) for its whole call: a second thread waits its turn, like a {@code synchronized} method, while
 * threads that do not touch the device keep running. A guard records its owner; cancelling a structured subtask that
 * owns one runs the guard's abort hook (closing a half-used client) and releases it, so the device does not stay
 * locked by a thread that will never run again.
 */
final class IoRuntime {
    private IoRuntime() {
    }

    /** Whether the program reaches a library with waiting entry points, so the shim needs {@link #prelude}. */
    static boolean needed(Set<ShimFeature> features) {
        return features.contains(ShimFeature.HTTP) || features.contains(ShimFeature.HTTPS)
                || features.contains(ShimFeature.HTTP_SERVER) || features.contains(ShimFeature.SD)
                || features.contains(ShimFeature.LEGO_POWERED_UP);
    }

    /** The devices, the wait and the guard, emitted ahead of the libraries that use them. */
    static String prelude(boolean threads) {
        String implementation = threads ? """
                extern "C" void juno_thread_sleep(int64_t millis);
                extern "C" void juno_io_acquire(uint32_t device, void* handle, void (*abort)(void*));
                extern "C" void juno_io_release(uint32_t device);

                static void juno_io_wait(uint32_t millisToWait) {
                  juno_thread_sleep(static_cast<int64_t>(millisToWait));
                }
                """ : """
                static void juno_io_wait(uint32_t millisToWait) {
                  delay(millisToWait);
                }

                static void juno_io_acquire(uint32_t, void*, void (*)(void*)) {
                }

                static void juno_io_release(uint32_t) {
                }
                """;
        return """

                // ---- Juno I/O: waits that let the other threads run, one thread per device ----
                enum : uint32_t { JUNO_IO_NETWORK, JUNO_IO_STORAGE, JUNO_IO_BLE, JUNO_IO_DEVICES };

                """ + implementation + """

                struct JunoIoGuard {
                  uint32_t device;
                  JunoIoGuard(uint32_t guarded, void* handle = nullptr, void (*abort)(void*) = nullptr)
                      : device(guarded) {
                    juno_io_acquire(guarded, handle, abort);
                  }
                  ~JunoIoGuard() {
                    juno_io_release(device);
                  }
                };
                """;
    }

    /**
     * The scheduler's half: who owns each device. A guard is reentrant for its owner (one guarded entry point may call
     * another), and a contender sleeps a millisecond at a time until the device is free.
     */
    static String owners() {
        return """

                struct JunoIoOwner {
                  uint32_t owner;
                  uint32_t depth;
                  void* handle;
                  void (*abort)(void*);
                };
                static constexpr uint32_t JUNO_IO_OWNED_DEVICES = 3u;
                static JunoIoOwner juno_io_owners[JUNO_IO_OWNED_DEVICES];

                extern "C" void juno_thread_sleep(int64_t millis);

                extern "C" void juno_io_acquire(uint32_t device, void* handle, void (*abort)(void*)) {
                  // A device call is the deepest the runtime goes on a thread's stack: catch an overflow here, before
                  // the call's own frames are used.
                  juno_port_check_stack(juno_current_slot);
                  uint32_t self = juno_current_slot + 1u;
                  JunoIoOwner& entry = juno_io_owners[device];
                  while (entry.owner != 0u && entry.owner != self) juno_thread_sleep(1);
                  if (entry.owner == self) {
                    entry.depth++;
                    return;
                  }
                  entry = {self, 1u, handle, abort};
                }

                extern "C" void juno_io_release(uint32_t device) {
                  JunoIoOwner& entry = juno_io_owners[device];
                  if (entry.owner != juno_current_slot + 1u) return;
                  if (--entry.depth == 0u) entry = {0u, 0u, nullptr, nullptr};
                }

                // A cancelled thread never leaves its library call: abort what it left half done and free the device.
                static void juno_io_release_owned(uint32_t owner) {
                  for (uint32_t device = 0u; device < JUNO_IO_OWNED_DEVICES; device++) {
                    JunoIoOwner& entry = juno_io_owners[device];
                    if (entry.owner != owner) continue;
                    if (entry.abort != nullptr) entry.abort(entry.handle);
                    entry = {0u, 0u, nullptr, nullptr};
                  }
                }
                """;
    }
}
