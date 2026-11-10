## Why

Juno cannot produce an image model that runs on a board. The Functional Specification for issue #312 names
"producing a fully int8-quantized `.tflite` strictly in Java" as the primary binding constraint: nothing downstream
can be built until a model artifact exists. The maintainer decided that no Python is allowed anywhere in the
pipeline, export included. If no Java library can quantize and export, a Java-native int8 exporter is in scope.

## What Changes

- New module `juno-third-parties/juno-cat-detection` with host-side tooling that runs on the build machine:
  - **dataset downloader:** Oxford-IIIT Pet; cats are the images whose file name starts with an uppercase letter.
  - **preprocessor:** 64×64 grayscale.
  - **trainer:** a tiny CNN with these layers:
    - Conv2D(8) + ReLU + MaxPool
    - Conv2D(16) + ReLU + MaxPool
    - Conv2D(24) + ReLU
    - GlobalAveragePooling
    - Dense(1) + Sigmoid
  - **evaluator.**
  - **int8 `.tflite` exporter.**
- A deterministic cat / no_cat split (fixed seed, tracked file lists) with separate validation and test sets.
- A tracked model artifact `cat_detector_int8.tflite` of at most 100 KB that uses TFLite Micro operators only. It is
  shipped with a NOTICE that credits Oxford-IIIT Pet and marks the model CC BY-SA 4.0, separately from the repository
  license.
- An evaluation report: held-out accuracy and the false-positive rate on the non-cat negatives. v1 has no numeric gate.
- Training runs on demand and never inside `./mvnw verify`, like the RCX firmware regeneration.

## Capabilities

### New Capabilities

- `cat-detection-model-pipeline`: Java-only, reproducible dataset → training → int8 TFLite model pipeline with a
  tracked, attributed artifact.

### Modified Capabilities

None.

## Impact

- New `juno-third-parties/juno-cat-detection` module, listed in `juno-third-parties/pom.xml`.
- A new Maven dependency for training (TensorFlow Java or DJL). This is an AGENTS.md "Ask first" item and needs
  explicit approval before it is added.
- A new ArchUnit rule that keeps training dependencies out of `juno-api`, `juno-compiler` and `juno-examples`.
- Dependencies: none on the other #312 changes. `esp32-cam-cat-detector` depends on this change.

## Source and derivation

- Source: issue https://github.com/jabrena/juno/issues/312. The snapshot was retrieved 2026-11-10T20:20:15Z and holds
  the body plus 2 accessible comments, matching the provider's count of 2. Inputs:
  - The issue body: Model, Dataset, Module layout, Plan steps 1–2.
  - The Functional Specification https://github.com/jabrena/juno/issues/312#issuecomment-6778278178 (§2 binding
    constraint 1, §3 A1/A3/A7–A10/A12, §5 rows 3, 7, 8, 10, 11).
- Derivation direction: issue → OpenSpec.
