## Why

Even with grayscale frames and a trained model, a Juno program cannot run a model on the AI Thinker ESP32-CAM:
- No intrinsic, API type or shim performs inference.
- The board has no inference capability, which is core problem (A) in the #312 Functional Specification.

This change delivers the user-visible goal of issue #312: offline cat detection on the board, reported over serial.

## What Changes

- New `juno-api` interface `hardware/device/vision/CatDetector` with:
  - `MAX_SCORE = 1000`
  - `begin()`
  - `inputSize()`, which returns 64 for the first model
  - `int score(byte[] grayscale)`, which returns 0..`MAX_SCORE`, so the program needs no float math
- `hardware/internal/NativeCatDetector` and a new `Esp32Cam.getCatDetector()` accessor.
- A new capability on `AI_THINKER_ESP32_CAM`. `IntrinsicRegistry`/`IntrinsicLowering` entries with a `Linker` gate:
  every other board fails to compile with a clear error.
- A use-gated shim feature that declares two libraries through `native-library-injection`:
  - TensorFlow Lite Micro from the Arduino Library Manager, at a pinned version.
  - A repo-bundled `cat_inference` library (`cat_inference.cpp`/`.h`) that embeds the tracked model as a C array.

  The TFLite Micro tensor arena lives in shim memory (PSRAM if needed), never in the Juno arena.
- Example `AiThinkerEsp32CamCatDetector` in `juno-examples`:
  - prints `score=… ms=…` for every frame;
  - prints `CAT` after 3 frames in a row score at least 850;
  - prints `NO_CAT` when the score falls below the threshold;
  - runs at 115200 baud.
- Hardware verification. A domain-shift check scores about 100 real ESP32-CAM frames offline. Board frames are added
  to training only if accuracy drops noticeably.
- Scope stays narrow: `CatDetector` only. A generic `ImageClassifier` waits until a second model exists.

## Capabilities

### New Capabilities

- `esp32-cam-cat-detector`: On-board, zero-allocation cat scoring on the AI Thinker ESP32-CAM through TensorFlow Lite
  Micro.

### Modified Capabilities

None.

## Impact

- `juno-api`: `Esp32Cam`, a new `vision` package, `hardware/internal`.
- `juno-compiler`: `Board`, `Capability`, `IntrinsicRegistry`, `IntrinsicLowering`, `Linker`, `ShimFeature`,
  `Esp32CoreRuntime`.
- The `cat_inference` library inside `juno-third-parties/juno-cat-detection`.
- `juno-examples` and `juno-site` (`ai-thinker-esp32-cam.md`).
- **Depends on:**
  - `native-library-injection`
  - `esp32-cam-grayscale-capture`
  - `cat-detection-model-pipeline`
- The build-flash-monitor sequence is `juno:verify`, then `juno:upload`, then
  `juno:monitor -Djuno.baudRate=115200`.

## Source and derivation

- Source: issue https://github.com/jabrena/juno/issues/312. The snapshot was retrieved 2026-11-10T20:20:15Z and holds
  the body plus 2 accessible comments, matching the provider's count of 2. Inputs:
  - The API comment https://github.com/jabrena/juno/issues/312#issuecomment-6777836078.
  - The Functional Specification https://github.com/jabrena/juno/issues/312#issuecomment-6778278178.
- Derivation direction: issue → OpenSpec.
- Conflict resolved by the maintainer: the comment's `CatDetector` / `AiThinkerEsp32CamCatDetector` supersedes the
  body's `predict(...)` / `isCat(...)` / `CatCamera`. The issue body has not been edited.
