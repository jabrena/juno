## Context

`juno_camera_begin` currently initialises the OV2640 with:
- `PIXFORMAT_JPEG` and `FRAMESIZE_QQVGA`
- `jpeg_quality=12`
- `fb_count=2`
- PSRAM frame buffers
- `CAMERA_GRAB_LATEST`
- a 180° flip

A guard (`juno_camera_ready`) prevents a second init. The board has `BOARD_HAS_PSRAM` and no `DOUBLE_MATH`.

## Goals / Non-Goals

**Goals:**
- A caller-owned grayscale buffer, filled without allocating in the Juno arena.
- Downscaling to an arbitrary `width × height`, with 64×64 as the first consumer.
- `capture(byte[])` behaviour unchanged.

**Non-Goals:**
- Model inference (`esp32-cam-cat-detector`).
- JPEG decoding on the board, unless the spike forces it.

## Decisions

- **Pixel source.** First choice: initialise the sensor in `PIXFORMAT_GRAYSCALE`. Fallback: YUV422, keeping the Y
  channel. Last resort: decode the JPEG. Validation step 2 of the Functional Specification decides between them: the
  spike passes if a grayscale QQVGA frame reports `len == 160*120` with stable values.
- **Downscale in the shim.** Conversion and downscaling run in C++ into the caller's buffer, so no float math or loop
  runs in the Java program.
- **Failure is a `false` return**, never a panic.
- **Mode strategy (decided by the panel, 2026-11-18).** One camera mode per program. A program that calls both
  `capture` and `captureGrayscale` is rejected at compile time with a `CompileException`, so the camera is never
  re-initialised at run time. This rule stands even if the spike falls back to YUV422.

## Risks / Trade-offs

- JPEG mode and grayscale mode conflict, because the camera is initialised once. Switching modes needs
  `esp_camera_deinit` and a fresh init, which costs time and can fragment PSRAM.
- Grayscale conversion on the original ESP32 costs time per frame. It is measured, with no budget in v1.

## Open Questions

- None on the mode strategy. The pixel source is still decided by the hardware spike (task 1.1).
