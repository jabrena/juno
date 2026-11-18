package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.backend.runtime.spi.ShimFeature;

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
 * threads that do not touch the device keep running. HTTP requests instead use core-bounded, independently owned
 * {@code JunoHttpGuard} leases: two on the R4, whose blocking TLS handshake can starve another response. Wi-Fi setup and the HTTP server retain the exclusive network guard. A guard records its owner; cancelling a structured subtask that
 * owns one runs the guard's abort hook (closing a half-used client) and releases it, so the device does not stay
 * locked by a thread that will never run again.
 *
 * <p>Some waits happen inside a vendor library, out of the shim's reach: such a call still holds every thread.
 */
public final class IoRuntime {
    private static final String WAIT = """
            static void juno_io_wait(uint32_t millisToWait) {
              juno_thread_sleep(static_cast<int64_t>(millisToWait));
            }
            """;

    private static final String LOCKED_WAIT = """
            static void juno_io_wait(uint32_t millisToWait) {
              bool runtimeHeld = juno_runtime_owned();
              if (runtimeHeld) juno_runtime_exit();
              juno_thread_sleep(static_cast<int64_t>(millisToWait));
              if (runtimeHeld) juno_runtime_enter();
            }
            """;

    private static final String IO_GUARD = """

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

    private static final String LOCKED_IO_GUARD = """

            struct JunoIoGuard {
              uint32_t device;
              bool runtimeReleased;
              JunoIoGuard(uint32_t guarded, void* handle = nullptr, void (*abort)(void*) = nullptr)
                  : device(guarded), runtimeReleased(juno_runtime_owned()) {
                if (runtimeReleased) juno_runtime_exit();
                juno_io_acquire(guarded, handle, abort);
                if (runtimeReleased) juno_runtime_enter();
              }
              ~JunoIoGuard() {
                if (runtimeReleased) juno_runtime_exit();
                juno_io_release(device);
                if (runtimeReleased) juno_runtime_enter();
              }
            };
            """;

    private final RuntimeSource runtimeSource = new RuntimeSource();


    /** Whether the program reaches a library with waiting entry points, so the shim needs {@link #prelude}. */
    boolean needed(Set<ShimFeature> features) {
        return features.contains(ShimFeature.HTTP) || features.contains(ShimFeature.HTTPS)
                || features.contains(ShimFeature.HTTP_SERVER) || features.contains(ShimFeature.SD)
                || features.contains(ShimFeature.BLUETOOTH_LE);
    }

    /** {@link #prelude} for a single carrier when the program needs it, else nothing. */
    String preludeFor(Set<ShimFeature> features) {
        return preludeFor(features, 1);
    }

    /**
     * {@link #prelude} when the program needs it, else nothing. Only with threads on more than one carrier do the
     * waits and guards release the runtime lock, which single-carrier ports do not define.
     */
    String preludeFor(Set<ShimFeature> features, int carrierCount) {
        boolean threads = features.contains(ShimFeature.THREADS);
        return needed(features) ? prelude(threads, threads && carrierCount > 1) : "";
    }

    /**
     * The devices, the wait, the guard and {@code juno_io_offload}, emitted ahead of the libraries that use them.
     * {@code juno_io_offload(job, argument)} runs a device call right here. A job may wait ({@code juno_io_wait}) and
     * take guards, but never touches the arena.
     *
     * @param runtimeLock whether threads run on several carriers, so a wait or guard releases the runtime lock
     */
    String prelude(boolean threads, boolean runtimeLock) {
        String offload = """
                static void juno_io_offload(void (*job)(void*), void* argument) {
                  job(argument);
                }
                """;
        String lockDeclarations = runtimeLock ? """
                extern "C" bool juno_runtime_owned();
                extern "C" void juno_runtime_enter();
                extern "C" void juno_runtime_exit();
                """ : "";
        String implementation = threads ? lockDeclarations + """
                extern "C" void juno_thread_sleep(int64_t millis);
                extern "C" void juno_io_acquire(uint32_t device, void* handle, void (*abort)(void*));
                extern "C" void juno_io_release(uint32_t device);
                extern "C" uint32_t juno_http_acquire(void* handle, void (*abort)(void*));
                extern "C" void juno_http_release(uint32_t device);
                """ + offload + """

                """ + (runtimeLock ? LOCKED_WAIT : WAIT) : offload + """
                static void juno_io_wait(uint32_t millisToWait) {
                  delay(millisToWait);
                }

                static void juno_io_acquire(uint32_t, void*, void (*)(void*)) {
                }

                static void juno_io_release(uint32_t) {
                }

                static uint32_t juno_http_acquire(void*, void (*)(void*)) {
                  return 0u;
                }

                static void juno_http_release(uint32_t) {
                }
                """;
        return """

                // ---- Juno I/O: waits that let the other threads run, bounded HTTP and one thread per other device ----
                enum : uint32_t {
                  JUNO_IO_HTTP_FIRST,
                  JUNO_IO_HTTP_SECOND,
                  JUNO_IO_NETWORK,
                  JUNO_IO_STORAGE,
                  JUNO_IO_BLE,
                  JUNO_IO_DEVICES
                };

                """ + implementation + (runtimeLock ? LOCKED_IO_GUARD : IO_GUARD) + """

                struct JunoHttpGuard {
                  uint32_t device;
                  JunoHttpGuard(void* handle = nullptr, void (*abort)(void*) = nullptr)
                      : device(juno_http_acquire(handle, abort)) {
                  }
                  ~JunoHttpGuard() {
                    juno_http_release(device);
                  }
                };
                """;
    }

    /**
     * The scheduler's half: who owns each device. A guard is reentrant for its owner (one guarded entry point may call
     * another), and a contender sleeps a millisecond at a time until the device is free.
     */
    String owners(int maxHttpRequests) {
        return runtimeSource.load("common/io-owners.cpp")
                .replace("${JUNO_HTTP_MAX_IN_FLIGHT}", Integer.toString(maxHttpRequests));
    }
}
