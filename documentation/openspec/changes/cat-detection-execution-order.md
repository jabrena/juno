# Cat detection (#312): execution order

Recorded 2026-11-18, updated the same day after checking every named item against `main`. Written for: the maintainer
and the implementers of the four #312 changes.

This note records a panel review of the four changes under `documentation/openspec/changes/`: `esp32-cam-grayscale-capture`,
`cat-detection-model-pipeline`, `native-library-injection` and `esp32-cam-cat-detector`. The panel looked at the work from
six angles: hardware (ESP32-CAM), ML (TinyML), compiler (Juno backend and intrinsics), plugin (Maven and arduino-cli),
risk and licensing, and test.

## 1. Repo state (checked against `main`)

**Exists now, reuse it:**

| Item | Where | Note |
| --- | --- | --- |
| Camera, begin and JPEG capture | `juno-api`: `hardware/device/camera/Camera.java`, `hardware/internal/NativeCamera.java`, `hardware/MCU.java` (`getCamera()`) | The `Esp32Cam` class is gone; `MCU` is the only facade. |
| Camera runtime | `juno-compiler`: `resources/runtime/esp32/camera.cpp`, `ShimFeature.CAMERA` | Hard-codes JPEG at QQVGA. |
| Text blocks emitted only when used | `ShimFeatureBlock`, `RuntimeShim`, `SketchFiles` | The mechanism `native-library-injection` needs already exists. |
| Capability gate | `board/Capability.java` (`CAMERA`), `linker/CapabilityRequirements.java`, `linker/Linker.java` | |
| Intrinsic wiring | `intrinsic/IntrinsicRegistry.java`, `backend/IntrinsicLowering.java`, `backend/runtime/core/Esp32CoreRuntime.java` | |
| Board | `board/Board.java` (`AI_THINKER_ESP32_CAM`), `juno-api` `annotations/AiThinkerEsp32CamBoard.java` | |
| Library installs | `juno-maven-plugin`: `InstallDepsMojo` (`OPTIONAL_LIBRARIES` takes `name@version`), `ArduinoCli.compile` (no `--library`) | |
| Example style reference | `juno-examples`: `AiThinkerEsp32CamCapture.java` | |
| Third-party precedent | `juno-third-parties/juno-pico-secure`, `juno-third-parties/pom.xml` | |
| Guards and tests | `RemovedFacadeReferencesTest`, `ApiArchitectureTest`, `ExamplesArchitectureTest`, `BackendArchitectureTest`, `RcxFirmwareArchitectureTest`, `JunoCompilerTest`, `GeneratedAsmToolchainTest` | |
| Test image and docs | `juno-compiler/src/test/docker/arduino-cli/Dockerfile`, `juno-site/.../third-parties.md`, `juno-site/.../ai-thinker-esp32-cam.md` | |

**Does not exist yet, to create** (confirmed by search on 2026-11-18):

- `juno-third-parties/juno-cat-detection` and its tracked `cat_detector_int8.tflite` with NOTICE.
- `hardware/device/vision/CatDetector`, `hardware/internal/NativeCatDetector` and `MCU.getCatDetector()`.
- `Camera.captureGrayscale` and its `NativeCamera` intrinsic.
- A cat-detection `ShimFeature` constant and its `ShimFeatureBlock` for the `cat_inference` source.
- The pinned TensorFlow Lite Micro entry in `InstallDepsMojo`. The package and version are not chosen yet (G2).
- The `AiThinkerEsp32CamCatDetector` example.
- The training dependency. It is not added yet (G1).

**Removed. Do not reference them in new work** (they remain in git history only):

- The `Esp32Cam` facade, removed in #344.
- `juno-bearssl`, the BearSSL `--library` branch and the UNO Q, removed in #388.

## 2. Corrections made to the changes

