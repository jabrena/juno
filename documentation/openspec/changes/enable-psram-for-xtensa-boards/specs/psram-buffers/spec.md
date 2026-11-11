## ADDED Requirements

### Requirement: PSRAM capability is explicit and target-gated

The compiler and runtime SHALL identify PSRAM support only for the Arduino Nano ESP32 and AI Thinker ESP32-CAM, with nominal capacities of 8 MB and 4 MB respectively. The runtime SHALL query actual availability and SHALL NOT assume the nominal capacity is fully usable.

#### Scenario: Nano ESP32 reports runtime PSRAM capability

- **WHEN** a Nano ESP32 program queries or allocates PSRAM
- **THEN** the runtime uses the platform PSRAM allocator and reports the actual available result rather than only the nominal 8 MB capacity

#### Scenario: Unsupported board rejects PSRAM use

- **WHEN** a program uses the PSRAM capability while targeting a board other than Nano ESP32 or AI Thinker ESP32-CAM
- **THEN** compilation fails with a clear unsupported-board diagnostic and does not emit ESP32 PSRAM helpers

### Requirement: PSRAM buffers are explicit, off-heap, and caller-owned

The API SHALL represent a PSRAM allocation with an opaque primitive handle or equivalent zero-allocation representation. A handle SHALL have explicit allocation and release operations. PSRAM contents SHALL NOT be ordinary Java objects, arrays, strings, references, or conservative-GC arena blocks.

#### Scenario: Program allocates and releases a PSRAM buffer

- **WHEN** a supported-board program requests a valid positive size and later releases the returned handle
- **THEN** the requested storage is allocated from PSRAM, can be accessed through the documented buffer operations, and becomes available for reuse after release

#### Scenario: Ordinary Java allocation remains internal

- **WHEN** a program uses `new`, a Java array, or `juno.Xmx` without opting into a PSRAM handle
- **THEN** the allocation uses the existing internal-SRAM Java arena and the default 8 KiB arena behavior remains unchanged

### Requirement: Buffer lifetime and access are deterministic

The API SHALL define handle validity, alignment, bounds, maximum supported size, release semantics, and caller synchronization requirements. Access through an invalid, released, or out-of-bounds handle SHALL return the documented failure result and SHALL NOT corrupt the Java arena or runtime state.

#### Scenario: Invalid buffer requests fail without fallback

- **WHEN** a caller requests zero or negative size, an invalid alignment, or a size larger than usable PSRAM
- **THEN** allocation returns the documented failure result and does not consume internal SRAM as a fallback

#### Scenario: Released handle is accessed

- **WHEN** a caller accesses or releases a handle after it has been released
- **THEN** the operation returns the documented invalid-handle result and leaves other allocations intact

### Requirement: PSRAM exhaustion and absence are observable

The runtime SHALL distinguish unavailable PSRAM and insufficient free PSRAM from successful allocation. It SHALL never silently fall back to internal SRAM for a PSRAM-only request.

#### Scenario: Board has no usable PSRAM at runtime

- **WHEN** PSRAM initialization or detection reports no usable PSRAM
- **THEN** the capability reports unavailable and every PSRAM allocation returns the documented unavailable result

#### Scenario: PSRAM becomes exhausted

- **WHEN** a valid request cannot fit in currently available PSRAM
- **THEN** allocation returns the documented exhaustion result, preserves existing handles, and does not panic or corrupt the Java arena unless the API explicitly documents that behavior

### Requirement: Generic PSRAM buffers are not implicitly DMA-safe

The API SHALL document generic PSRAM buffers as CPU-accessible storage only. A DMA-dependent operation SHALL reject an incompatible PSRAM handle or use an internal-SRAM staging buffer; it SHALL NOT assume that every PSRAM address satisfies peripheral DMA requirements.

#### Scenario: DMA-dependent operation receives a generic PSRAM handle

- **WHEN** a DMA-dependent operation is given a generic PSRAM handle
- **THEN** the operation returns the documented incompatibility result or performs a documented internal-SRAM copy before starting DMA

### Requirement: Non-opt-in output is compatible

The compiler SHALL emit PSRAM runtime helpers only for programs that use the PSRAM capability or another feature that already requires PSRAM, such as the ESP32-CAM camera path. Programs that do not use PSRAM SHALL preserve their existing generated assembly, shim, reports, arena size, and board behavior.

#### Scenario: Existing Nano program does not use PSRAM

- **WHEN** the same Nano ESP32 program is compiled before and after this change without PSRAM API use
- **THEN** its generated output and default internal-memory behavior remain byte-identical

#### Scenario: Cardputer remains unchanged

- **WHEN** a Cardputer program is compiled without a supported PSRAM capability
- **THEN** the compiler does not treat the shared ESP32 backend as sufficient PSRAM support and preserves the Cardputer's current behavior
