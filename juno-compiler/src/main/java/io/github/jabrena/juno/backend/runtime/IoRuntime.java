package io.github.jabrena.juno.backend.runtime;

import io.github.jabrena.juno.RuntimeLimits;
import io.github.jabrena.juno.backend.runtime.spi.CoreRuntime;
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
 * {@code JunoHttpGuard} leases: two on R4/Q and one on WiFi Rev2, whose blocking TLS handshake can starve another
 * response. Wi-Fi setup and the HTTP server retain the exclusive network guard. A guard records its owner; cancelling a structured subtask that
 * owns one runs the guard's abort hook (closing a half-used client) and releases it, so the device does not stay
 * locked by a thread that will never run again.
 *
 * <p>Some waits happen inside a vendor library, out of the shim's reach. Where the core has an RTOS (the UNO Q's
 * Zephyr, whose network calls all wait on Linux through Arduino_RouterBridge), such a call runs on an I/O worker
 * thread while its caller is parked in the scheduler ({@code juno_io_offload}); elsewhere it still holds every thread.
 */
public final class IoRuntime {
    private final RuntimeSource runtimeSource = new RuntimeSource();


    /**
     * Whether the program reaches a library with waiting entry points, so the shim needs {@link #prelude}; with an
     * I/O worker, starting Wi-Fi (the UNO Q's bridge to Linux) is one too.
     */
    boolean needed(Set<ShimFeature> features, boolean worker) {
        return features.contains(ShimFeature.HTTP) || features.contains(ShimFeature.HTTPS)
                || (worker && features.contains(ShimFeature.WIFI))
                || features.contains(ShimFeature.HTTP_SERVER) || features.contains(ShimFeature.SD)
                || features.contains(ShimFeature.LEGO_POWERED_UP);
    }

    /** Whether waiting device calls run on the core's I/O worker: the core has one and the program makes them. */
    boolean usesWorker(CoreRuntime core, Set<ShimFeature> features) {
        return core.ioWorker() && features.contains(ShimFeature.THREADS) && needed(features, true);
    }

    /** {@link #prelude} when the program needs it, else nothing. */
    String preludeFor(CoreRuntime core, Set<ShimFeature> features) {
        boolean worker = usesWorker(core, features);
        return needed(features, core.ioWorker()) ? prelude(features.contains(ShimFeature.THREADS), worker) : "";
    }

    /** The core's I/O worker when the program uses it, else nothing; goes right after the thread port. */
    String workerPortFor(CoreRuntime core, Set<ShimFeature> features) {
        return usesWorker(core, features) ? core.ioWorkerPort() : "";
    }

    /**
     * The devices, the wait, the guard and {@code juno_io_offload}, emitted ahead of the libraries that use them.
     * {@code juno_io_offload(job, argument)} runs a device call: on the I/O worker while the calling thread is parked,
     * when the core has one and the program has threads, else right here. A job may wait ({@code juno_io_wait}) and
     * take guards (no-ops on the worker, whose caller already holds them), but never touches the arena.
     *
     * @param worker whether the core's port provides the I/O worker ({@link CoreRuntime#ioWorker()})
     */
    String prelude(boolean threads, boolean worker) {
        String offload = threads && worker ? runtimeSource.load("common/io-stable-text.cpp").replace("${JUNO_SLOT_BYTES}", Integer.toString(RuntimeLimits.STRING_SLOT_CAPACITY_BYTES))
                : """
                static void juno_io_offload(void (*job)(void*), void* argument) {
                  job(argument);
                }

                static bool juno_io_on_worker() {
                  return false;
                }
                """;
        String implementation = threads ? """
                extern "C" void juno_thread_sleep(int64_t millis);
                extern "C" void juno_io_acquire(uint32_t device, void* handle, void (*abort)(void*));
                extern "C" void juno_io_release(uint32_t device);
                extern "C" uint32_t juno_http_acquire(void* handle, void (*abort)(void*));
                extern "C" void juno_http_release(uint32_t device);
                """ + offload + """

                static void juno_io_wait(uint32_t millisToWait) {
                  if (juno_io_on_worker()) {
                    delay(millisToWait);
                  } else {
                    juno_thread_sleep(static_cast<int64_t>(millisToWait));
                  }
                }
                """ : offload + """
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
     * another), and a contender sleeps a millisecond at a time until the device is free. With an I/O worker, this is
     * also where a call is handed to it: the caller parks ({@code JUNO_SLOT_WAITING_IO}) until the worker is idle
     * again, and a caller cancelled meanwhile stays {@code JUNO_SLOT_DRAINING}, its stack still scanned by the
     * collector since the worker may write into its arrays, until {@code juno_io_reap} frees it.
     */
    String owners(boolean worker, int maxHttpRequests) {
        String workerState = worker ? """

                // The owner and deferred cancellation state of each I/O worker. A cancelled call keeps its device
                // lease until its worker finishes, so another request cannot reuse its transport prematurely.
                static uint32_t juno_io_offloader[JUNO_IO_WORKER_COUNT];
                static uint32_t juno_io_worker_for_slot[JUNO_MAX_THREADS];
                static uint32_t juno_io_deferred_owner[JUNO_IO_WORKER_COUNT];
                static void (*juno_io_deferred_abort[JUNO_IO_WORKER_COUNT])(void*);
                static void* juno_io_deferred_handle[JUNO_IO_WORKER_COUNT];
                """ : "";
        String onWorker = worker ? "\n  if (juno_io_on_worker()) return;" : "";
        String onHttpWorker = worker ? "\n  if (juno_io_on_worker()) return 0u;" : "";
        String abort = worker ? """
                    if (entry.abort == nullptr) {
                    } else if (offloading) {
                      juno_io_deferred_abort[offloadingWorker] = entry.abort;
                      juno_io_deferred_handle[offloadingWorker] = entry.handle;
                    } else {
                      entry.abort(entry.handle);
                    }""" : """
                    if (entry.abort != nullptr) entry.abort(entry.handle);""";
        String offloadingFlag = worker ? """
                  uint32_t offloadingWorker = JUNO_IO_WORKER_COUNT;
                  for (uint32_t worker = 0u; worker < JUNO_IO_WORKER_COUNT; worker++) {
                    if (juno_io_offloader[worker] == owner && juno_port_io_busy(worker)) {
                      offloadingWorker = worker;
                      break;
                    }
                  }
                  bool offloading = offloadingWorker != JUNO_IO_WORKER_COUNT;
                  if (offloading) juno_io_deferred_owner[offloadingWorker] = owner;""" : "";
        String offload = worker ? runtimeSource.load("common/io-worker-offload.cpp") : "";
        return runtimeSource.load("common/io-owners.cpp").replace("${WORKER_STATE}", workerState)
                .replace("${JUNO_HTTP_MAX_IN_FLIGHT}", Integer.toString(maxHttpRequests))
                .replace("${ON_WORKER}", onWorker)
                .replace("${ON_HTTP_WORKER}", onHttpWorker)
                .replace("${OFFLOADING}", offloadingFlag)
                .replace("${ABORT}", abort)
                .replace("${OFFLOADING_CONTINUE}", worker ? "\n    if (offloading) continue;" : "")
                .replace("${OFFLOAD}", offload);
    }
}
