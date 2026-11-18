## Context

- `AI_THINKER_ESP32_CAM` (FQBN `esp32:esp32:esp32cam`, Xtensa backend, 8 KiB Juno arena) has `Capability.CAMERA`,
  and does not have `DOUBLE_MATH`.
- Usable memory is about 320 KB of DRAM plus 4 MB of PSRAM. Camera frame buffers already live in PSRAM.
- `Camera`, `Gpio` and `I2c` are interfaces with native board instances, so programs can be unit-tested with mocks.
  `CatDetector` follows the same pattern.

## Goals / Non-Goals

**Goals:**
- Zero-allocation scoring from a caller-owned 64×64 buffer.
- Capability gating.
- Use-gated emission.
- Byte-identical output for the existing examples.
- A measured tensor arena, flash footprint and inference time.

**Non-Goals:**
- A generic `ImageClassifier`.
- Model selection (`-Djuno.model=`); there is one linked model per program.
- The host-side REST/MQTT layer.
- An accuracy gate or a latency budget in v1.

## Decisions

- **Interface + native instance.** `MCU.getCatDetector()` returns `NativeCatDetector`. The detection logic in a
  program can be tested with a mock `CatDetector`.
- **TensorFlow Lite Micro from the Arduino Library Manager**, pinned to an exact version in `InstallDepsMojo`, as
  `native-library-injection` defines. The maintainer chose this over vendoring the sources or using Espressif's
  `esp-tflite-micro`. The model and the wrapper live in a repo-bundled `cat_inference` library, so
  `ExamplesArchitectureTest` is unchanged.
- **Tensor arena outside the Juno arena.** It is allocated by the shim, in PSRAM if needed. Only the 4 KiB image
  buffer counts against `-Djuno.Xmx`.
- **Integer score.** The shim maps the sigmoid output to 0..1000.
- **Input 64×64.** A 96×96 input needs 9,216 bytes, more than the whole 8 KiB Juno arena, so it is ruled out for v1.
  The 4 KiB image buffer is half the arena. The risk report must show headroom. If it is too tight, the fallback is
  48×48 (2,304 bytes), which changes `inputSize()` only, not the API.

## Risks / Trade-offs

- [A2] The chosen Library Manager package may not build against the unpinned `esp32:esp32` core (U3) → pin both
  versions, and validate with a dummy-model `juno:verify` spike before wiring the real model.
- [A5/A6] The arena, frame buffers and runtime flash may not fit, or the model plus runtime may exceed the default
  app partition → measure `arena_used_bytes()`, the flash report and ms per inference on the board. Read the runtime
  risk report (`Arena: N / 8192`) before flashing.
- [A7] Domain shift → score about 100 real frames offline, and feed them back to `cat-detection-model-pipeline` only
  if accuracy drops noticeably.
- Serial lines must stay under the 31-character runtime String limit. The proposed lines do.

## Open Questions

- Which Arduino Library Manager TensorFlow Lite Micro package, at which version, to use (U3).
- The name of the new capability, for example `CAT_DETECTION` versus a broader `TINYML`. The source does not say, so
  it is unresolved.

## Pinned versions and spike results (recorded 2026-11-18)

- **TensorFlow Lite Micro:** `Chirale_TensorFLowLite@2.0.0` (Library Manager, Apache-2.0, generated from TFLM; pinned in
  `InstallDepsMojo` by `native-library-injection`). `TensorFlowLite_ESP32` 1.0.0 does not compile on the core below.
- **`esp32:esp32` core:** 3.3.12, the version the spike was built against. CI installs `esp32:esp32` unpinned.
- **Dummy-model compile spike (task 1.2, compile part):** the upstream 96×96 person-detection model stands in for the cat
  model, which does not exist yet. The sketch uses `MicroMutableOpResolver<5>`, the tensor arena in PSRAM, and one
  `Invoke()` on a zero image. It compiles for `esp32:esp32:esp32cam`: 656,080 bytes of flash (20%), 22,676 bytes of
  globals.
- **Board run (same session, raw `arduino-cli upload`, not `juno:flash`):** `arena_used=82300` bytes, one `Invoke()`
  `ok` in 493,268 µs (about 493 ms) on a zero image, with the 96×96 person model. These are scale references, not cat-model
  numbers. `juno:verify` for the spike is not run: it needs the `cat_inference` shim.
- **Finding:** a 136 KB tensor arena in internal DRAM overflows the link by 37,384 bytes. The arena must live in PSRAM, as
  the Risks section already says.

## Decision: the detector comes from the camera (2026-11-18)

- `Camera.detector(DetectorKind kind)` is the generic method; the API provides one kind, `DetectorKind.CAT`, so the cat
  detector is the only one returned. There is no `MCU.getCatDetector()`. Detectors are a camera feature.
- `ImageDetector` holds the behaviour every detector shares (`begin`, `inputSize`, `score`, `MAX_SCORE`); `CatDetector`
  extends it. A new kind needs its own model and runtime, and a new `DetectorKind` constant.
- No new capability. `Capability.CAMERA` already gates the camera, so the detector is gated with it. This settles the
  capability-name question in Open Questions: nothing new is named.
