## Context

After change `virtual-thread-continuations`, every board runs Juno's virtual threads on one carrier. On the dual-core boards:

| Board | Chip | Today | Second core today |
|---|---|---|---|
| Nano ESP32, M5Stack Cardputer | ESP32-S3, 2 × Xtensa LX7 | one carrier on core 1 (the Arduino loop's core), FreeRTOS tasks sharing its stack, switcher task | Wi-Fi/BLE stack, core-0 idle task watched by the task watchdog (5 s) |
| ESP32-CAM | ESP32, 2 × Xtensa LX6 | as above | as above |
| Pico 2 W | RP2350, 2 × Cortex-M33 | one carrier on core 0, SysTick tick, switcher context (R4 port) | unused by Juno |

Facts checked in the code for this design:
- Generated code references only `juno_gc_stack_top` among the runtime's globals. The current slot, pending exception
  and `ScopedValue` bindings are touched only by the runtime, so making them per-carrier needs no code-generation change.
- The common runtime keeps 11 mutable static tables in 9 files (`runtime-string-slots`, `http-server`,
  `http-server-headers`, `sd`, `ble-central`, `parallel-bus`, `io-owners`, `local-date-time-config`,
  `thread-scheduler`), all written on the assumption of one Java thread at a time.
- `long`/`double` fields are made atomic by masking interrupts around their two word accesses (`FieldAccessLowering`),
  which does not exclude the other core.
- On the ESP32 boards, `CONFIG_ARDUINO_RUNNING_CORE` is 1 and the task watchdog checks the core-0 idle task
  (`CONFIG_ESP_TASK_WDT_CHECK_IDLE_TASK_CPU0`, 5 s).

## Panel review of the plan (issue #317, dual-core comment)

**1. Problem framing.**
- **Problem:** the plan was framed as "start a second carrier and make the scheduler safe". The real scope is the whole
  runtime: every shim helper with static state becomes a data race once two cores run Java.
- **Success criterion:** two CPU-bound virtual threads measurably run in parallel on each dual-core board. Nothing that
  works on one carrier today may regress, including device I/O and the risk report.

**2. Root cause analysis.**
- **Five whys:** the runtime is unsafe on two cores → its state is global → it was designed around the baton, which
  serializes everything → there was only ever one execution context → single-core boards came first.
- **Constraint:** making each of the 11 tables (and the scheduler, allocator, monitors and atomics) individually
  lock-free or finely locked is the expensive, error-prone path.
- **Decision:** serialize the runtime with **one lock** (D3). Let only generated Java code run in parallel, which is
  where CPU-bound parallelism lives anyway.

**3. Assumption analysis.**

| Assumption | Validation |
|---|---|
| Espressif QEMU runs both ESP32-S3 cores. | Spike 1.1. |
| A FreeRTOS task pinned to core 0 at the loop task's priority does not starve the radio stack. The Wi-Fi tasks have higher priority. | Hardware Wi-Fi example on a Nano ESP32. |
| The Pico 2 W's CYW43 Wi-Fi and the arduino-pico core accept calls from core 1. | **Unknown.** If not, device-reaching threads are pinned to core 0 (D2). Spike 1.2 on hardware. |
| Runtime calls are short except blocking calls, so one runtime lock costs little. | Measure lock hold time and contention on the dual-core example. |

**4. Context mapping.**
- **Affected:** the ESP32 and RP2350 thread ports, the shared scheduler, the allocator and collector, atomics,
  `FieldAccessLowering` and barrier emission in both the Thumb-2 and Xtensa backends, the risk analyzer, `XtensaRunIT`,
  the examples and the site.
- **External:** ESP-IDF FreeRTOS SMP (`xTaskCreateStaticPinnedToCore`, `portMUX` spinlocks), arduino-pico's multicore
  API (core-1 start, FIFO, `idleOtherCore` during flash writes), the RP2350 SIO hardware spinlocks.

**5. Quality attributes, in priority order.**
1. Correctness across cores: no lost objects, no torn 64-bit values, no data race inside the runtime.
2. No regression for single-carrier behaviour and single-core boards (byte-identical output there).
3. Parallel speed-up for CPU-bound virtual threads.
4. Diagnosability of placement and pinning.
5. RAM cost of the second carrier.

**Changes the review made to the plan:**
- Add a **runtime lock** instead of making each structure multi-core safe one by one.
- **Pin device-reaching threads** to a core.
- Add the **ESP32 idle-task watchdog** constraint.
- Replace interrupt masking for 64-bit fields with a **spinlock**.
- Add **validation spikes** before porting.

## Goals / Non-Goals

**Goals:**
- Two carriers, one per core, on the Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W.
- CPU-bound virtual threads run in parallel.
- Everything in `virtual-thread-continuations` keeps working.
- UNO R4 WiFi, UNO Q and AVR output unchanged.

**Non-Goals:**
- A lock-free or fine-grained concurrent runtime.
- Migrating a virtual thread between carriers.
- More than two cores.
- Running `main` on the second core (it stays a platform thread on its own core).
- Changing the deferred preemption policy.

## Decisions

### D1. Carriers per core, from the core runtime

`CoreRuntime` reports a carrier count (1 or 2). The ESP32 family and RP2350 report 2; all others report 1 and keep
today's code paths and generated text.

The build-time compatibility property `juno.carriers` may lower that count to 1. It exists for test pipelines whose
emulator cannot execute two cores; it is absent by default and therefore never disables a physical board's second
carrier accidentally. `RuntimeConfig` parses and validates the property once, and both code generation and diagnostics
use the resulting effective count. The CI/toolchain owner removes the override from a pipeline when its emulator gains
reliable dual-core support.

- **ESP32:**
  - Two carrier stacks, each with its own switcher task, pinned to core 0 and core 1. Continuations on a carrier are
    FreeRTOS tasks pinned to that carrier's core and sharing its stack.
  - **Carrier 0** runs on core 0 alongside the radio stack. It yields one tick at least once per second, so the core-0
    idle task feeds the task watchdog.
- **RP2350:**
  - The port starts core 1 through arduino-pico's multicore API, with its own carrier stack, switcher stack and SysTick
    tick (each Cortex-M33 core has its own SysTick).
  - Carrier 0 stays on core 0 with `main`.
- *Alternative rejected:* one carrier plus offloading CPU-bound work to the second core by explicit API. It doesn't
  match the virtual-thread model.

### D2. Placement with carrier affinity

- A new virtual thread or fork goes to the carrier with the fewest live threads, and never migrates.
- **Device threads:** the linker knows which intrinsics each `Runnable`'s entry can reach. A thread that can reach a
  radio, network, BLE or other device intrinsic whose core library requires a specific core is placed on that core's
  carrier, and the risk report says so.
  - ESP32: either core, since ESP-IDF is SMP-safe for these APIs (to confirm in 1.1).
  - Pico 2 W: core 0 until spike 1.2 proves otherwise.
- *Alternative rejected:* runtime migration. Frames hold absolute stack addresses, so migration needs relocation.

### D3. One runtime lock

- Every shim entry point called from generated code takes a recursive runtime lock and releases it on return.
- The lock is released while a thread parks:
  - in the scheduler (sleep, join, wait, monitor contention, latch, semaphore and similar);
  - in a blocking device call, through the existing `JunoIoGuard` entry/exit points;
  - on the UNO Q-style I/O offload.
- **Lock implementation:**
  - ESP32: a `portMUX_TYPE` spinlock guarding a recursive owner/depth pair, falling back to a FreeRTOS wait when
    contended.
  - RP2350: an SIO hardware spinlock guarding the same pair.
- The lock makes allocation, monitors, locks, latches, semaphores, atomics, runtime strings, big numbers, HTTP, SD, BLE
  and I/O ownership tables safe without changing them one by one.
- *Alternative rejected:* per-structure locks or lock-free tables. That's 11+ tables, high risk and little gain for
  CPU-bound parallelism.

### D4. Per-carrier runtime state

- `juno_current_slot`, `juno_pending_exception`, `juno_scoped_top`, `juno_slice_start` and the preemption flags become
  arrays indexed by carrier. They're read through accessors that use the core id (`xPortGetCoreID()` on ESP32, the SIO
  `CPUID` register on RP2350).
