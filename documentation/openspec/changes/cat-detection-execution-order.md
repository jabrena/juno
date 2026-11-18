# Cat detection (#312): execution order

Recorded 2026-11-18. Written for: the maintainer and the implementers of the four #312 changes.

This note records a panel review of the four changes under `documentation/openspec/changes/`: `esp32-cam-grayscale-capture`, `cat-detection-model-pipeline`, `native-library-injection`, `esp32-cam-cat-detector`. The panel looked at the work from six angles: hardware (ESP32-CAM), ML (TinyML), compiler (Juno backend and intrinsics), plugin (Maven and arduino-cli), risk and licensing, and test. Each change was checked against `main` (`3632a372` and later).

## 1. Corrections made against `main`

| Change | Was | Now | Why |
| --- | --- | --- | --- |
| `native-library-injection` | Generic `--library` for the UNO Q BearSSL branch | Use-gated bundled sketch sources, plus a pinned Library Manager entry | The UNO Q branch was removed in #388. No `--library` argument exists today. |
| `esp32-cam-cat-detector` | `Esp32Cam.getCatDetector()` | `MCU.getCatDetector()` | `Esp32Cam` was removed in #344. `RemovedFacadeReferencesTest` would fail on it. |
| `cat-detection-model-pipeline` | `juno-bearssl` as the precedent | `juno-pico-secure` | `juno-bearssl` was removed with the UNO Q. |
| `esp32-cam-grayscale-capture` | Mode strategy left open | One camera mode per program, rejected at compile time when mixed | Re-initialising the camera at run time was the costly option. |
| `esp32-cam-cat-detector` | 96×96 listed as an alternative | 64×64 only | 96×96 needs 9,216 bytes, more than the 8 KiB Juno arena. |

## 2. Gates (no code)

- **G1. Training dependency.** Approve TensorFlow Java or DJL, or confirm a hand-written exporter. AGENTS.md requires approval for new Maven dependencies. Nothing in `juno-cat-detection` starts before this.
- **G2. Pins.** Choose the TensorFlow Lite Micro Library Manager package and version, and the `esp32:esp32` core version CI uses (U3).
- **G3. Negatives.** Confirm the source and licence of the empty-room and furniture negatives (U5).

## 3. Spikes (run first, in parallel)

- **S-A. Java int8 export** (desk, no board). Kill switch for `cat-detection-model-pipeline`. Needs G1. If it fails, the hand-written exporter becomes the largest task in the change.
- **S-B. Grayscale frame** (hardware, about 1 h). Initialise the OV2640 in `PIXFORMAT_GRAYSCALE` at QQVGA and print `len` and a checksum. Decides the pixel source for `esp32-cam-grayscale-capture`.
- **S-C. TensorFlow Lite Micro link and size** (`arduino-cli`, no Juno). Build a dummy model on `esp32:esp32:esp32cam` with the G2 pins. Measure compile time, sketch size, and that the array stays in flash. Hardware run prints arena bytes and ms per inference. Decides the arena budget and the 64×64 versus 48×48 question.

## 4. Build order

| Order | Work | Needs | Parallel with |
| --- | --- | --- | --- |
| 1 | Spikes S-A, S-B, S-C | G1 (S-A), G2 (S-C) | each other |
| 2 | `esp32-cam-grayscale-capture` | S-B | `cat-detection-model-pipeline`, `native-library-injection` |
| 2 | `cat-detection-model-pipeline` (train, export, commit `.tflite`) | S-A, G1, G3 | grayscale, native |
| 2 | `native-library-injection` (bundled source, pinned entry) | S-C | grayscale, model pipeline |
| 3 | `esp32-cam-cat-detector` on a dummy model | grayscale API, native mechanism, S-C | model pipeline (still training) |
| 4 | `esp32-cam-cat-detector` with the real model | model pipeline | none |
| 5 | Example, `juno:verify`, hardware check, domain-shift check, docs | 4 | none |

**Freeze points.** The `CatDetector` and `Camera.captureGrayscale` signatures freeze before step 2 ends. Step 3 must not start on an unfrozen API.

**Critical path.** S-A → `cat-detection-model-pipeline` → real-model swap → hardware check. The model pipeline is the long pole: training takes 10 to 65 minutes, and a hand-written exporter would add to it.

## 5. Dissent (recorded, not resolved)

- **Plugin owner:** prefers a generic `--library` path now. The consensus defers it until a library cannot be shipped as sketch source.
- **Hardware:** the 64×64 buffer takes half of the 8 KiB arena. The consensus keeps 64×64 and measures it in S-C, with 48×48 as the fallback. This is not settled until S-C reports.
- **ML:** the 100 KB model may not generalise to ESP32-CAM frames. The domain-shift check stays as the last step, and it can send board frames back to training.

## 6. Open decisions for the maintainer

1. Approve G1 (the training dependency).
2. Choose the G2 pins.
3. Confirm the G3 negatives.
4. Confirm the capability name (`CAT_DETECTION` or a broader `TINYML`), which is still open in `esp32-cam-cat-detector/design.md`.
