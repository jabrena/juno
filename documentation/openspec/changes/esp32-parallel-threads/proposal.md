## Why

The Nano ESP32 and the M5Stack Cardputer Adv have two Xtensa LX7 cores, but Juno runs one Java thread at a time
under a baton. The Java Memory Model (JLS chapter 17) holds today only because nothing runs in parallel. If two Juno
threads ran on different cores, the current runtime would break it:

- `volatile` is never lowered: `FieldInfo.isVolatile()` is unused, and the Xtensa backend emits no `memw`, `l32ai`
  or `s32ri`.
- Atomics are plain read-modify-write (`common/atomics.cpp`), and `AtomicLong` is two separate word accesses.
- Monitors are a global table with no lock.
- The allocator, the collector's root scan, the scheduler globals, the pending exception, scoped values,
  runtime-string slots and the device owners are all protected only by the baton.

Issue #241 now asks for true parallel threads, platform and virtual, on both cores, with the JMM guaranteed.

## What Changes

- **Opt-in two-core mode.** A `-Djuno.cores=N` build flag (default 1) and a `CoreRuntime.parallelCores()` capability
  (2 on both ESP32 cores). The compiler rejects the flag with a `CompileException` when it exceeds the capability.
  With `cores == 1`, every board's output stays byte-identical.
- **JMM lowering on Xtensa** when cores > 1:
  - `volatile` reads use `l32ai`; `volatile` writes use `s32ri` then `memw`.
  - `volatile long`/`double` go through runtime helpers guarded by striped `s32c1i` spinlocks.
  - Atomics use `__atomic` sequentially consistent built-ins (`s32c1i`), and `AtomicLong` uses the striped locks.
  - Constructors of classes with `final` fields end with a freeze barrier.
  - The volatile flag reaches the backend through `IrProgram`, and the optimizer no longer deletes volatile loads.
- **Runtime for two running threads:**
  - Two run tokens replace the single baton. Slot tasks may run on either core, and Java on core 0 yields regularly
    so the IDLE0 watchdog is not starved.
  - A `__thread` slot pointer (`juno_self`) replaces `juno_current_slot`, the global pending exception and the
    scoped-value top.
  - One scheduler spinlock guards slots, monitors, read-write locks, barriers and device owners. A separate heap
    spinlock guards allocation.
  - The collector stops the other running thread with a safepoint handshake.
  - Cancelling a running thread becomes cooperative.
  - New device owners cover Serial, SD, the M5 display and keyboard, and BLE.
- **Verification:**
  - A litmus suite (message passing, store buffering, IRIW, load buffering, counters, tearing, final fields,
    wait/notify) with negative controls that must show races on hardware.
  - Structural CI checks on the generated code.
  - A two-core probe under QEMU.
  - Hardware transcripts from both boards.
- **Documentation:** a "Java Memory Model on two cores" section and updates to the board pages.

## Capabilities

### New Capabilities

- `esp32-parallel-threads`: Juno threads run Java on both ESP32-S3 cores at once, and every JLS chapter 17
  guarantee holds.

### Modified Capabilities

- `concurrency-semantic-conformance` (delta still to be written, task 5.3): adds the litmus suite and allows
  OK/FAIL transcripts for programs whose exact outcome counts vary between runs. The change also supplies the
  `programs/concurrency/` fixtures that spec already requires but that the catalog still lacks.

## Impact

- **Compiler:**
  - `classfile/FieldInfo`, `ir/IrProgram`, `ProgramContext`/`FieldLowering`, `ProgramLayout`
  - `optimize/DeadValueElimination`, `CopyPropagation`
  - `backend/xtensa/XtensaMemoryLowering`, `XtensaAsmBackend`
  - `backend/runtime/{ThreadRuntime, AtomicRuntime, AllocatorRuntime, RuntimeConfig}`
  - `spi/CoreRuntime`, `core/Esp32CoreRuntime`
  - the Maven plugin's `juno.cores` property
- **Runtime:**
  - new `esp32/thread-port-smp.cpp` and a two-core atomics source
  - placeholders in `common/thread-scheduler.cpp`, `allocator.cpp`, `exceptions.cpp`, `scoped-values.cpp`,
    `runtime-string-slots.cpp`, `io-owners.cpp`, `structured-tasks.cpp`
  - a safepoint variant of `avr/preemption.cpp` for two cores
- **Memory:** no new Java stacks (every slot already has one). There is a small amount of per-slot state and two
  semaphores. The arena stays at 8 KiB by default; collections are expected about twice as often in two-core mode.
- **Not changed:** no public `juno-api` change, no new Maven dependency, no change to the accepted bytecode subset.
- **Depends on:** `xtensa-gc-window-spill` and `esp32-io-worker`.
- **Size:** about 2 to 4 months, delivered as the PRs in `tasks.md`. Two-core mode stays opt-in until the hardware
  evidence is attached.

## Source and Derivation

- **Requirements source:** GitHub issue [#241](https://github.com/jabrena/juno/issues/241), as rescoped on
  2026-11-05 to include parallel threads with the JMM. The design comes from the 2026-11-05 four-expert panel
  (memory model and code generation, GC and allocator, scheduler and FreeRTOS, verification).
- **Derived artifact:** this OpenSpec change, created 2026-11-05. `design.md` serves as the architecture decision
  record that #241 requires before the arena, collector or scheduler change.
- **Direction:** issue → OpenSpec.
