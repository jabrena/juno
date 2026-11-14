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

## Risks / Trade-offs

- JPEG mode and grayscale mode conflict, because the camera is initialised once. Switching modes needs
  `esp_camera_deinit` and a fresh init, which costs time and can fragment PSRAM.
- Grayscale conversion on the original ESP32 costs time per frame. It is measured, with no budget in v1.

## Open Questions

- **Mode strategy (unresolved in the source).** Two options:
  - (a) One camera mode per program, chosen at compile time from which capture call the program uses.
  - (b) Re-initialise when the program switches between `capture` and `captureGrayscale`.
  Behaviour when one program uses both calls is undefined until this is decided.
