## Context

Juno's thread runtime (`runtime/common/thread-scheduler.cpp`) is one scheduler over `JunoSlot[JUNO_MAX_THREADS]`.
Slot 0 is `main`; every other slot owns a stack for the whole life of the thread that occupies it. Each core runtime
supplies a port through `CoreRuntime.threadPort()`:

| Board | Port today | Preemption |
|---|---|---|
| UNO R4 WiFi (RA4M1, single core) | `renesas/thread-port.cpp`: own stacks, `juno_context_switch` | SysTick tick, asynchronous (`thumb2/preemption.cpp`) |
| Pico 2 W (RP2350, dual core) | reuses the R4 port | SysTick tick, asynchronous |
| UNO Q (STM32U585, single core) | `zephyr/thread-port.cpp`: Zephyr threads + semaphore baton; 2 I/O workers | `k_timer` tick, asynchronous |
| Nano ESP32, Cardputer (ESP32-S3), ESP32-CAM (ESP32) | `esp32/thread-port.cpp`: FreeRTOS tasks on one core + semaphore baton | safepoints (loop backedges, method entries) |
| UNO R3, UNO WiFi Rev2 (AVR) | `avr/thread-port.cpp`: own stacks | safepoints |

Per-thread state lives partly in `JunoSlot` (pending exception, scoped-value bindings, `waitKey`, joining/scope
pointers) and partly in `JunoThreadObject`. Monitors record a slot index as owner. `JunoTaskScope.tasks` is sized
`JUNO_MAX_THREADS - 1`. The collector scans each slot stack conservatively from its saved SP to its top. On the UNO Q,
`juno_io_offload(job, &job)` hands a worker a pointer into the caller's stack (HTTP client, HTTP server accept/respond,
SD free space).

Source: issue jabrena/juno#317, Functional Specification, Decisions 1–11.

## Goals / Non-Goals

**Goals:**

- Any number of virtual threads and forks, bounded by arena space, on the UNO R4 WiFi, UNO Q, Nano ESP32, M5Stack
  Cardputer, ESP32-CAM and Pico 2 W.
- One carrier per core; true parallelism on the dual-core boards.
- Unchanged generated code for methods; unchanged Java API; byte-identical output for programs without threads and for
  AVR boards.
- Today's preemption behaviour preserved.
- OpenJDK parity under QEMU and hardware confirmation on all six boards.

**Non-Goals:**

- AVR boards (UNO R3, UNO WiFi Rev2): keep slot-based threads.
- Migrating a virtual thread between carriers after it starts.
- A compiler CPS / state-machine transform.
- Platform `Thread` API (`new Thread`, builders) — still rejected by `ThreadSupport.validateCall`.
- Deciding the final preemption policy (deferred).
- A feature flag or a parallel slot-based path on the in-scope boards.

## Decisions

### D1. Carriers and the thread model as a core capability

A carrier is a platform execution context with one stack sized by `-Djuno.Xss`: one per core. On the R4 and Pico 2 W
the carrier is Juno's own stack (core 1 on the Pico is started by the port through the arduino-pico core); on the UNO Q a
Zephyr thread; on the ESP32 family a FreeRTOS task pinned to its core. `main` remains a platform thread on its own stack.
Whether a core uses continuations or slots is a `CoreRuntime` capability (like `preemptsAsynchronously()`), never a board
branch in the backend; AVR core runtimes keep the slot model.

*Alternative rejected:* one carrier per board (simpler, but leaves the second core idle).

### D2. Freeze/thaw by stack copying, with carrier affinity

Each virtual thread runs on its carrier's stack. To unmount it, the runtime saves registers as `juno_port_switch` does
today, then copies `[sp, carrier top)` into an arena chunk owned by the thread (reused across switches and grown when
needed; copied lazily, only when another virtual thread takes the carrier). To mount it, the chunk is copied back to the
same addresses on the same carrier. Because frames hold absolute stack addresses (saved SP/FP, pointers to locals passed
into the C++ runtime), a virtual thread is placed on the least-loaded carrier when it starts and never migrated.
On Xtensa, live register windows are spilled (`xthal_window_spill`) before copying.

*Alternative rejected:* compiler CPS/state machines — changes in all three backends, a frame allocation per call to a
method that may park, code growth, and it forces JDK-style scheduling.

### D3. Scheduling policy behind an interface; preemption kept

The scheduler selects the next virtual thread through a policy object; phase 1 keeps time slicing. On Thumb-2 the tick
runs in handler mode and only requests a switch; the freeze happens in thread mode on the way out of
`juno_preempt_trampoline`. On the ESP32 family the switch happens at the existing safepoints. A later JDK-style policy
(tick only between platform threads, virtual threads unmount only when they block) must be a policy change, not a
redesign. Decision deferred until the R4 copy cost is measured (task group 1).

### D4. Per-thread state moves to the virtual thread object

Pending exception, `ScopedValue` bindings, `waitKey`/`notified`, joining target, scope pointer, interrupt flag and the
I/O worker index move from `JunoSlot` to `JunoThreadObject`. `JunoSlot[JUNO_MAX_THREADS]` becomes per-carrier state plus
a run queue of thread objects; `JunoTaskScope.tasks[]` becomes a list through the thread objects. The collector scans
each carrier's live stack, `main`'s stack, and every frozen chunk conservatively.

### D5. StructuredTaskScope forks are virtual threads

