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

- **Interface + native instance.** `Esp32Cam.getCatDetector()` returns `NativeCatDetector`. The detection logic in a
  program can be tested with a mock `CatDetector`.
- **TensorFlow Lite Micro from the Arduino Library Manager**, pinned to an exact version, installed through
  `native-library-injection`. The maintainer chose this over vendoring the sources or using Espressif's
  `esp-tflite-micro`. The model and the wrapper live in a repo-bundled `cat_inference` library, so
  `ExamplesArchitectureTest` is unchanged.
- **Tensor arena outside the Juno arena.** It is allocated by the shim, in PSRAM if needed. Only the 4 KiB image
  buffer counts against `-Djuno.Xmx`.
- **Integer score.** The shim maps the sigmoid output to 0..1000.

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
