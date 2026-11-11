## Implementation Tasks

- [ ] 1.1 Spike: confirm Espressif QEMU runs both ESP32-S3 cores with tasks pinned to each, and that a carrier pinned to core 0 runs beside the Wi-Fi stack without tripping the core-0 idle-task watchdog.
- [ ] 1.2 Spike on a Pico 2 W: start core 1 through arduino-pico with its own SysTick tick; check whether CYW43 Wi-Fi calls are safe from core 1; record the outcome in `design.md` (D2).
- [x] 2.1 Add the carrier count to the `CoreRuntime` SPI (2 for the ESP32 family and RP2350, 1 elsewhere); assert byte-identical output for UNO R4 WiFi, UNO Q and AVR.
- [x] 2.2 Make the current slot, pending exception, `ScopedValue` bindings, slice start and preemption flags per-carrier, behind core-id accessors that fold to index 0 on single-carrier boards.
- [ ] 2.3 Add the recursive runtime lock (ESP32 `portMUX`, RP2350 SIO spinlock) around shim entry points called from generated code; release it while parking in the scheduler, in `JunoIoGuard` blocking calls and in the I/O offload.
- [ ] 2.4 Implement stop-the-world collection: stop flag, acknowledgement at safepoints (ESP32) and in the per-core tick (RP2350), register spill and recorded sp for a carrier waiting for the runtime lock or blocked in a device call; scan the stopped carrier's live range.
- [ ] 3.1 Emit barriers for `volatile` accesses and monitor enter/exit on dual-carrier boards (Thumb-2 `dmb ish`, Xtensa `memw`); replace interrupt masking for 64-bit field accesses with a spinlock there.
- [ ] 3.2 Implement placement: least-loaded carrier at start, carrier affinity, and linker-driven pinning of threads whose entry reaches a core-restricted device intrinsic; extend the risk report with carriers per board and pinned threads.
- [ ] 4.1 ESP32 port: second carrier stack and switcher task pinned to core 0; continuations pinned to their carrier's core; periodic yield of the core-0 carrier for the idle-task watchdog.
- [ ] 4.2 RP2350 port: start core 1 with its own carrier, switcher stack and SysTick; tolerate the other core being paused during flash writes.
- [x] 5.0 Add a typed `juno.carriers=1` compatibility override for pipelines without dual-core emulation; keep the board carrier count as the default and cover both paths.
- [ ] 5.1 Add dual-carrier programs to `src/test/qemu/programs` declaring the Nano ESP32 (parallel progress, atomic and monitor counters across cores, collections with both carriers allocating, volatile publication, no torn `volatile long`, exceptions on both cores) and run them in `XtensaRunIT`; add `JunoCompilerTest`/`GeneratedAsmToolchainTest` cases for barriers and the 64-bit spinlock.
- [ ] 5.2 Run `./mvnw test`, `-Pqemu`, `-Psimavr` and `-Pxtensa` (with `-Djuno.Xmx=24k`), and `juno:verify` of the affected examples for the four dual-core boards.
- [ ] 5.3 Add a dual-core example (parallel computation, contended monitor, atomics, collections, Wi-Fi call on one carrier while the other computes) and confirm it with `juno:upload` then `juno:monitor` for at least five minutes on the Nano ESP32, M5Stack Cardputer, ESP32-CAM and Pico 2 W.
- [ ] 6.1 Update `juno-site` thread docs and the four board pages (carriers per board, placement and pinning, runtime lock); regenerate with `./mvnw clean verify -Psite -DskipTests` and run `./mvnw -pl juno-site test`.
- [ ] 6.2 Open one PR stacked on the `virtual-thread-continuations` PR (#323), referencing jabrena/juno#317.
