## Context

Juno currently uses one conservative garbage-collected arena in internal SRAM. `juno.Xmx` fixes that arena's size; generated static data, carrier stacks, scheduler state, and GC metadata also rely on internal memory. `Board.NANO_ESP32` records 8 MiB of PSRAM, while `Board.AI_THINKER_ESP32_CAM` records its nominal 4 MiB.

The ESP32-CAM runtime already requests `CAMERA_FB_IN_PSRAM`, obtains driver-owned frames, copies complete JPEGs into caller-owned Java arrays, and returns the driver frame. Its public examples do not call `Camera.begin()`, and initialization cleanup/retry behavior is not specified or focused-tested.

The Nano and ESP32-CAM use different pinned Arduino ESP32 core lines, so the runtime design must use common ESP-IDF heap-capability APIs. PSRAM is cached, slower than internal SRAM, fragmented at runtime, and not interchangeable with DMA-capable internal memory. Nominal capacity therefore cannot be used as the allocation limit.

## Goals / Non-Goals

**Goals:**

- Expose explicit CPU-accessible PSRAM byte buffers on exactly the Nano ESP32 and AI Thinker ESP32-CAM.
- Preserve ordinary Java allocation, GC, stacks, runtime strings, scheduler state, and `juno.Xmx` in internal SRAM.
- Make allocation, access, release, stale handles, bounds, exhaustion, and unavailable PSRAM deterministic.
- Preserve and harden the ESP32-CAM's driver-owned frame lifecycle and existing Java capture contract.
- Keep optional helpers and metadata usage-gated and verify the real target toolchains.
- Demonstrate large text as bounded UTF-8 byte chunks rather than a PSRAM-backed `String`.

**Non-Goals:**

- A PSRAM-backed Java heap, second GC region, PSRAM Java arrays/objects/strings, or changed `juno.Xmx` semantics.
- Native-pointer exposure, arbitrary memory mapping, or storing Java references in PSRAM.
- Generic DMA-safe PSRAM, ISR access, or direct integration with every network, TLS, SD, audio, image, or ML API.
- Direct camera capture into a generic PSRAM handle.
- PSRAM API support for Cardputer or any non-target board in this change.

## Decisions

### 1. Add a static primitive-only `Psram` API

Add a final `Psram` class whose static operations are compiler intrinsics. There is no board getter, service object, or per-call allocation for the user to manage: callers write `Psram.allocate(...)`, `Psram.read(...)`, and `Psram.release(...)` directly. Runtime-owned allocations such as camera frames remain entirely hidden behind their subsystem APIs.

Do not extend `BoardMemory`: adding PSRAM methods would break external implementations and would mix arena/flash introspection with explicitly managed external storage. Do not expose a singleton service object: the Juno API is intentionally static and primitive-only for board-side code, and the compiler's closed-world tests cover the native mappings without requiring a mock object.

The static API contract is primitive-only:

- availability plus nominal, detected-total, current-free, and largest-free-block byte queries;
- `allocate(int)` returning a positive handle or a named negative error;
- `capacity(int)`, byte read/write, and all-or-nothing bulk copies to/from caller-owned `byte[]` ranges;
- `release(int)` returning zero on success or a named negative error;
- documented constants for invalid argument, unavailable PSRAM, exhausted PSRAM, invalid handle, out of bounds, and live-handle limit.

Queries return nonnegative byte counts. Byte reads return `0..255`; successful bulk reads and writes return the complete requested length; scalar byte writes and release return zero. Every failure is negative and returned by the operation that failed, avoiding a carrier-unsafe global `lastError`.

### 2. Use a fixed generation-checked handle registry

Native pointers never cross into Java. A usage-gated table in internal SRAM holds at most 16 live entries containing pointer, capacity, and a positive token/generation. Handle zero and negative values are never valid.

Every operation validates the live entry and token. Release invalidates the entry before reuse, then frees exactly once. A stale, forged, or double-released handle cannot alias a later allocation. Positive tokens are not reused during one program run; if token space is exhausted, allocation reports the handle-limit error.

The registry serializes allocation/release metadata changes. Callers must serialize all operations that share a handle, especially access racing with release; independent handles may be used by separate carriers. The API is not callable from interrupt context.

This is preferred over pointer handles, which can be mistaken for GC references and cannot reject stale pointers after address reuse, and over Java buffer objects, which would consume the arena and imply GC ownership.

### 3. Allocate with an exact PSRAM capability mask

Allocate zero-initialized storage with `heap_caps_calloc(1, size, MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT)` and release with the matching heap API. Never retry with generic `calloc`/`malloc`, `MALLOC_CAP_DEFAULT`, or internal memory. Zero initialization prevents a new owner from observing stale payload, camera, or library data after heap reuse. Base addresses are at least 4-byte aligned; the API supports byte access and arbitrary byte offsets, so callers do not request custom alignment.

Use the same PSRAM capability mask for detected total, free bytes, and largest free block. Allocation accepts every positive `int` byte count, subject to the largest contiguous block and a free registry slot. It validates all ranges using overflow-safe subtraction before reading or writing, and rejected bulk operations transfer zero bytes.