- On single-carrier boards the accessors are compile-time constant index 0, so their generated text doesn't change.

### D5. Stop-the-world collection

- Collection runs inside `juno_alloc`, which holds the runtime lock. The collector raises a stop request and waits until
  the other carrier is stopped, which happens in one of three ways:
  - it acknowledges at its next safepoint;
  - it is already waiting for the runtime lock (it spilled its registers before waiting);
  - it is inside a blocking device call that holds no arena pointer it may write (marked by `JunoIoGuard`).
- The stopped carrier records its stack pointer (registers spilled), so the collector scans its live range like a parked
  thread's.
- Safepoints:
  - ESP32: the existing backedge and method-entry safepoints check the stop flag.
  - RP2350: the per-core preemption tick checks it, and the trampoline has already saved the interrupted registers.

### D6. Memory model across cores

- `volatile` loads and stores, and monitor enter/exit, emit barriers on dual-carrier boards: `dmb ish` (Cortex-M33) or
  `memw` (Xtensa).
- 64-bit `long`/`double` field accesses take a short spinlock (the same hardware spinlock or `portMUX`) instead of
  masking interrupts. ARMv8-M Mainline has no doubleword exclusives, and Xtensa has no 64-bit atomics.
- Programs without threads, and single-carrier boards, keep today's code.

### D7. Board constraints

- **ESP32:** the core-0 carrier yields periodically (D1). Collection and freeze/thaw never run inside an interrupt.
- **Pico 2 W:** arduino-pico pauses the other core while writing flash (`idleOtherCore`). A carrier paused that way
  counts as stopped for D5 only if it is at a safepoint; otherwise the collector waits for it to resume.

## Risks / Trade-offs

- [Runtime-lock contention limits speed-up] → acceptable: the goal is parallel Java computation, not a parallel runtime.
  Measure in the example.
- [Intermittent races found only on hardware] → long-running dual-core example with contended monitors, atomics,
  collections and 64-bit volatiles; repeated runs.
- [The core-0 carrier starves the radio or the watchdog] → periodic yield; Wi-Fi hardware test.
- [Pico 2 W Wi-Fi only from core 0] → placement pins device-reaching threads (D2); spike 1.2.
- [No QEMU for the Pico 2 W's second core] → hardware validation only.
- [Changed output for threaded programs on dual-core boards] → expected; single-core and AVR output stay
  byte-identical.

## Migration Plan

The second carrier replaces the single carrier on physical dual-core boards. Pipelines whose emulator cannot execute
both cores pass `-Djuno.carriers=1`, preserving the established single-carrier runtime while still exercising the
generated program. The property defaults to the core's carrier count, accepts only 1 or 2, and cannot request more
carriers than the selected board provides. Remove the pipeline override once its emulator reliably supports the
dual-core runtime; rollback for physical boards remains a revert.

## Open Questions

- Does the Pico 2 W's CYW43 Wi-Fi accept calls from core 1? (spike 1.2)
- Does Espressif QEMU schedule both ESP32-S3 cores well enough to expose races in CI? (spike 1.1)
- Contention cost of the runtime lock on real workloads (MinerGame-style).
