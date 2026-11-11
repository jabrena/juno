## ADDED Requirements

### Requirement: Camera frame buffers use PSRAM

The AI Thinker ESP32-CAM camera runtime SHALL request driver-owned frame buffers in PSRAM and SHALL report deterministic initialization failure when the camera or required PSRAM frame allocation is unavailable.

#### Scenario: Camera initializes with usable PSRAM

- **WHEN** `Camera.begin()` is called on an AI Thinker ESP32-CAM with a working camera and usable PSRAM
- **THEN** initialization succeeds and the camera driver owns its configured PSRAM frame buffers

#### Scenario: Camera initialization cannot obtain PSRAM frames

- **WHEN** `Camera.begin()` cannot initialize the camera or allocate the required PSRAM frame buffers
- **THEN** it returns failure without corrupting the Java arena or internal runtime state

### Requirement: Camera lifecycle is idempotent and bounded

`Camera.begin()` SHALL be safe to call repeatedly after successful initialization, and `Camera.capture(byte[])` SHALL preserve the existing caller-owned destination contract. The implementation SHALL use the fixed supported frame configuration and SHALL not allocate Java objects inside the capture loop.

#### Scenario: Camera is started more than once

- **WHEN** `Camera.begin()` is called after it has already succeeded
- **THEN** it returns success without allocating a second camera runtime or leaking the existing frame buffers

#### Scenario: Capture writes into a caller-owned buffer

- **WHEN** a started camera captures a frame into a sufficiently large caller-owned `byte[]`
- **THEN** it copies the JPEG bytes from the driver-owned PSRAM frame, returns the frame length, and leaves ownership of the destination with the caller

### Requirement: Capture never partially copies or leaks frames

The camera runtime SHALL return every driver-owned frame on success and failure. If the destination is null, unavailable, or smaller than the complete JPEG frame, capture SHALL return failure without a partial copy.

#### Scenario: Destination is too small

- **WHEN** the next JPEG frame is larger than the supplied destination buffer
- **THEN** capture returns `-1`, does not expose a truncated frame, and returns the driver-owned PSRAM frame

#### Scenario: Capture fails after acquiring a frame

- **WHEN** a frame is acquired but copying cannot complete
- **THEN** the runtime returns failure, returns the frame to the camera driver, and leaves subsequent capture attempts valid

### Requirement: Camera verification distinguishes compile-time from hardware behavior

The change SHALL include generated-sketch compilation for `esp32:esp32:esp32cam` and a documented physical-board smoke test when hardware is available. The smoke test SHALL call `begin()`, capture repeatedly, verify successful JPEG output, and monitor serial output after upload. Unavailable hardware SHALL be reported as unverified rather than passed.

#### Scenario: Camera sketch compiles with the target core

- **WHEN** the representative Juno camera program is generated and compiled with the AI Thinker ESP32-CAM FQBN
- **THEN** the Arduino toolchain accepts the sketch and the generated runtime contains the PSRAM camera configuration

#### Scenario: Physical camera smoke test runs

- **WHEN** an AI Thinker ESP32-CAM is available and the representative program is uploaded over USB-serial
- **THEN** the program initializes, captures repeated JPEG frames, reports valid output over serial, and the result is checked with `juno:monitor`
