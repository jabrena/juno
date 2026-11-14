## Why

`Camera` offers only `begin()` and `int capture(byte[] jpeg)`. `runtime/esp32/camera.cpp` hard-codes
`PIXFORMAT_JPEG` at `FRAMESIZE_QQVGA` (160×120) and only ever initialises the camera once. A Juno program on the AI
Thinker ESP32-CAM therefore never sees pixels, so it cannot feed an image model. The Functional Specification for
issue #312 identifies this as core problem (B). It is a real gap but not a binding one, because the repo controls the
camera configuration.

## What Changes

- New `Camera.captureGrayscale(byte[] pixels, int width, int height)`. It waits for the next frame, converts it to
  8-bit grayscale (0 = black, 255 = white), downscales it to `width × height` row-major into the caller-owned buffer,
  and returns `false` in three cases: the camera is not started, no frame arrived, or the buffer is shorter than
  `width × height`.
- A matching `NativeCamera` intrinsic, an `IntrinsicRegistry`/`IntrinsicLowering` entry, and an ESP32 shim
  implementation, all gated on use.
- `capture(byte[] jpeg)` behaviour is unchanged.
- The existing examples' generated output stays byte-identical.

## Capabilities

### New Capabilities

- `camera-grayscale-capture`: Caller-owned, zero-allocation grayscale frame capture on boards with `Capability.CAMERA`.

### Modified Capabilities

None (no existing OpenSpec capability covers `Camera`).

## Impact

- `juno-api`: `hardware/device/camera/Camera.java`, `hardware/internal/NativeCamera.java`.
- `juno-compiler`: `IntrinsicRegistry`, `IntrinsicLowering`, `Esp32CoreRuntime`, `resources/runtime/esp32/camera.cpp`.
- `juno-examples`: a grayscale capture example.
- `juno-site`: `ai-thinker-esp32-cam.md`.
- Dependencies: none on the other #312 changes. `esp32-cam-cat-detector` depends on this change.

## Source and derivation

- Source: issue https://github.com/jabrena/juno/issues/312. The snapshot was retrieved 2026-11-10T20:20:15Z and holds
  the body plus 2 accessible comments, matching the provider's count of 2. Inputs:
  - The API comment https://github.com/jabrena/juno/issues/312#issuecomment-6777836078, which defines the
    `captureGrayscale` contract.
  - The Functional Specification https://github.com/jabrena/juno/issues/312#issuecomment-6778278178 (§1, §3 A4,
    validation step 2).
- Derivation direction: issue → OpenSpec.
- Per the maintainer, the comment's API supersedes the body's `predict(...)` and `CatCamera` naming.
