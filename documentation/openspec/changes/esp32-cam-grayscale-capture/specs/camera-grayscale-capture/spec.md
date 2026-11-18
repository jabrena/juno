## ADDED Requirements

### Requirement: Grayscale capture fills a caller-owned buffer

`Camera.captureGrayscale(byte[] pixels, int width, int height)` SHALL wait for the next frame, convert it to 8-bit
grayscale (0 = black, 255 = white), downscale it to `width × height`, and write it row-major, one byte per pixel,
into `pixels`. It SHALL return `true` on success and SHALL NOT allocate in the Juno arena.

#### Scenario: A 64×64 frame is captured

- **WHEN** a started camera is asked for a 64×64 grayscale frame into a 4096-byte buffer
- **THEN** the call returns `true`, and the first 4096 bytes of the buffer hold the downscaled frame

#### Scenario: Repeated captures do not allocate

- **WHEN** a program captures grayscale frames in its main loop into a buffer allocated once at startup
- **THEN** the runtime risk report shows no allocation inside the loop

### Requirement: Grayscale capture reports failure with false

`captureGrayscale` SHALL return `false`, and SHALL NOT panic, in each of these cases: the camera is not started, no
frame arrived, or `pixels.length < width × height`.

#### Scenario: Camera not started

- **WHEN** `captureGrayscale` is called before `begin()` succeeded
- **THEN** it returns `false`

#### Scenario: Buffer too short

- **WHEN** `captureGrayscale` is called with a buffer shorter than `width × height`
- **THEN** it returns `false` and does not write past the end of the buffer

### Requirement: Grayscale capture is gated and leaves existing output unchanged

The grayscale intrinsic SHALL compile only for boards with `Capability.CAMERA`. Other boards SHALL reject it at
compile time with a clear `CompileException`. Its shim code SHALL be emitted only when a program uses it. Every
existing example's generated `.S`, `Shim.cpp` and report SHALL stay byte-identical, and `capture(byte[])` SHALL keep
its current behaviour.

#### Scenario: A board without a camera rejects the call

- **WHEN** a program calling `captureGrayscale` is compiled for a board without `Capability.CAMERA`
- **THEN** compilation fails with a `CompileException` that names the missing capability

#### Scenario: The JPEG capture example is unchanged

- **WHEN** `AiThinkerEsp32CamCapture` is regenerated
- **THEN** its `.S`, `Shim.cpp` and report are byte-identical to the files from before the change

### Requirement: Grayscale capture is verified on hardware

A grayscale example SHALL pass `juno:verify` for `ai-thinker-esp32-cam`. On a real board, after `juno:upload`
followed by `juno:monitor`, it SHALL print the frame size, a checksum, and the time per frame.

#### Scenario: Stable QQVGA grayscale frames

- **WHEN** the example runs on an AI Thinker ESP32-CAM pointed at a static scene
- **THEN** the serial output reports the expected frame size and checksums that stay stable across frames
