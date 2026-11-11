## Why

On every board a Juno virtual thread is a platform thread under another name. `Thread.startVirtualThread` and
`StructuredTaskScope.fork` claim one of `RuntimeLimits.MAX_THREADS` (4, main included) scheduler slots, and each slot
owns a stack reserved for the thread's whole life: 2 KiB on the UNO R4 WiFi, 4 KiB on the UNO Q and Pico 2 W, 8 KiB on
the ESP32 family. On the ESP32 boards and the UNO Q that slot is an RTOS task or thread (FreeRTOS, Zephyr) passing a
baton; on the R4 and Pico 2 W it is a stack switched by Juno's own port. Nothing is ever unmounted, so a blocked virtual
thread keeps its whole stack, at most three run besides main, and threaded programs need `-Djuno.Xss`/`-Djuno.Xmx`
workarounds to link (MinerGame on the R4). Only one thread runs Java code at a time, even on dual-core boards.

## What Changes

- Virtual threads and `StructuredTaskScope` forks become **continuations** mounted on **one carrier per core**: one on the
  UNO R4 WiFi and UNO Q, two on the Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W. `main` stays a platform
  thread with its own stack.
- When a virtual thread parks or is preempted, the live part of its carrier stack is **copied into an arena chunk**
  (freeze) and copied back to the same addresses on the same carrier when it resumes (thaw). Generated code is unchanged.
- Current preemption is kept (1 ms tick on Thumb-2, safepoints on the ESP32 family) behind a scheduling-policy interface.
- On the dual-core boards two virtual threads run Java code **in parallel**: the arena, collector, monitors, atomics,
  run queue and per-thread globals become multi-core safe, and the collector stops the other core with a flag checked at
  its safepoints.
- Monitors are owned by virtual threads, so a virtual thread may park while holding a `synchronized` lock.
- UNO Q offloaded I/O moves its job data into **worker-owned storage**, so the waiting virtual thread unmounts and others
  keep running.
- **BREAKING:** `-Djuno.maxThreads` is removed from the Maven plugin and `RuntimeConfig`; the only limit on live virtual
  threads is arena space. `-Djuno.Xss` now sets the carrier stack size.
- The runtime risk report shows carrier stack size and estimated frozen-chunk sizes.
- The UNO R3 and UNO WiFi Rev2 (AVR) are out of scope and keep today's slot-based threads with byte-identical output.
- Released without a feature flag: continuations replace slots outright on the six boards above.

## Capabilities

### New Capabilities

- `virtual-thread-continuations`: carriers per core, freeze/thaw continuations, scheduling, structured-concurrency
  forks, monitors, multi-core safety, offloaded I/O, configuration limits, diagnostics and validation for virtual threads
  on the UNO R4 WiFi, UNO Q, Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W.

### Modified Capabilities

None (no existing OpenSpec capability covers the thread runtime).

## Impact

- `juno-compiler`: `backend/runtime/ThreadRuntime`, the `CoreRuntime` SPI and the Renesas, Zephyr, ESP32 and RP2350 core
  runtimes; runtime resources `common/thread-scheduler.cpp`, `common/structured-tasks.cpp`,
  `common/io-worker-offload.cpp`, the HTTP client/server and SD offload call sites, `thumb2/preemption.cpp`, and the
  `renesas`, `zephyr` and `esp32` thread ports; `RuntimeConfig`, `RuntimeLimits`, `ConfigAnalyzer` and the risk report;
  atomics, monitors and the collector's root scan.
- `juno-maven-plugin`: removal of the `juno.maxThreads` parameter from `AbstractJunoMojo`.
- `juno-api`: unchanged (`Thread.startVirtualThread`/`join`/`isAlive`, `StructuredTaskScope`).
- `juno-examples`: one dedicated virtual-thread example; MinerGame build flags revisited.
- `juno-site`: thread documentation and board pages.
- No new Maven dependency.

## Source and derivation

- Source: GitHub issue jabrena/juno#317 "Evolve Virtual Threads as Continuations in Juno" — description (user story) and
  1 accessible comment (Functional Specification with Decisions 1–11), retrieved 2026-11-11T15:00:32Z (issue updated
  2026-11-11T14:49:46Z).
- Derivation direction: issue → OpenSpec. Changes to this change are not synchronized back to the issue automatically.
- Unresolved by source: Decision 1 (preemption policy) is deliberately deferred until the frame-copy cost is measured
  on the R4; the Pico 2 W stop-flag check location is "to be confirmed in design" (see `design.md`).
