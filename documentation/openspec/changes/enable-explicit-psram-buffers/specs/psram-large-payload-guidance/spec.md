## ADDED Requirements

### Requirement: Ordinary Java text remains in existing storage

String literals and eligible constant tables SHALL retain their existing flash placement. Ordinary `String`, concatenation, `StringBuilder`, formatting results, runtime strings, and Java byte/character arrays SHALL retain their existing internal-SRAM and arena behavior; size alone SHALL NOT migrate them to PSRAM.

#### Scenario: A normal string crosses an implementation threshold

- **WHEN** an ordinary Java string or backing value exceeds any internal size threshold
- **THEN** the compiler/runtime preserves existing string semantics rather than silently placing it in PSRAM

### Requirement: Large payloads use explicit bounded byte chunks

A representative supported-board workflow SHALL encode, write, read, and consume a large text payload as UTF-8 bytes through a PSRAM handle using a reusable bounded internal buffer. It SHALL NOT materialize the complete payload as an ordinary Java `String` or full-size Java array.

#### Scenario: Large text is staged in PSRAM

- **WHEN** the representative workflow produces a payload larger than its reusable internal chunk
- **THEN** it transfers bounded chunks through the PSRAM bulk API and verifies/consumes the payload without a whole-payload Java allocation

#### Scenario: A chunk operation fails

- **WHEN** allocation or a bounded transfer returns a negative result
- **THEN** the workflow reports the failure deterministically, releases any live handle exactly once, and does not continue with partial payload semantics

### Requirement: Documentation guides storage selection

Documentation SHALL distinguish ordinary strings, caller-owned internal arrays, explicit PSRAM buffers, and streaming APIs by size, lifetime, access pattern, latency, DMA, and ownership needs. It SHALL explain nominal versus usable capacity, explicit release, performance/cache trade-offs, and caller synchronization.

#### Scenario: Developer chooses storage for a payload

- **WHEN** a developer consults the memory guidance for a text or binary workload
- **THEN** the guidance explains when to keep a normal `String`, use a small/hot/DMA internal buffer, use a large CPU-only PSRAM buffer, or stream sequential data without retaining it all

### Requirement: Large-payload scope remains primitive and additive

This capability SHALL require only byte and bulk PSRAM access plus example/documentation changes. It SHALL NOT imply direct PSRAM integration with HTTP, TLS, SD, camera, audio, image, machine-learning, or Java string APIs.

#### Scenario: Subsystem needs direct PSRAM integration

- **WHEN** a future subsystem needs to consume a PSRAM handle without a bounded internal copy
- **THEN** that integration requires its own explicit ownership, lifetime, DMA, error, and compatibility design