Nominal capacity remains compiler board metadata: 8 MiB for Nano and 4 MiB for ESP32-CAM. Detected total/free/largest values are runtime allocator state and may be smaller or zero. The largest block, not total free or nominal capacity, determines the largest current request.

### 4. Gate the API and generated runtime by capability and reachability

Add `Capability.PSRAM` to exactly the Nano ESP32 and AI Thinker ESP32-CAM. Linker capability checks reject every reachable PSRAM intrinsic for other boards, including mixed multi-board declarations containing an unsupported target.

Add PSRAM intrinsics, lowering, a `ShimFeature.PSRAM`, and ESP32 runtime helpers without board-specific branches in an assembly backend. The compiler emits the registry, includes, and helpers only when a PSRAM intrinsic is reachable. Non-opt-in `.S`, `Shim.cpp`, arena layout, and `juno.Xmx` behavior remain byte-identical. Correcting ESP32-CAM metadata from absent to 4 MiB intentionally changes its memory metadata only.

### 5. Keep generic and camera ownership models separate

Generic handles own application allocations. The camera driver continues to own its frame buffers; a frame is never exposed as a generic handle.

`Camera.begin()` first confirms usable PSRAM, requests `CAMERA_FB_IN_PSRAM`, and is a no-op returning success after successful initialization. A failed attempt cleans any partial driver state, returns `false`, and leaves the camera eligible for a later retry after resources become available. A null sensor after nominal initialization is treated as failed initialization and cleaned up.

`Camera.capture(byte[])` retains its existing contract: it returns the complete JPEG length or `-1`. Capture before successful initialization, no frame, and an oversized destination all return `-1`; an oversized frame copies zero bytes. Every acquired frame is returned exactly once through a single cleanup path. Camera calls require caller serialization.

Generic allocations and camera buffers compete for the same allocator. Runtime free/largest queries reflect camera use, and documentation tells camera programs to initialize the camera before reserving large generic buffers.

### 6. Treat generic buffers as CPU-only storage

The API guarantees byte-addressable CPU access, not DMA capability, cache-disabled availability, or real-time latency. No native address is exposed, so existing DMA APIs cannot accidentally consume a handle. Any future DMA integration must reject generic handles or stage through explicitly documented internal SRAM.

Large text uses UTF-8 bytes in caller-owned bounded chunks. Ordinary `String`, string literals, concatenation, `StringBuilder`, and runtime-string limits remain unchanged. A size threshold cannot silently migrate a Java value to PSRAM.

### 7. Verify contracts in layers

Host/API tests cover mockability and result semantics. Linker/backend tests cover board gating, intrinsic lowering, and generated-output gating. A native runtime harness with a fake capability allocator covers exact flags, unavailable/exhausted memory, handle/token lifetime, no fallback, range overflow, and independent allocation isolation. A fake camera harness covers success, failure cleanup/retry, idempotence, oversized/no-frame capture, destination preservation, and exactly-once frame return.

Arduino CLI fixtures compile against `arduino:esp32:nano_nora` and `esp32:esp32:esp32cam`; missing toolchains are skipped, never reported as passed. Physical smoke tests remain conditional on hardware availability and explicit upload approval, and every upload is followed by `juno:monitor` transcript verification.

## Risks / Trade-offs

- **[Explicit handles can leak]** → Document ownership, cap live handles at 16, expose deterministic exhaustion, and test repeated allocate/release cycles.
- **[Finite tokens can wrap]** → Never reuse a positive token during a run and fail further allocation if the token space is exhausted.
- **[PSRAM fragments]** → Expose both free bytes and largest block and define the latter as the current allocation ceiling.
- **[Shared-handle races can cause invalid application data]** → Require caller serialization and internally protect registry mutation.
- **[PSRAM is slower and cache-sensitive]** → Keep hot/runtime state internal and document CPU-only bulk-data use.
- **[Zeroing a large allocation adds startup latency]** → Make zero initialization part of the deterministic ownership/security contract and recommend allocating reusable buffers once at startup.
- **[Camera and generic buffers compete]** → Make failures normal and deterministic, expose dynamic capacity, and recommend camera-first initialization.
- **[Two Arduino core lines differ]** → Use common heap-capability APIs and compile fixtures against both pinned FQBN toolchains.
- **[Shared ESP32 runtime changes can affect Cardputer]** → Capability- and reachability-gate emission and retain non-opt-in golden output.

## Migration Plan

1. Add the API, capability, and corrected nominal metadata without changing ordinary allocation.
2. Add usage-gated runtime helpers and intrinsic lowering.
3. Harden camera initialization/capture lifecycle while preserving public return values.
4. Add examples, layered tests, toolchain checks, and source documentation.
5. Run physical USB-serial smoke tests only when boards are available and upload is explicitly approved.

Rollback removes the additive accessors/intrinsics/helpers and restores the old ESP32-CAM report metadata; no persisted format or existing Java allocation needs migration.

## Open Questions

- Confirm during implementation that the exact heap-capability query/allocation declarations are available without compatibility wrappers in both pinned Arduino core lines. If a wrapper is needed, it must preserve the exact capability mask and no-fallback behavior.