| Change | Was | Now | Why |
| --- | --- | --- | --- |
| `native-library-injection` | Generic `--library` for the UNO Q BearSSL branch | Bundled source through the existing `ShimFeatureBlock`, plus one pinned Library Manager entry | No `--library` argument exists today, and the text-block mechanism already exists. |
| `esp32-cam-cat-detector` | `Esp32Cam.getCatDetector()` | `MCU.getCatDetector()` | `Esp32Cam` was removed in #344. |
| `cat-detection-model-pipeline` | `juno-bearssl` as the precedent | `juno-pico-secure` | `juno-bearssl` was removed with the UNO Q. |
| `esp32-cam-grayscale-capture` | Mode strategy left open | One camera mode per program, rejected at compile time when mixed | Re-initialising the camera at run time was the costly option. |
| `esp32-cam-cat-detector` | 96×96 listed as an alternative | 64×64 only | 96×96 needs 9,216 bytes, more than the 8 KiB Juno arena. |

## 3. Gates (no code)

- **G1. Training dependency.** Approve TensorFlow Java or DJL, or confirm a hand-written exporter. AGENTS.md requires approval for new Maven dependencies. Nothing in `juno-cat-detection` starts before this.
- **G2. Pins.** Choose the TensorFlow Lite Micro Library Manager package and version, and the `esp32:esp32` core version CI uses (U3).
- **G3. Negatives.** Confirm the source and licence of the empty-room and furniture negatives (U5).

## 4. Spikes (run first, in parallel)

- **S-A. Java int8 export** (desk, no board). Kill switch for `cat-detection-model-pipeline`. Needs G1. If it fails, the hand-written exporter becomes the largest task in the change.
- **S-B. Grayscale frame** (hardware, about 1 h). Initialise the OV2640 in `PIXFORMAT_GRAYSCALE` at QQVGA and print `len` and a checksum. Decides the pixel source for `esp32-cam-grayscale-capture`.
- **S-C. TensorFlow Lite Micro link and size** (`arduino-cli`, no Juno). Build a dummy model on `esp32:esp32:esp32cam` with the G2 pins. Measure compile time, sketch size, and that the array stays in flash. Hardware run prints arena bytes and ms per inference. Decides the arena budget and the 64×64 versus 48×48 question.

## 5. Build order

| Order | Work | Needs | Parallel with |
| --- | --- | --- | --- |
| 1 | Spikes S-A, S-B, S-C | G1 (S-A), G2 (S-C) | each other |
| 2 | `esp32-cam-grayscale-capture`: extend `ShimFeature.CAMERA`'s block and add `captureGrayscale` | S-B | `cat-detection-model-pipeline`, `native-library-injection` |
| 2 | `cat-detection-model-pipeline`: create the module, train, export, commit `.tflite` | S-A, G1, G3 | grayscale, native |
| 2 | `native-library-injection`: pinned TensorFlow Lite Micro entry and tests (the bundled source uses `ShimFeatureBlock`, which exists) | S-C | grayscale, model pipeline |
| 3 | `esp32-cam-cat-detector` on a dummy model | grayscale API, native entry, S-C | model pipeline (still training) |
| 4 | `esp32-cam-cat-detector` with the real model | model pipeline | none |
| 5 | Example, `juno:verify`, hardware check, domain-shift check, docs | 4 | none |

**Freeze points.** The `CatDetector` and `Camera.captureGrayscale` signatures freeze before step 2 ends. Step 3 must not start on an unfrozen API.

**Critical path.** S-A → `cat-detection-model-pipeline` → real-model swap → hardware check. The model pipeline is the long pole: training takes 10 to 65 minutes, and a hand-written exporter would add to it.

## 6. Dissent (recorded, not resolved)

- **Plugin owner:** prefers a generic `--library` path now. The consensus defers it until a library cannot be shipped as sketch source.
- **Hardware:** the 64×64 buffer takes half of the 8 KiB arena. The consensus keeps 64×64 and measures it in S-C, with 48×48 as the fallback. This is not settled until S-C reports.
- **ML:** the 100 KB model may not generalise to ESP32-CAM frames. The domain-shift check stays as the last step, and it can send board frames back to training.

## 7. Open decisions for the maintainer

1. Approve G1 (the training dependency).
2. Choose the G2 pins.
3. Confirm the G3 negatives.
4. Confirm the capability name (`CAT_DETECTION` or a broader `TINYML`), which is still open in `esp32-cam-cat-detector/design.md`.
