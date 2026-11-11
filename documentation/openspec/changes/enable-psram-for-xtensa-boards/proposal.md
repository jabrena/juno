## Why

Issue [#316](https://github.com/jabrena/juno/issues/316) identifies useful PSRAM on the Arduino Nano ESP32 and AI Thinker ESP32-CAM, but Juno currently exposes no general PSRAM-buffer contract. The issue was retrieved from GitHub on 2026-11-11; the provider reported zero comments, so the issue description is the complete accessible source snapshot.

The expert review found that moving Juno's conservative garbage-collected arena into PSRAM would be a separate, high-risk memory-management change: the current collector, stacks, runtime metadata, cache restrictions, and DMA capabilities are all designed around internal SRAM. This change therefore delivers explicit PSRAM buffers and reliable camera-buffer handling without changing ordinary Java allocation semantics.

## What Changes

- Add an explicit, opt-in PSRAM buffer capability for the Arduino Nano ESP32 and AI Thinker ESP32-CAM.
- Define PSRAM buffer ownership, lifetime, alignment, bounds, failure behavior, concurrency rules, and DMA limitations.
- Keep ordinary Java objects, arrays, strings, the conservative GC arena, stacks, scheduler state, and `juno.Xmx` in internal SRAM.
- Detect actual runtime PSRAM availability rather than treating the nominal 8 MB or 4 MB capacity as fully allocatable.
- Preserve and harden the AI Thinker ESP32-CAM camera path so driver-owned frame buffers remain in PSRAM and are returned on every path.
- Keep PSRAM behavior gated by the exact board target; the M5Stack Cardputer and all non-target boards remain unchanged.
- Add host tests, generated-sketch/toolchain checks, and documented physical-board verification for the two target boards.
- Document the distinction between internal SRAM, the fixed Java arena, explicit PSRAM buffers, nominal capacity, usable capacity, and DMA-safe memory.
- Defer a PSRAM-backed Java heap or changed `juno.Xmx` semantics to a future change requiring an explicit multi-region GC design.

## Capabilities

### New Capabilities

- `psram-buffers`: Explicit caller-owned PSRAM-backed buffers on the Nano ESP32 and AI Thinker ESP32-CAM, including runtime availability and failure semantics.
- `esp32-camera-psram`: Reliable AI Thinker ESP32-CAM frame-buffer allocation and capture behavior using PSRAM.

### Modified Capabilities

- None. The repository has no existing OpenSpec capability specifications; the two capabilities above establish new contracts.

## Impact

- `juno-api`: a board-gated, zero-allocation API or primitive-handle contract for explicit PSRAM buffers, without making them ordinary GC-managed Java objects.
- `juno-compiler`: board capability metadata, intrinsic/lowering integration if required, ESP32 runtime allocation helpers, camera failure handling, and target-specific emission.
- `juno-compiler` tests: capability, allocation, failure, camera, generated-output, and toolchain coverage.
- `juno-examples`: representative Nano ESP32 and ESP32-CAM fixtures, including the required camera lifecycle.
- `juno-site`: memory model, board matrix, API, and verification documentation.
- Arduino ESP32 toolchains and optional physical hardware: `arduino:esp32:nano_nora` and `esp32:esp32:esp32cam`.

The derivation direction is one-way: issue #316 plus the expert-panel review inform this OpenSpec; implementation and later documentation changes must not silently rewrite the issue.
