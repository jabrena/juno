## Why

Change `virtual-thread-continuations` (PR #323) runs virtual threads as continuations on **one carrier per board**.
On the dual-core boards (Nano ESP32 and M5Stack Cardputer on ESP32-S3, ESP32-CAM on ESP32, Pico 2 W on RP2350) all
Java code therefore still runs on a single core: the second core is idle (Pico 2 W) or only runs the radio stack
(ESP32). Decision 2 of issue #317 is **one carrier per core**, so CPU-bound virtual threads on those boards should run in
parallel.

The runtime cannot simply start a second carrier: every part of it assumes that only one thread runs Java code at a
time (the baton). That covers the scheduler and collector, but also 11 mutable static tables in 9 common runtime files
(runtime-string slots, HTTP, SD, BLE, I/O owners, …). 64-bit field atomicity relies on masking interrupts, which does
not stop the other core.

## What Changes

- A **second carrier on core 1** of each dual-core board: a second carrier stack and switcher, plus FreeRTOS tasks
  pinned to that core on the ESP32 family; core 1 started by the port with its own carrier and preemption tick on the
  Pico 2 W.
- **Placement:** a new virtual thread or fork goes to the least-loaded carrier and never migrates (carrier affinity).
  A thread whose entry can reach a radio or device intrinsic is placed on the carrier of the core the core library
  requires for that device.
- **A runtime lock:** every runtime (shim) call from generated code runs under one recursive lock, released while a
  thread blocks or waits for I/O. Generated Java code runs truly in parallel; the runtime stays serialized. This makes
  allocation, monitors, locks, latches, semaphores, atomics, runtime strings and device tables safe without redesigning
  each one.
- **Per-carrier runtime state:** current slot, pending exception, `ScopedValue` bindings and preemption state, one
  copy per carrier, selected by the core id.
- **Stop-the-world collection:** the collecting carrier, holding the runtime lock, raises a stop flag. The other
  carrier acknowledges at its next safepoint (or is already stopped waiting for the lock, or inside a blocking call that
  holds no arena pointer), with its registers spilled to its stack.
- **Memory model on two cores:** `volatile` accesses and monitor enter/exit emit barriers (`dmb` on Cortex-M33,
  `memw` on Xtensa); `volatile long`/`double` accesses take a spinlock instead of masking interrupts.
- **Board constraints:** the ESP32 core-0 carrier lets the core-0 idle task run (task-watchdog check on CPU 0);
  the Pico 2 W tolerates the other core being paused during flash writes.
- **Diagnostics:** the risk report shows carriers per board and warns when a virtual thread's code reaches a
  device that pins it to one core.
- Single-core boards (UNO R4 WiFi, UNO Q) and AVR boards are unchanged, and their generated output stays
  byte-identical.

## Capabilities

### New Capabilities

- `dual-core-carriers`: second carrier per dual-core board, placement, runtime lock, per-carrier state, stop-the-world
  collection, memory barriers and 64-bit atomicity across cores, board constraints, diagnostics and validation.

### Modified Capabilities

- `virtual-thread-continuations` (change in progress): tasks 6.2–6.5 (second carrier, multi-core safety, ESP32 and
  RP2350 dual carriers) move to this change.

## Impact

- `juno-compiler`:
  - runtime resources: `common/thread-scheduler.cpp`, `common/allocator.cpp`, `common/atomics.cpp`, the `esp32` and
    `renesas` (shared with RP2350) thread ports, `thumb2/preemption.cpp`, `avr/preemption.cpp` (safepoint path shared
    with ESP32)
  - shim entry points: the runtime-lock wrapper
  - backends: `FieldAccessLowering` (64-bit atomicity), barrier emission for `volatile` and monitors (Thumb-2 and
    Xtensa)
  - `CoreRuntime` SPI: carrier count per core
  - `RuntimeRiskAnalyzer`
- Tests: `XtensaRunIT` (ESP32-S3 QEMU, both cores), new QEMU program(s), unit tests for the shim text and generated
  barriers.
- `juno-examples`: one dual-core example.
- `juno-site`: thread docs and board pages.
- No new Maven dependency.

## Source and derivation

- Source: issue jabrena/juno#317: Decision 2 in the Functional Specification comment, plus the dual-core status comment
  (issuecomment-6802420795), both written in this repository's session on 2026-11-11; reviewed below through five lenses
  (see `design.md`, "Panel review").
- Depends on: change `virtual-thread-continuations` (PR #323). This change is implemented on branch
  `feat/dual-core-carriers`, stacked on it.
- Derivation direction: issue → OpenSpec. Not synchronized back to the issue automatically.
