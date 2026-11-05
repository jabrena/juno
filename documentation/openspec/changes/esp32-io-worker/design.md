## Context

`runtime/esp32/thread-port.cpp` creates one FreeRTOS task per Juno slot. Each task is pinned to `xPortGetCoreID()`,
the Arduino loop core (core 1), at the loop task's priority, and the tasks hand a baton between them. Every piece of
runtime state is unlocked because only the baton holder touches it. That covers the allocator, the collector,
monitors, atomics, the scheduler globals, the pending exception, scoped values, string slots and device globals.
This change keeps all of that as it is.

The worker contract already exists for the UNO Q:

- `IoRuntime` emits `juno_io_offload`, `juno_io_wait` and the device owners.
- `ThreadRuntime.zephyrIoWorker()` provides the port: `JUNO_IO_WORKER_COUNT`, `juno_port_io_dispatch`,
  `juno_port_io_busy` and `juno_io_on_worker`.
- `CoreRuntime.ioWorker()`/`ioWorkerPort()` select the worker per core.

The scheduler parks the caller in `JUNO_SLOT_WAITING_IO` and moves the baton on. A cancelled caller stays in
`JUNO_SLOT_DRAINING`, and its stack is still scanned until the worker goes idle.

## Goals / Non-Goals

**Goals:**

- A Juno thread waiting in a device call on the Nano ESP32 or the Cardputer no longer holds the Java core.
- The shared `delay(1)` waits yield the baton on every board.
- Programs that don't use the worker produce byte-identical output.

**Non-Goals:**

- Running Java code on two cores at once. That is `esp32-parallel-threads`, which builds on this change. Every
  decision here must still hold when two Java threads run: the worker never allocates and never touches scheduler
  state, results are handed back with explicit acquire/release, and a parked caller in `JUNO_SLOT_WAITING_IO` has
  already published its stack pointer.
- A Java API for core IDs.
- Speeding up CPU-bound programs.

## Decisions

- **Worker placement: core 0, priority 2.** On core 1 the worker would have to outrank the Juno tasks, and it would
  preempt Java during CPU-heavy jobs such as the mbedTLS handshake. At priority 2 on core 0 it sits above IDLE0 and
  far below `esp_timer` (about 22), Wi-Fi (about 23) and lwIP (about 18). *Alternative:* `tskNO_AFFINITY`, the
  AsyncTCP default. Rejected as the default because it makes "which core runs I/O" non-deterministic, which the
  docs and the boot line have to state. It is kept as the fallback if core 0 placement trips the watchdog.
- **Two workers, 8 KiB stack each.** Two matches `maxConcurrentHttpRequests()`. On the UNO Q, TLS runs on Linux,
  so 4 KiB is enough there. On ESP32, mbedTLS runs inside the job and needs more. The stacks are static, in
  internal DRAM (`StackType_t[]`, `StaticTask_t`, `xTaskCreateStaticPinnedToCore`), so the link fails rather than
  the board running out of heap at runtime.
- **Same synchronization as the UNO Q.** One binary semaphore (`xSemaphoreCreateBinaryStatic`) per worker wakes it.
  `busy` is claimed with an acquire-release compare-exchange and cleared with a release store. The caller polls it
  with an acquire load. On ESP32 the caller and the worker run on different cores, so those explicit orderings are
  what make the job's results visible to the caller, not the semaphore. `juno_io_on_worker` compares
  `xTaskGetCurrentTaskHandle()` against the workers' handles.
- **What a job may touch (unchanged contract):**
  - It may touch its job struct, on the parked caller's stack, and the buffers that struct points to, including
    Java `byte[]` buffers. The collector is non-moving mark/sweep, so they stay put.
  - It never allocates, throws, uses runtime-string slots (stable-text copies cover this), takes owners or calls
    the scheduler.
  - It never writes to `Serial`: on the Cardputer, USB-Serial/JTAG writes can block when no host is attached.
- **Wi-Fi gating.** `IoRuntime.needed(features, worker)` counts `WIFI` as waiting when the core has a worker. That
  is true on the UNO Q, where `Bridge.begin()` waits, and false on ESP32, where `WiFi.begin` returns at once. A new
  default method `CoreRuntime.wifiBeginWaits()` (false, overridden to true by `ZephyrCoreRuntime`) replaces
  `worker` in that clause. `preludeFor` and `usesWorker` keep their structure. The UNO Q output doesn't change,
  and ESP32 Wi-Fi-only programs keep today's shim.
- **Boot diagnostic.** When the worker is used, the ESP32 worker port prints
  `[juno-thread] java core=<n> io core=<m>` once, from the caller's task, before the first dispatch. The numbers
  come from `xPortGetCoreID()` and the pinned core. The line is under 31 characters of runtime text. No
  `juno-api` change is needed.
- **The `delay(1)` loops.** These three shared helpers already sit inside the device-owner prelude, so
  `juno_io_wait` is available. Without threads, `juno_io_wait` is a plain delay, so single-threaded programs keep
  their timing. Their shim text still changes, which is expected.

## Risks / Trade-offs

- **The task watchdog on IDLE0.** A job that keeps core 0 busy for about 5 s without blocking can reset the board.
  Mitigation: jobs already block in lwIP and mbedTLS I/O, and the SD scan already pauses. Run the hardware probe on
  both boards. On the Cardputer, `@Watchdog` disables the IDLE0 check, so the Nano ESP32 is the board to watch.
- **Internal RAM.** About 16 KiB more static DRAM, plus about 40 KB of heap per TLS session. The Cardputer's
  budget is tighter. Mitigation: `juno:verify` the threaded HTTPS examples on both boards, and add a line to the
  runtime report for worker stacks.
- **SdFat/SPI is not thread-safe.** Mitigation: the `JUNO_IO_*` owners are held by the parked caller for the
  job's lifetime. A test asserts that the SD offload takes the storage owner on ESP32.
- **Cancellation during a long connect.** A cancelled caller drains until the worker is idle, which is the existing
  reap logic. It is covered by an ESP32 variant of the UNO Q cancellation test.
- **Two Arduino-ESP32 lines (2.x on the Nano, 3.x on the M5Stack).** `WiFiClient` is an alias for `NetworkClient`
  in 3.x, and the connect timeouts differ. The worker code uses only FreeRTOS APIs that are common to both lines.

## Migration Plan

Ship the fix and the worker as separate PRs (see `tasks.md`). Rollback means returning `ioWorker()` to `false` in
`Esp32CoreRuntime`, which brings back today's inline offload.

## Open Questions

- Does core 0 at priority 2 hold up under a TLS handshake on the Nano ESP32 without tripping the watchdog? This
  will be measured with the probe on hardware.
- Should `RuntimeRiskAnalyzer` warn when worker stacks plus thread stacks approach the Cardputer's internal DRAM
  limit?
