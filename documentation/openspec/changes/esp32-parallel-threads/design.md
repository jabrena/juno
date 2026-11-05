## Context

**Today.** `esp32/thread-port.cpp` pins every slot task to the loop core and passes one baton, so one Juno thread runs
Java at a time. Preemption happens only at the safepoints the backend emits (method entry and loop back edges;
`XtensaAsmBackend.java:209-211,494`), and no register holds a value across them. Every runtime structure relies on
the baton:

- the scheduler globals (`thread-scheduler.cpp:65-78`)
- the monitor table (`:342-368`)
- the allocator free list and GC queue (`allocator.cpp:2,26-29`)
- the root scan from parked threads' saved stack pointers (`thread-scheduler.cpp:178-190`)
- the pending exception and the scoped-value top (`exceptions.cpp:35`, `scoped-values.cpp:12`)
- the runtime-string slot cursor
- the device owners (`io-owners.cpp:26-40`)
- atomics (`atomics.cpp`)

**Hardware facts.** The ESP32-S3 `core-isa.h` defines `XCHAL_HAVE_S32C1I 1` and `XCHAL_HAVE_RELEASE_SYNC 1`, so
`l32ai` and `s32ri` exist. `s32c1i` works only on internal SRAM. The arena (`allocator.cpp:1`) and static fields
(`ProgramLayout`, `.section .bss`) are in internal DRAM.

## Goals / Non-Goals

**Goals:**

- Up to two Juno threads run Java at the same time on the ESP32-S3.
- Every JLS chapter 17 guarantee holds: `volatile`, `synchronized`, `wait`/`notify`, atomics, `final` fields,
  `start`/`join`, interrupt, and non-tearing `volatile long`/`double`.
- With `-Djuno.cores=1` (the default), every board's output is byte-identical.

**Non-Goals:**

- More than two cores.
- Other boards. The UNO Q's MCU has a single core, and its Linux cores are not a Juno target.
- A Java API for core IDs or affinity.
- A moving or concurrent collector.

## Decisions

### D1. JMM → Xtensa mapping (applies only when cores > 1)

| Java | Lowering | JSR-133 cookbook barrier |
|---|---|---|
| `volatile` int/ref read | `l32ai` | LoadLoad and LoadStore (acquire) |
| `volatile` int/ref write | `s32ri`, then `memw` | release, then StoreLoad |
| `volatile long`/`double` read and write | `juno_volatile_load64`/`juno_volatile_store64`, each under one of 64 striped `s32c1i` spinlocks hashed on `addr >> 3` | the lock provides atomicity and ordering |
| `AtomicInteger`/`AtomicBoolean` operations | `__atomic_*` built-ins, `__ATOMIC_SEQ_CST` (`memw`, `wsr SCOMPARE1`, `s32c1i` retry loop) | sequentially consistent |
| `AtomicLong` operations | the striped spinlocks | sequentially consistent |
| End of `<init>` for a class with `final` fields | `memw` before `retw` | freeze |
| `monitorenter`/`monitorexit`, `wait`/`notify` | the scheduler spinlock (D4) | acquire on enter, release on exit |
| `Thread.start`, `join`, interrupt | release before the hand-off; `l32ai` on the thread's finished and interrupt flags | happens-before edges |

`l32ai` and `s32ri` have the same offset range as `l32i` (0–1020, scaled by 4), so `XtensaMemoryLowering.reach()`
needs no change. *Alternative rejected:* a seqlock for 64-bit values. Writers still have to exclude each other,
so it gains nothing over a lock.

### D2. Carrying the volatile flag

`IrProgram` gains `Set<FieldRef> volatileFields` and `Set<String> finalFieldClasses`. Existing constructors default
both to empty. They are filled from the declaring `FieldInfo` and exposed through `ProgramLayout.isVolatile`.
`DeadValueElimination` and `CopyPropagation` keep volatile loads. The IR record shapes don't change, so Thumb-2 and
AVR are untouched.

### D3. Execution model: K run tokens

The baton becomes K run tokens, with K = `juno.cores`. Slot tasks are created with `tskNO_AFFINITY`; main stays on the
loop task. A new slot state, `JUNO_SLOT_RUNNING`, keeps `juno_sched_pick` from choosing a slot that is running on the
other token. When the running core is 0, `juno_thread_slice_over` calls `vTaskDelay(1)` about every 100 ms, so IDLE0
runs and the task watchdog stays quiet. Wi-Fi (about 23), lwIP (about 18) and the I/O worker (2) all outrank Java
(1). *Alternative rejected:* N tasks scheduled freely by FreeRTOS. The tick would preempt threads between safepoints
with unspilled register windows, and the polled sleep/join/monitor states would have to be rewritten.

### D4. Per-thread context and locks

- `static __thread JunoSlot* juno_self`. ESP-IDF saves THREADPTR per task, so each access costs about 2
  instructions. It replaces `juno_current_slot`, the global pending exception and `juno_scoped_top`, and the
  `${JUNO_PENDING_SWAP}`/`${JUNO_BINDINGS_SWAP}` code becomes empty. Indexing by core is wrong, because tasks
  migrate.
