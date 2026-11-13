## ADDED Requirements

### Requirement: Camera initialization requires driver-owned PSRAM frames

The AI Thinker ESP32-CAM runtime SHALL verify usable PSRAM and initialize the camera with driver-owned `CAMERA_FB_IN_PSRAM` buffers. It SHALL NOT substitute internal frame buffers when PSRAM is unavailable.

#### Scenario: Camera initializes with usable PSRAM

- **WHEN** `Camera.begin()` is called with a working sensor and sufficient usable PSRAM
- **THEN** it returns `true` and the camera driver owns the configured PSRAM frame buffers

#### Scenario: PSRAM or sensor initialization is unavailable

- **WHEN** required PSRAM, driver initialization, or sensor retrieval fails
- **THEN** `Camera.begin()` returns `false`, cleans partial driver state, and leaves the Java arena and generic handles intact

### Requirement: Camera initialization is idempotent and retryable

`Camera.begin()` SHALL return success without reinitialization after a successful call. A failed initialization SHALL leave a clean state from which a later call can retry.

#### Scenario: Begin is repeated after success

- **WHEN** `Camera.begin()` is called after the camera is ready
- **THEN** it returns `true` without allocating another driver instance or frame-buffer set

#### Scenario: Begin is retried after resource failure

- **WHEN** an earlier `Camera.begin()` failed, resources later become available, and `begin()` is called again
- **THEN** the runtime performs a fresh initialization attempt without leaked state from the earlier call

### Requirement: Capture preserves the caller-owned destination contract

`Camera.capture(byte[])` SHALL return the complete JPEG length on success and `-1` on every existing failure condition. It SHALL allocate no Java object in the capture path and SHALL not expose a driver frame as a generic PSRAM handle.

#### Scenario: Complete frame fits

- **WHEN** the camera is ready, a frame arrives, and the non-null destination can hold the complete JPEG
- **THEN** capture copies the complete frame from driver-owned PSRAM, returns its length, and leaves destination ownership with the caller

#### Scenario: Capture occurs before successful begin

- **WHEN** capture is called before the camera becomes ready
- **THEN** it returns `-1` without acquiring or copying a frame

### Requirement: Capture never partially copies or leaks a frame

The runtime SHALL validate complete frame fit before copying and SHALL return every acquired driver frame exactly once on every exit path.

#### Scenario: Destination is too small

- **WHEN** an acquired JPEG is larger than the caller-owned destination
- **THEN** capture returns `-1`, copies zero bytes, leaves the destination unchanged, and returns the frame exactly once

#### Scenario: Driver provides no frame

- **WHEN** frame acquisition returns no frame
- **THEN** capture returns `-1` and remains able to perform a later capture

#### Scenario: Successful frame is copied

- **WHEN** a complete frame is copied successfully
- **THEN** the runtime returns that driver frame exactly once before returning the JPEG length

### Requirement: Camera and generic allocations share observable capacity

Camera frame buffers and generic handles SHALL use the same underlying PSRAM allocator state. Camera initialization failure caused by existing generic allocations SHALL be deterministic, and successful camera initialization SHALL reduce subsequent free/largest queries.

#### Scenario: Generic allocation prevents camera startup

- **WHEN** live generic buffers leave insufficient PSRAM for camera frame initialization
- **THEN** `Camera.begin()` returns `false` without falling back to internal frame storage or invalidating the generic buffers

#### Scenario: Camera consumes PSRAM

- **WHEN** camera initialization succeeds before generic allocation
- **THEN** PSRAM free and largest-block queries reflect the driver-owned frame allocation

### Requirement: Camera verification separates compilation and hardware behavior

The change SHALL compile a focused camera fixture with `esp32:esp32:esp32cam`. When physical hardware is available and upload is explicitly approved, verification SHALL upload over USB serial, call and check `begin()`, capture repeated valid JPEGs, and inspect output with `juno:monitor`; unavailable hardware SHALL remain unverified.

#### Scenario: Camera fixture compiles

- **WHEN** the ESP32-CAM Arduino toolchain is available
- **THEN** the generated sketch compiles with the target FQBN and contains the PSRAM camera configuration

#### Scenario: Hardware is unavailable

- **WHEN** no approved physical ESP32-CAM is available
- **THEN** the hardware smoke test is reported as unverified rather than passed
