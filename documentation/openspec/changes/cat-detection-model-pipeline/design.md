## Context

Two precedents exist in the repo:
- `juno-third-parties/juno-rcx-firmware`: a host-side generator with a tracked artifact (the S-record), regenerated
  on demand.
- `juno-third-parties/juno-bearssl`: a vendored native library with its own `LICENSE.txt`.

Build-machine code is written object-oriented (AGENTS.md code style).

## Goals / Non-Goals

**Goals:**
- No Python at any step.
- Reproducible split and metrics.
- An int8 model of at most 100 KB that uses only TFLite Micro operators.
- An attributed, tracked artifact.

**Non-Goals:**
- The Open Images provider. A `DatasetProvider` abstraction comes later, and the Oxford provider is the only one in v1.
- On-board inference (`esp32-cam-cat-detector`).
- A generic `ImageClassifier`.
- A numeric accuracy gate.

## Decisions

- **Conversion spike first.** Train a throwaway CNN in Java, export it to int8 `.tflite`, and dump the operator list.
  - Pass: every op is int8, every op is in {Conv2D, ReLU, MaxPool, Mean/GlobalAveragePooling, FullyConnected,
    Logistic}, the file loads in a TFLite Micro interpreter, and no Python ran.
  - Fail: build a Java-native int8 FlatBuffer exporter (post-training quantization plus the TFLite schema writer).
- **Reproducibility means split plus metrics.** A fixed seed and tracked file lists make the split deterministic.
  Evaluation metrics repeat within a recorded tolerance. The `.tflite` does not have to be byte-identical across
  retraining.
- **Tracked artifact with NOTICE.** `cat_detector_int8.tflite` is committed beside a NOTICE (Oxford-IIIT Pet,
  CC BY-SA 4.0, model under CC BY-SA 4.0).
- **On demand.** Download, training and export are a separate entry point (an explicit Maven invocation or `main`),
  never part of `verify`. Training takes about 10–65 minutes.

## Risks / Trade-offs

- [A1, High/Low] There may be no Java library that does full int8 quantization and TFLite export → a custom
  exporter, which is the largest piece of work here.
- [A7, High/Low] Domain shift from pet photos to ESP32-CAM frames → checked in `esp32-cam-cat-detector` by scoring
  about 100 real frames offline, adding them to training only if accuracy drops noticeably.
- [A9] 64×64 grayscale may not separate cats from dogs → report the false-positive rate for each negative class.
- The dataset URL may be unstable, and the "uppercase = cat" rule may not hold for every file (U4) → verify during
  download.

## Open Questions

- Which Java training library (TensorFlow Java or DJL) to use. This needs "Ask first" approval.
- Where the empty-room and furniture negatives (500 each) come from, and under which licenses (U5). The source does
  not specify this, so it is unresolved.
