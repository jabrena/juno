## ADDED Requirements

### Requirement: CatDetector scores a grayscale image without allocation

`CatDetector.score(byte[] grayscale)` SHALL run the linked model on an `inputSize() × inputSize()` grayscale image
and return an integer from 0 to `CatDetector.MAX_SCORE` (1000). `begin()` SHALL load the model and prepare the
runtime, returning whether it succeeded. Neither call SHALL allocate in the Juno arena.

#### Scenario: The first model expects 64×64

- **WHEN** a program calls `inputSize()` on the ESP32-CAM detector
- **THEN** it returns 64

#### Scenario: A score is returned for a captured frame

- **WHEN** `begin()` has returned true and `score` is called with a 4096-byte grayscale buffer
- **THEN** it returns a value in 0..1000

#### Scenario: Scoring in the main loop does not allocate

- **WHEN** the example scores frames in its main loop
- **THEN** the runtime risk report shows no allocation inside the loop, and the Juno arena use includes only the
  4 KiB image buffer for detection

### Requirement: The tensor arena lives outside the Juno arena

The TensorFlow Lite Micro tensor arena SHALL be allocated by the shim, in PSRAM if needed. It SHALL NOT be counted
in the Juno arena (`-Djuno.Xmx`).

#### Scenario: Juno arena use is unaffected by the model

- **WHEN** the example is compiled with the default Juno arena
- **THEN** the reported Juno arena use does not include the tensor arena

### Requirement: Cat detection is capability-gated and use-gated

`Esp32Cam.getCatDetector()` and its intrinsics SHALL compile only for boards that declare the cat-detection
capability, which in v1 is only `ai-thinker-esp32-cam`. Every other board SHALL fail at compile time with a clear
`CompileException`. The shim includes, the TensorFlow Lite Micro library and the `cat_inference` library SHALL be
emitted and linked only when a program uses the intrinsic. Every existing example's generated `.S`, `Shim.cpp` and
report SHALL stay byte-identical.

#### Scenario: Another board rejects the detector

- **WHEN** a program that uses `CatDetector` is compiled for `arduino-uno-r4-wifi`
- **THEN** compilation fails with a `CompileException` that names the missing capability

#### Scenario: Existing examples are unchanged

- **WHEN** every existing example is regenerated after this change
- **THEN** its `.S`, `Shim.cpp` and report are byte-identical to the files from before the change

### Requirement: The example reports cat transitions over serial

`AiThinkerEsp32CamCatDetector` SHALL pass `juno:verify` for `ai-thinker-esp32-cam`. It SHALL:
- allocate its buffers once at startup;
- print `score=<n> ms=<n>` for every frame at 115200 baud;
- print `CAT` once 3 frames in a row score at least 850;
- print `NO_CAT` when the score of a frame falls below the threshold while a cat is in view.

#### Scenario: A cat walks into view

- **WHEN** three frames in a row score at least 850 while no cat is in view
- **THEN** the example prints `CAT` once

#### Scenario: The cat leaves

- **WHEN** a frame scores below 850 while a cat is in view
- **THEN** the example prints `NO_CAT` once

### Requirement: Detection is verified on real hardware with measurements

Detection SHALL be verified on a real AI Thinker ESP32-CAM, after `juno:upload` followed by
`juno:monitor -Djuno.baudRate=115200`:
- the serial output SHALL show `CAT` with a cat in view and `NO_CAT` for an empty scene, with the inference time
  printed;
- the tensor arena bytes used, the flash footprint and the ms per inference SHALL be recorded.

v1 has no latency budget.

#### Scenario: Hardware run

- **WHEN** the example runs on the board, first pointed at an empty scene and then at a cat
- **THEN** the monitor shows `score=… ms=…` lines, then `CAT`, then `NO_CAT` after the cat leaves

#### Scenario: Domain shift is checked

- **WHEN** about 100 real ESP32-CAM grayscale frames, with and without a cat, are scored offline by the tracked model
- **THEN** their accuracy is recorded next to the held-out accuracy, and board frames are added to training only if
  accuracy drops noticeably