- **Scheduler spinlock** (`portMUX_TYPE juno_sched_mux`): guards slots, counters, monitors, read-write locks,
  barriers and phasers, and device owners. Monitors use this lock rather than one of their own, so there is a
  single lock order.
- **Heap spinlock** (`juno_heap_mux`): guards `juno_gc_try_allocate`. Memory is zeroed outside it.
  *Alternative rejected:* per-core allocation buffers. With 4 KiB each, a first-fit heap that never moves objects
  fragments badly.
- **Rule:** never allocate, call a semaphore, call `Serial` or panic while holding either spinlock. A spinlock
  section disables interrupts.
- **Monitor blocking** keeps today's polling, now under the scheduler spinlock. FreeRTOS task notifications are a
  later optimisation.
- **Runtime-string slots** become per thread (a ring in `JunoSlot`). The random state, big-number handles and
  `local-date-time` get short spinlock sections.

### D5. Stop-the-world collection

```
alloc slow path:
  if !take(gc_mutex, 0): park_for_gc(); retry          // another thread is collecting
  gc_requested = self + 1; memw
  wait until every RUNNING slot except self is parked   // 50 ms deadline, then panic "[juno-gc] stop timeout"
  xthal_window_spill(); publish own sp
  collect(); gc_requested = 0; memw
  give gc_resume to each parked slot; give gc_mutex

safepoint / back edge:
  if gc_requested: park_for_gc()                        // else the usual slice check

park_for_gc:
  xthal_window_spill(); sp[self] = &marker; set parked bit; take(gc_resume); clear parked bit
```

Slots that are already blocked count as parked: `SLEEPING`, `JOINING*`, waiting on a token or a monitor, and
`WAITING_IO` (from `esp32-io-worker`). Their stack pointer is published before they block. A thread inside a direct
vendor call marks itself `NATIVE` after a window spill, and on return it waits if a collection is under way. I/O
workers never allocate and are not roots. No write barrier is needed: the collector doesn't move objects, isn't
incremental, and stops every mutator for the whole mark and sweep.

### D6. Cooperative cancellation

`StructuredTaskScope` cancellation of a running thread sets a per-slot request flag. The target unwinds at its next
safepoint. The canceller waits until the target is parked, then calls `juno_port_cancel` (`vTaskDelete`). A running
task is never deleted, because it may hold a spinlock.

### D7. Opt-in and byte-identity

- `RuntimeConfig.cores` comes from `-Djuno.cores` (default 1).
- `CoreRuntime.parallelCores()` defaults to 1; `Esp32CoreRuntime` returns 2.
- The compiler raises a `CompileException` if the flag exceeds the capability, or if it is above 1 with fewer than
  3 thread slots.
- With `cores == 1`, every placeholder expands to today's text and `XtensaMemoryLowering` emits today's
  instructions.
- `Esp32CoreRuntime` selects `esp32/thread-port-smp.cpp` and the two-core atomics only when cores > 1. Nothing
  branches on a board name.

### D8. Verification

- **Litmus programs** under `programs/concurrency/litmus/`. Each runs 20,000 iterations, reuses its state in place
  (no allocation per iteration), uses an `AtomicInteger` epoch barrier, and prints lines under 31 characters.
- **Positive tests** print `<T> FORBIDDEN 0`.
- **Negative controls** must report a count above 0 on hardware, or the run is invalid. They are plain-field store
  buffering, a plain counter, and a plain `long` that a writer alternates between 0 and -1L.
- **CI** checks the structure of the generated code, and runs a two-core probe under QEMU. QEMU interleaves the cores
  sequentially, so it can catch deadlocks and lost wake-ups, not weak orderings.
- **Memory-model proof** comes only from hardware transcripts (`juno:upload` + `juno:monitor`) on both boards.

## Risks / Trade-offs

- **`s32c1i` on PSRAM fails silently**, so a lock would spin forever. Mitigation: at startup, check that the arena,
  the statics and the lock table are internal (`esp_ptr_internal`).
- **Collector stop latency** depends on the longest stretch of code without a safepoint. Mitigation: the `NATIVE`
  state for vendor calls, and a 50 ms timeout that panics loudly.
- **A global that is missed** becomes a silent data race. Mitigation: before the default changes, run a grep audit
  for direct reads of `juno_current_slot` and other globals, and the litmus and stress runs on IDF 4.4 (Nano) and
  IDF 5 (Cardputer).
- **Spinlock misuse** trips the interrupt watchdog. Mitigation: the D4 rule, plus debug assertions.
- **Cost:** `memw` takes 10+ cycles, and a monitor's spinlock about 100 cycles. Programs that don't opt in pay
  nothing.
- **More false retention:** two stacks are scanned conservatively.

## Migration Plan

Each step in `tasks.md` is its own branch and PR, and two-core mode stays off by default throughout. Changing the
default is a separate decision, taken after the hardware litmus transcripts are attached. Rollback is
`-Djuno.cores=1`.

## Open Questions

- Should the ESP32-S3 default arena grow from 8 KiB to 16 KiB in two-core mode? The S3 has 512 KiB of SRAM.
- Should the litmus programs use a dedicated hardware runner that compares only the `FORBIDDEN` lines, or print
  OK/FAIL so they fit the OpenJDK transcript runners?
- Would replacing monitor polling with task notifications be worth its cost?
