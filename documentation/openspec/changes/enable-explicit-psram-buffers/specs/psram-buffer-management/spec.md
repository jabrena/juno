## ADDED Requirements

### Requirement: Juno PSRAM support is explicit and target-gated

Juno SHALL expose the static PSRAM API only for programs whose declared targets all support it in this change: Arduino Nano ESP32 and AI Thinker ESP32-CAM. The compiler SHALL record nominal capacities of 8 MiB and 4 MiB respectively and SHALL reject reachable PSRAM operations for every other target.

#### Scenario: Supported target uses PSRAM

- **WHEN** a program declaring only Nano ESP32 and/or AI Thinker ESP32-CAM targets reaches a PSRAM operation
- **THEN** the linker accepts the capability and the generated runtime uses the target's external PSRAM allocator

#### Scenario: Unsupported target reaches PSRAM

- **WHEN** a program declaring any unsupported target reaches a PSRAM operation
- **THEN** compilation fails with a PSRAM-specific unsupported-board diagnostic and emits no PSRAM helper for that build

### Requirement: Nominal and runtime capacity have distinct meanings

The API SHALL distinguish nominal board capacity from runtime detected total bytes, current free bytes, and the largest currently allocatable contiguous block. Runtime queries SHALL use the same external-memory capabilities as allocation and SHALL return nonnegative values, including zero when unavailable.

#### Scenario: Runtime availability is lower than nominal capacity

- **WHEN** core, camera, or application allocations leave less PSRAM than the board's nominal capacity
- **THEN** detected/free/largest queries report allocator state rather than the nominal 8 MiB or 4 MiB figure

#### Scenario: Fragmentation limits one allocation

- **WHEN** total free PSRAM exceeds the largest contiguous free block
- **THEN** the largest-block query reports the maximum current single request independently of total free bytes

### Requirement: Allocations are external-only and caller-owned

The static `Psram` API SHALL allocate every successful buffer as zero-initialized storage with external-PSRAM and byte-accessible capabilities. It SHALL return a positive opaque handle on success and a named negative result for invalid size, unavailable PSRAM, exhausted PSRAM, or the live-handle limit. It SHALL never retry in generic or internal SRAM.

#### Scenario: Valid allocation succeeds

- **WHEN** a caller requests a positive size that fits the largest block and a handle slot is free
- **THEN** allocation returns an exclusive positive handle backed only by PSRAM with at least 4-byte base alignment and every byte initially zero

#### Scenario: Released storage is reused

- **WHEN** the allocator reuses PSRAM previously owned by a released handle
- **THEN** the new handle observes zero-initialized bytes rather than data left by the former owner

#### Scenario: PSRAM is unavailable or exhausted

- **WHEN** the runtime detects no usable PSRAM or the requested size cannot fit the largest block
- **THEN** allocation returns the corresponding documented negative result, creates no handle, and consumes no internal-SRAM fallback payload

#### Scenario: Handle table is full

- **WHEN** 16 live PSRAM handles already exist and another otherwise valid allocation is requested
- **THEN** allocation returns the handle-limit result without allocating a payload

### Requirement: Handles reject stale and invalid ownership

Handles SHALL contain no native address and SHALL be validated against internal-SRAM metadata on every operation. Release SHALL invalidate a handle and free its payload exactly once. Positive handle tokens SHALL not be reused during one program run.

#### Scenario: Released handle is used again

- **WHEN** a caller reads, writes, queries, or releases a released handle
- **THEN** the operation returns the invalid-handle result and changes no allocation or payload

#### Scenario: Slot is reused after release

- **WHEN** a released registry slot later holds a new allocation
- **THEN** the old handle remains invalid and cannot read, write, query, or release the new allocation

#### Scenario: One release does not affect another allocation

- **WHEN** one of several live handles is released
- **THEN** its storage becomes reusable while every other handle and payload remains intact

### Requirement: Byte and bulk access are bounds-safe and all-or-nothing

The API SHALL support byte reads/writes and bounded copies between a PSRAM range and a non-null caller-owned Java `byte[]`. It SHALL validate handle, byte value, source range, destination range, negative values, and integer overflow before access. A rejected operation SHALL transfer or modify zero bytes.

#### Scenario: Complete bulk write and read succeed

- **WHEN** all Java-array and PSRAM ranges fit their respective capacities
- **THEN** the complete requested length is transferred and returned, including arbitrary byte offsets

#### Scenario: PSRAM range is invalid

- **WHEN** an offset or length is negative, overflows, or extends beyond the handle's capacity
- **THEN** the operation returns the out-of-bounds or invalid-argument result and leaves the complete destination unchanged

#### Scenario: Java array range is invalid

- **WHEN** a caller-owned array range is negative, overflows, or extends beyond the array
- **THEN** the operation returns the documented negative result before copying any byte

### Requirement: PSRAM payloads are outside Java memory management

PSRAM payloads SHALL contain bytes only and SHALL NOT be scanned or reclaimed by the conservative collector. Ordinary objects, arrays, strings, runtime strings, arena and GC metadata, stacks, scheduler state, and `juno.Xmx` SHALL remain in internal SRAM with the existing default arena behavior.

#### Scenario: Program opts into a PSRAM buffer

- **WHEN** a supported program allocates a PSRAM handle alongside ordinary Java values
- **THEN** only the handle's byte payload uses PSRAM and ordinary values continue to use the existing internal arena

#### Scenario: Program does not opt in

- **WHEN** an existing program contains no reachable PSRAM operation
- **THEN** its generated assembly, shim, arena layout, `juno.Xmx`, and runtime behavior remain byte-compatible, except that ESP32-CAM reports its corrected nominal PSRAM metadata

### Requirement: Concurrency and DMA limitations are explicit

The runtime SHALL serialize registry metadata mutation. Callers SHALL serialize all accesses sharing one handle, including release, while independent handles MAY be used by separate carriers. Generic handles SHALL be documented as CPU-accessible, not ISR-safe and not implicitly DMA-safe.

#### Scenario: Independent buffers are used by separate carriers

- **WHEN** separate carriers operate on distinct live handles
- **THEN** registry validation preserves allocation isolation without requiring application-level serialization between those handles

#### Scenario: Dual-core carriers invoke PSRAM operations

- **WHEN** two ESP32 carriers invoke PSRAM intrinsics concurrently
- **THEN** each generated intrinsic call is bracketed by the shared Juno runtime lock, so registry metadata and one complete byte-copy operation cannot interleave; a sequence of calls still requires application-level ownership or synchronization when it must be observed as one transaction

#### Scenario: A DMA-dependent API encounters PSRAM data

- **WHEN** a DMA-dependent operation is designed to consume data held in a generic PSRAM buffer
- **THEN** it rejects the handle or uses a documented internal-SRAM staging copy rather than assuming DMA compatibility

### Requirement: PSRAM runtime code is reachability-gated

The compiler SHALL emit PSRAM includes, the 16-entry registry, and helper functions only when a PSRAM intrinsic is reachable.

#### Scenario: ESP32 program does not use the API

- **WHEN** a Nano ESP32, ESP32-CAM, or Cardputer program has no reachable PSRAM operation
- **THEN** its generated shim contains no generic PSRAM registry or helper symbols
