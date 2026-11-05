## 1. Fix (PR: `fix(runtime)`)

- [ ] 1.1 Add `${JUNO_GC_REGISTER_SPILL}` at the start of `juno_gc_mark` in `common/allocator.cpp`, before `stackMarker` is taken.
- [ ] 1.2 Add `default String gcRegisterSpill()` (empty) to `CoreRuntime`; override it in `Esp32CoreRuntime` with `xthal_window_spill();` and the `<xtensa/hal.h>` include. Substitute it in `AllocatorRuntime`.
- [ ] 1.3 `CoreRuntimeTest`: the hook is empty for every non-Xtensa core and non-empty for both ESP32 cores.
- [ ] 1.4 `JunoCompilerTest`: the Nano ESP32 shim contains the spill before the scan; the UNO R4 WiFi shim does not.
- [ ] 1.5 Make sure `GeneratedAsmToolchainTest` stubs provide `xthal_window_spill` for the Xtensa harness.

## 2. Regression program

- [ ] 2.1 Add a conformance or QEMU program: references live in locals across a call chain of at least eight frames while the program allocates until a collection runs; print a checksum of every object.
- [ ] 2.2 Run it under `XtensaRunIT` and on a Nano ESP32 (`juno:upload` + `juno:monitor`, after asking first).

## 3. Verification

- [ ] 3.1 Example `.S`/`Shim.cpp`/report diff: only Xtensa programs that allocate change.
- [ ] 3.2 `./mvnw test` green.
- [ ] 3.3 Archive with `close-spec`.
