Prerequisites: `xtensa-gc-window-spill` (step 1), the `delay(1)` → `juno_io_wait` fix and the I/O worker in
`esp32-io-worker` (steps 2–3). Each section below is its own branch and PR, and two-core mode stays off by default
throughout.

## 4. Concurrency fixtures in the catalog (PR: `test`)

- [ ] 4.1 Move the existing concurrency programs (LostUpdates, VolatileHappensBefore, WordTearing, FinalFieldPublication, WaitNotification, VolatileLongAtomicity) into `conformance/programs/concurrency/` and catalog them, as `concurrency-semantic-conformance` requires.
- [ ] 4.2 Run them under the three emulator runners with today's single-core mode.

## 5. Litmus suite (PR: `test`)

- [ ] 5.1 Add `programs/concurrency/litmus/`: MP, SB, IRIW, LB, AtomicInteger counter, `synchronized` counter, AtomicLong and `volatile long` tearing, final-field publication, wait/notify ping-pong. 20,000 iterations, no allocation per iteration, output lines under 31 characters.
- [ ] 5.2 Add `litmus/neg/` controls: plain-field SB, plain counter, plain `long`.
- [ ] 5.3 Decide on the transcript format (open question in `design.md`); update `concurrency-semantic-conformance` to match.
- [ ] 5.4 Under the baton the suite passes and the controls read 0. Record that as the baseline.

## 6. Xtensa JMM lowering (PR: `feat(xtensa)`)

- [ ] 6.1 Add `volatileFields` and `finalFieldClasses` to `IrProgram`, fill them from `FieldInfo`, and expose `ProgramLayout.isVolatile`.
- [ ] 6.2 Keep volatile loads in `DeadValueElimination` and `CopyPropagation`.
- [ ] 6.3 `XtensaMemoryLowering`: `l32ai` and `s32ri`+`memw` for volatile int/ref; `juno_volatile_load64`/`store64` for long/double; `memw` before `retw` in `<init>` of classes with final fields. All gated on `cores > 1`.
- [ ] 6.4 Two-core atomics source on `__atomic` built-ins, plus 64 striped `s32c1i` spinlocks for 64-bit values.
- [ ] 6.5 Startup check that the arena, statics and lock table are in internal RAM.
- [ ] 6.6 Tests: golden assembly for each lowering with cores = 2; byte-identical output with cores = 1 on every board.

## 7. Per-thread context and locks (PR: `feat(runtime)`)

- [ ] 7.1 Add `RuntimeConfig.cores`, the `juno.cores` plugin property, and `CoreRuntime.parallelCores()` (1 by default, 2 for ESP32). Raise `CompileException` on an invalid value.
- [ ] 7.2 `juno_self` placeholders replace `juno_current_slot`, the pending exception and the scoped-value top in `thread-scheduler.cpp`, `exceptions.cpp`, `scoped-values.cpp` and the safepoint code.
- [ ] 7.3 Scheduler spinlock and heap spinlock placeholders; per-thread runtime-string rings; short spinlock sections for the random state, big-number handles and `local-date-time`.
- [ ] 7.4 Grep audit: with cores > 1, no runtime file reads a former global directly.
- [ ] 7.5 With cores = 1, every placeholder expands to today's text. Verify with the example output diff.

## 8. Two-core ESP32 port (PR: `feat(esp32)`)

- [ ] 8.1 `esp32/thread-port-smp.cpp`: K run tokens, `tskNO_AFFINITY` slot tasks, `JUNO_SLOT_RUNNING`, a core-0 yield about every 100 ms.
- [ ] 8.2 Stop-the-world handshake in `allocator.cpp` and the safepoint code: GC mutex, request flag, parked mask, 50 ms timeout panic, `NATIVE` state around vendor calls.
- [ ] 8.3 Cooperative cancellation in `structured-tasks.cpp`.
- [ ] 8.4 Device owners for Serial, SD, the M5 display and keyboard, and BLE; atomic `juno_io_acquire`/`juno_http_acquire`.
- [ ] 8.5 Tests: ThreadsTest, JunoCompilerTest and GeneratedAsmToolchainTest cases for cores = 2 on both ESP32 cores.

## 9. Evidence (PR: `test`)

- [ ] 9.1 QEMU two-core probe: print the core ID from both workers; if both cores run, run the litmus suite in OK/FAIL mode in CI, otherwise mark it optional with the reason.
- [ ] 9.2 After asking first, `juno:upload` + `juno:monitor` the litmus suite and the allocation stress program on the Nano ESP32 and the Cardputer with `-Djuno.cores=2`. Every positive test must print `FORBIDDEN 0` and every negative control must report a count above 0. Attach the transcripts.
- [ ] 9.3 Re-run the existing threaded examples with `-Djuno.cores=2` and read the runtime risk report.

## 10. Documentation and default (PR: `docs`)

- [ ] 10.1 `memory-model.md`: a "Java Memory Model on two cores" section mapping each happens-before rule to its mechanism; a "parallel cores" column in the concurrency-by-board table.
- [ ] 10.2 `arduino-nano-esp32.md` and `m5stack-cardputer.md`: the flag, a warning that unsynchronized code can now race, and the litmus results.
- [ ] 10.3 Decide, in a separate PR, whether to turn two-core mode on by default for the ESP32-S3.
- [ ] 10.4 Tick #241 and archive this change with `close-spec`.
