## Context

Juno currently emits one conservative garbage-collected arena as a static internal-RAM array. The collector scans that arena, generated static data, and task stacks; `juno.Xmx` sizes the arena for non-AVR targets. The ESP32-CAM runtime already asks the camera driver for PSRAM frame buffers, but it copies each frame into a caller-owned Java `byte[]`, which remains in the Java arena.

The Nano ESP32 has nominal 8 MB PSRAM and the AI Thinker ESP32-CAM has nominal 4 MB PSRAM. The Arduino ESP32 family also imposes capability, cache, alignment, and DMA constraints: PSRAM is not interchangeable with internal SRAM, and nominal capacity is not the same as usable free capacity.

The expert panel identified the central risk: a second Java-managed arena would require a multi-region conservative-GC design, including cross-region root discovery, pointer validity, cache-disabled behavior, fragmentation, and runtime metadata placement. That is not a safe extension of the current allocator.

## Goals / Non-Goals

**Goals:**

- Provide explicit, opt-in, caller-owned PSRAM buffers on exactly the Nano ESP32 and AI Thinker ESP32-CAM.
- Keep the default Java arena, `juno.Xmx`, stacks, scheduler state, GC metadata, and ordinary Java objects in internal SRAM.
- Define runtime availability, ownership, lifetime, alignment, bounds, failure, concurrency, and DMA behavior.
- Preserve and harden the ESP32-CAM camera path, including frame release and no-partial-copy behavior.
- Keep non-target boards and non-opt-in generated output unchanged.
- Verify the feature with host tests, Arduino CLI compilation, optional QEMU coverage where applicable, and physical-board checks when hardware is available.

**Non-Goals:**

- Moving ordinary Java objects, arrays, strings, records, or the conservative GC arena to PSRAM.
- Changing the meaning or default of `juno.Xmx`.
- Making generic PSRAM buffers DMA-capable for SPI, SD, USB, I2S, or other peripherals.
- Adding PSRAM support to the M5Stack Cardputer, UNO boards, UNO Q MCU side, or Raspberry Pi Pico 2 W.
- Replacing the ESP32-CAM camera driver's ownership model with a Java-managed frame object.

## Decisions

### 1. Use explicit off-heap PSRAM buffers, not a second Java heap

The public API will expose a board-provided PSRAM service and an opaque primitive handle (or an equivalent zero-allocation representation). Allocation, access, and release are explicit. PSRAM contents are not Java references and are not scanned or reclaimed by Juno's garbage collector.

This is preferred over a PSRAM-backed Java arena because it preserves the current collector and `juno.Xmx` contract. A future multi-region Java heap requires a separate design and is explicitly deferred.

### 2. Gate support by exact board target and runtime capability

The compiler will model PSRAM capability for the Nano ESP32 and AI Thinker ESP32-CAM only. The ESP32 core adapter will use the platform's PSRAM-capable allocator and runtime queries for usable capacity. The implementation must not infer support from the shared Xtensa backend or silently use internal SRAM as a fallback.

### 3. Keep the buffer contract CPU-oriented by default

Generic PSRAM buffers are byte-addressable storage for CPU reads/writes and bulk data. They are not promised to satisfy peripheral DMA requirements. A DMA-dependent API must reject an incompatible PSRAM handle or copy through an internal-SRAM staging buffer; this change does not add a generic DMA abstraction.

The API will define explicit alignment, bounds, handle validity, release, and concurrent-access rules. Invalid handles, zero/negative sizes, alignment violations, unavailable PSRAM, and exhaustion have deterministic status results and never fall back to internal SRAM.

### 4. Preserve the ESP32-CAM driver-owned frame path

Camera initialization continues to request driver-owned frame buffers in PSRAM. `begin()` remains idempotent and reports initialization failure. `capture(byte[])` keeps its caller-owned destination contract: it returns the frame length on success, returns failure when the destination is null, unavailable, or too small, never partially copies an oversized frame, and returns every driver frame on all paths.

The new generic PSRAM-buffer service and camera driver buffers remain distinct. A later change may add direct capture into a PSRAM handle, but it is not required here.

### 5. Emit optional runtime code only for opt-in use

PSRAM includes, helpers, and intrinsic/lowering paths are emitted only when a program uses the PSRAM API or the camera capability. Programs that do not use the feature retain their current generated assembly, shim, report, arena size, and behavior. Board-specific allocation stays in the ESP32 runtime adapter; shared lowering does not branch on individual boards.

### 6. Verify in layers

Host tests cover board gating, handle validation, status behavior, lifetime, boundary sizes, and generated-source inclusion. Arduino CLI tests compile representative Nano ESP32 and ESP32-CAM sketches. Physical verification is separate from compilation: when boards are available, a Nano smoke test reports PSRAM availability and repeated allocation/read/write/release, while an ESP32-CAM smoke test calls `begin()`, captures repeatedly, checks JPEG output, and uses `juno:monitor` after upload. Skipped or unavailable hardware is reported as unverified, not passed.

## Risks / Trade-offs

- **[PSRAM is slower and cache-sensitive]** → Keep stacks, GC/runtime state, and hot data in internal SRAM; document performance expectations.
- **[Nominal capacity is not usable capacity]** → Query runtime availability and report allocation failure without promising the full 8 MB or 4 MB.
- **[PSRAM may be incompatible with DMA]** → Mark generic buffers CPU-only and reject or stage DMA-dependent operations.
- **[Explicit handles can leak]** → Require explicit release, document ownership, provide runtime diagnostics where practical, and test repeated allocation/release.
- **[Concurrent tasks may race on a shared handle]** → Define access as caller-synchronized unless a future capability adds synchronization; test invalid/released handles and concurrent independent buffers.
- **[Camera support already partially exists]** → Treat the ESP32-CAM work as hardening and regression protection, not as a duplicate new camera architecture.
- **[Shared ESP32 runtime changes can affect Cardputer]** → Gate emission and runtime behavior by exact board family and retain golden output for non-target boards.
- **[Physical hardware may be unavailable]** → Keep host/toolchain tests independent and distinguish compile verification from hardware verification.

## Migration Plan

1. Add the API contract and board capability metadata without changing default programs.
2. Add ESP32 runtime allocation/status helpers and the ESP32-CAM lifecycle hardening.
3. Add representative examples, host tests, Arduino CLI checks, and documentation.
4. Verify Nano ESP32 and ESP32-CAM hardware when available.
5. Roll back by disabling the opt-in API/runtime helpers; ordinary Java allocation and generated output remain unaffected.

## Open Questions

- Exact public type and status-constant names are implementation details to be selected during coding, provided they preserve the handle, ownership, failure, and DMA contracts in the specifications.
- A future PSRAM-backed Java heap remains an independent design question and must not be smuggled into this change through `juno.Xmx`.