`fork` starts a virtual thread on the same carriers; `join` parks the owner. Cancelling a parked subtask drops its
continuation and chunk instead of `vTaskDelete`/`k_thread_abort`; a running subtask is cancelled at its next park or
safepoint. Shutdown policies, deadlines and `firstFailure` keep their semantics.

### D6. Monitors owned by virtual threads

`JunoMonitor.owner` records the virtual thread. A virtual thread may park (sleep, join, wait, I/O, preemption) while
holding a monitor; ownership travels with the continuation. `wait`/`notify` park and wake virtual threads.

### D7. Multi-core safety on dual-core boards

- Stop-the-world: the collecting carrier raises a stop flag; the other carrier acknowledges at its next safepoint, parks
  with registers saved on its stack, and resumes after the collection. ESP32 family: existing safepoints. Pico 2 W:
  checked in that core's preemption tick (each Cortex-M33 core has its own SysTick and the trampoline has already saved
  the interrupted registers) — **to be confirmed by a spike (task 6.2)**.
- A carrier blocked inline in a vendor library counts as stopped while it holds no arena pointer it may write; the
  runtime marks entry/exit of such calls.
- Arena allocation (lock or per-carrier allocation buffer), monitors, atomics (real atomic instructions), the run queue
  and per-carrier globals (`juno_current_slot`, `juno_pending_exception`, `juno_scoped_top`) become multi-core safe.
- `volatile`/`synchronized` emit barriers (`dmb` on Cortex-M33, `memw` on Xtensa).

### D8. UNO Q offloaded I/O uses worker-owned storage

The caller copies its arguments into the worker's fixed job slot (`JunoIoWorker`); the `Client` is constructed inside
the job function on the worker's stack; results are copied out when the virtual thread resumes. Rule: nothing a worker or
a C++ library keeps a pointer to may live on a parked virtual thread's stack. Pinning (the thread stays mounted during
the offload) is a temporary fallback for an unconverted call site only. Java `byte[]` buffers stay valid (non-moving
arena) and alive (referenced from the frozen chunk). The `DRAINING` slot state disappears: a cancelled thread's chunk is
dropped at once. Boards without I/O workers run blocking I/O inline on their carrier, as today.

### D9. Limits and configuration

`-Djuno.maxThreads` is removed from `AbstractJunoMojo` and `RuntimeConfig`; AVR uses `RuntimeLimits.MAX_THREADS`
internally. `-Djuno.Xss` sets the carrier stack size (default to be set from measurements; larger than today's
per-thread stacks). Starting a virtual thread or fork with a full arena panics with
`cannot start virtual thread: arena full`. The risk report shows carrier stack size, estimated frozen-chunk sizes
against arena headroom, and warns when a virtual thread may need more stack than its carrier.

## Risks / Trade-offs

- [Frame-copy cost on every time-slice switch] → lazy freeze, reused chunk, measurement in the R4 proof of concept;
  JDK-style policy available behind D3 if too expensive.
- [Carrier stack must fit the deepest virtual thread] → risk-report warning; `-Djuno.Xss`.
- [Arena pressure from frozen chunks on the R4's 8 KiB arena] → chunk sizes reported; MinerGame used as the reference
  workload.
- [Xtensa register-window spill] → dedicated spike on the Nano ESP32 before porting.
- [Unmount from the Thumb-2 handler-mode tick] → tick only requests; freeze in thread mode; covered by the PoC.
- [Data races and memory-model bugs on two cores] → barriers, multi-core safe runtime, hardware example with a
  contended `synchronized` section; race examples (`WordTearing`, `ReferenceAtomicity`) will show real parallel races.
- [Pico flash writes pause the other core; ESP32 core 0 also runs Wi-Fi/BLE] → documented; stop protocol tolerates a
  paused core.
- [QEMU emulates only the Cortex-M4 path] → hardware confirmation with `juno:monitor` on all six boards before merge.
- [Breaking removal of `-Djuno.maxThreads`] → documented in the release notes and site.

## Migration Plan

Single release, no flag: continuations replace slot-based threads on the six in-scope boards. Build scripts passing
`-Djuno.maxThreads` must drop it. Rollback is a revert of the change's commits.

## Measurements (task 1.1)

R4 carrier port under QEMU (`mps2-an386`, `-icount`), counters in the port:

| Program | Carrier switches through the switcher | Bytes frozen | Peak frozen frame |
|---|---|---|---|
| `VirtualThreads` | 925 | 228,816 | 252 B |
| `TaskScheduling` | 920 | 229,188 | 372 B |
| `StructuredTasks`, `Synchronization`, `BlockingPrimitives` | 3–8 | 0 | — (one thread on the carrier at a time) |

A switch that changes the carrier's thread copies ~250 B out and ~250 B back in: about 500 bytes of
`memcpy`, on the order of 10 µs at 48 MHz (to be confirmed on hardware), i.e. ~1% of a 1 ms time slice.
Switches between `main` and the thread already on the carrier copy nothing (lazy freeze). Time slicing therefore stays
the default; Decision 1 remains deferred. The switcher runs on its own 1 KiB stack; carrier default sizes are revisited
in task 4.1.

## Open Questions

- Final preemption policy (Decision 1, deferred until the R4 copy cost is measured).
- Pico 2 W stop-flag check location (tick vs added safepoints), confirmed by task 6.2.
- Default carrier stack size per core, from measurements.
