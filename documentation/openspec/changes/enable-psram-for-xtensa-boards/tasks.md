## 1. API and board model

- [ ] 1.1 Define the public zero-allocation PSRAM service, opaque handle representation, status values, alignment rules, ownership, release semantics, bounds behavior, maximum size, and caller-synchronization contract.
- [ ] 1.2 Add the PSRAM capability and nominal-capacity metadata for exactly the Nano ESP32 and AI Thinker ESP32-CAM; ensure unsupported boards reject the capability.
- [ ] 1.3 Add JSpecify annotations, board facade getters, native declarations, intrinsic registry entries, and lowering/runtime feature registration required by the selected API.

## 2. ESP32 runtime implementation

- [ ] 2.1 Implement runtime PSRAM detection, usable-capacity reporting, explicit allocation/release, validation, and deterministic failure results using the ESP32 capability-aware allocator.
- [ ] 2.2 Keep the Java arena, `juno.Xmx`, stacks, GC metadata, scheduler state, and other internal-SRAM-only state unchanged; never silently fall back to internal SRAM for PSRAM-only requests.
- [ ] 2.3 Gate PSRAM helpers by exact board target and opt-in use so the M5Stack Cardputer and non-PSRAM boards retain their current generated output and behavior.
- [ ] 2.4 Document and enforce the CPU-only default for generic PSRAM buffers; reject or stage operations that require DMA-capable internal memory.

## 3. ESP32-CAM camera reliability

- [ ] 3.1 Harden camera initialization to verify the required PSRAM-backed frame-buffer setup and return deterministic failure without corrupting Juno runtime state.
- [ ] 3.2 Make camera initialization idempotent and guarantee driver-frame release on success, null/invalid destination, undersized destination, copy failure, and no-frame paths.
- [ ] 3.3 Preserve caller-owned JPEG capture semantics and no-partial-copy behavior; update the camera example to call and check `begin()` before its capture loop.

## 4. Tests and generated output

- [ ] 4.1 Add unit tests for board gating, nominal versus runtime capacity, unavailable PSRAM, exhaustion, invalid sizes, alignment, invalid/released handles, repeated release, and independent buffer reuse.
- [ ] 4.2 Add tests proving ordinary Java objects remain in the existing arena and that the default `juno.Xmx` and non-opt-in generated `.S`, `Shim.cpp`, and reports remain unchanged.
- [ ] 4.3 Add camera tests for initialization failure, repeated `begin()`, null and undersized destinations, no partial copy, repeated capture, and guaranteed frame release.
- [ ] 4.4 Compile representative Nano ESP32 and AI Thinker ESP32-CAM generated sketches with `arduino:esp32:nano_nora` and `esp32:esp32:esp32cam`; report skipped toolchains separately from passed checks.
- [ ] 4.5 When physical boards are available and explicitly approved, upload over USB serial and follow each upload with `juno:monitor`; verify Nano allocation/read/write/release and ESP32-CAM repeated JPEG capture.

## 5. Documentation and validation

- [ ] 5.1 Update the board and memory-model documentation with internal SRAM versus PSRAM, nominal versus usable capacity, explicit opt-in behavior, lifetime/failure semantics, performance trade-offs, and DMA restrictions.
- [ ] 5.2 Add or update representative examples showing one Nano PSRAM-buffer workflow and the ESP32-CAM lifecycle without allocations inside the capture loop.
- [ ] 5.3 Run the relevant unit, architecture, formatting, compiler, and generated-toolchain checks; run `openspec validate --all` and resolve any validation findings.
