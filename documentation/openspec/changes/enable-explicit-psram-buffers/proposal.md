## Why

Issue [#316](https://github.com/jabrena/juno/issues/316) identifies useful external RAM on the Arduino Nano ESP32 and AI Thinker ESP32-CAM, but Juno exposes no safe general-purpose PSRAM contract. Explicit off-heap buffers can enable large payloads without changing the conservative garbage-collected arena, while the existing ESP32-CAM path needs lifecycle guarantees before it can be considered reliable.

## What Changes

- Add an explicit, opt-in, zero-allocation static PSRAM API to the Nano ESP32 and AI Thinker ESP32-CAM targets.
- Represent allocations as opaque primitive handles with deterministic allocation, bounds, stale-handle, release, exhaustion, and runtime-availability results.
- Keep PSRAM payloads outside the Java object graph and keep objects, arrays, strings, arena/GC metadata, stacks, scheduler state, and `juno.Xmx` in internal SRAM.
- Require external-PSRAM-only allocation with no internal-SRAM fallback, and expose nominal capacity separately from detected total, current free bytes, and largest contiguous free block.
- Treat generic PSRAM buffers as CPU-accessible, caller-synchronized byte storage that is not implicitly DMA-safe.
- Record nominal 8 MiB and 4 MiB capacities for the Nano ESP32 and ESP32-CAM while limiting Juno API support to exactly those targets in this change.
- Harden the existing ESP32-CAM driver-owned PSRAM frame-buffer initialization, cleanup, retry, capture, and release lifecycle without exposing camera frames as generic handles or changing `Camera.capture(byte[])`'s `-1` failure contract.
- Add a bounded-chunk large-text workflow that uses bytes/UTF-8 without materializing the whole payload as an ordinary `String`.
- Gate generated helpers on reachable use, preserving non-opt-in assembly, shim, arena behavior, and `juno.Xmx`; the corrected ESP32-CAM PSRAM figure is an intentional metadata delta.
- Add host, compiler, Arduino-toolchain, documentation, and conditional physical-board verification.
- Defer a PSRAM-backed Java heap, direct camera-to-PSRAM-handle capture, and broad DMA/peripheral integration to separate changes.

## Capabilities

### New Capabilities

- `psram-buffer-management`: Target gating, runtime capacity queries, generation-checked primitive handles, byte/bulk access, release, failure, concurrency, output-gating, and non-DMA contracts for explicit PSRAM buffers.
- `esp32-camera-psram-lifecycle`: Reliable driver-owned PSRAM frame initialization and caller-owned capture behavior on the AI Thinker ESP32-CAM.
- `psram-large-payload-guidance`: Rules and a representative bounded-chunk workflow for choosing between ordinary strings, internal arrays, PSRAM buffers, and streaming.

### Modified Capabilities

None. The recreated OpenSpec workspace contains no established capability specifications.

## Impact

- `juno-api`: new additive static `Psram` intrinsic class; existing `BoardMemory` and `Camera` contracts remain source-compatible and no PSRAM service object is exposed.
- `juno-compiler`: PSRAM board capability and metadata, intrinsics/lowering, usage-gated ESP32 runtime helpers, fixed internal-SRAM handle metadata, and camera lifecycle hardening.
- Tests and generated fixtures: API, linker, backend, runtime-harness, camera-harness, deterministic-output, and both ESP32 FQBN compilation checks.
- `juno-examples`: explicit PSRAM/large-payload example and corrected camera startup in all camera consumers.
- `juno-site`: memory model, board pages, API guidance, performance, lifetime, failure, and DMA documentation.
- External toolchains: existing Arduino ESP32 cores only; no new Maven dependency or plugin is required.
