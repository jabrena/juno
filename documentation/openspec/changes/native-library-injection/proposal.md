## Why

The original premise of this change no longer holds. It described a hard-coded `--library` branch in
`ArduinoCli.compile` for `arduino:zephyr:unoq` and the bundled BearSSL library. The UNO Q was removed in #388, and that
branch went with it. `ArduinoCli.compile` now passes no `--library` at all.

The cat detector on the AI Thinker ESP32-CAM still needs two native pieces, and neither requires changing how
`arduino-cli` is invoked:

- TensorFlow Lite Micro, from the Arduino Library Manager at a pinned version. `InstallDepsMojo` already installs
  pinned managed libraries with `name@version` (for example `M5Cardputer@1.1.1`).
- The repo-owned `cat_inference` C++ source, which embeds the tracked model as a C array. The shim already writes its
  feature sources into the generated sketch (`Shim.cpp`), so a use-gated shim feature can emit this source the same way.

This change therefore shrinks to those two mechanisms. A generic `--library` path in the plugin is deferred until a
bundled library cannot be shipped as sketch source.

## What Changes

- A use-gated `ShimFeature` can carry bundled C++ source. The source is emitted into the generated sketch only when a
  program uses the feature, and it is not gated on any board FQBN.
- `InstallDepsMojo` gains one pinned Library Manager entry for TensorFlow Lite Micro, once the package and version are
  chosen (gate 0 in `cat-detection-execution-order.md`).
- Existing behaviour is preserved. No program that uses no new feature changes its `arduino-cli` arguments, its `.S`,
  its `Shim.cpp` or its report.

## Capabilities

### New Capabilities

- `native-library-injection`: Use-gated bundled sketch sources and pinned Library Manager libraries for a generated
  sketch. The name is kept from the original proposal; the scope is the narrower one above.

### Modified Capabilities

None.

## Impact

- `juno-compiler`: `ShimFeature` and the shim feature block that emits bundled sources.
- `juno-maven-plugin`: `InstallDepsMojo` (one pinned entry). `ArduinoCli.compile` is not changed.
- `juno-compiler/src/test/docker/arduino-cli/Dockerfile` when the pinned library is first needed by the test image.
- `esp32-cam-cat-detector` depends on this change.

## Source and derivation

- Source: issue https://github.com/jabrena/juno/issues/312, Functional Specification comment
  https://github.com/jabrena/juno/issues/312#issuecomment-6778278178 (§2 binding constraint 2, §4 Context Mapping).
- Derivation direction: issue → OpenSpec. The issue is not updated from this change.
- Revised 2026-11-18. The original premise (the BearSSL `--library` branch) was removed by #388. The panel's
  consensus on the reduced scope is recorded in `cat-detection-execution-order.md`.
