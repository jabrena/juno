## Why

`ArduinoCli.compile` passes `--library` only for `arduino:zephyr:unoq`, and only for the bundled BearSSL
(`juno-maven-plugin/src/main/java/io/github/jabrena/juno/maven/ArduinoCli.java:55-67`). No other board or native
library can be linked into a generated sketch. The Functional Specification for issue #312 names this as the second
binding constraint for offline cat detection on the AI Thinker ESP32-CAM: TensorFlow Lite Micro and a repo-owned
`cat_inference` library cannot reach `esp32:esp32:esp32cam` today. The maintainer decided the fix belongs in scope and
must be generic, not a second FQBN special case.

## What Changes

- The libraries a sketch needs are derived from the shim features (or capabilities) the program actually uses, and
  the plugin passes them to `arduino-cli compile` as `--library <path>`.
- Repo-bundled libraries (the `juno-bearssl` pattern) and Arduino Library Manager libraries with a pinned version
  are both supported. Library Manager libraries are installed through `InstallDepsMojo`
  and the arduino-cli test Docker image at the pinned version.
- Existing behaviour is preserved:
  - BearSSL on the UNO Q.
  - The `--build-property` settings for the UNO Q and the UNO R3 WiFi Rev2.
  - The `arduino-cli` command line for every program that uses no library feature.
- The existing examples' generated `.S`, `Shim.cpp` and reports stay byte-identical.

## Capabilities

### New Capabilities

- `native-library-injection`: Feature-driven selection, installation and linking of native Arduino libraries for a
  generated sketch.

### Modified Capabilities

None (no existing OpenSpec capability covers the plugin's arduino-cli invocation).

## Impact

- `juno-maven-plugin`: `ArduinoCli`, `InstallDepsMojo`, and the verify/upload Mojos that call `compile`.
- `juno-compiler`: a way to expose the required libraries of a compiled program, for example in the report or the
  compile result, derived from `ShimFeature` usage.
- `juno-compiler/src/test/docker/arduino-cli/Dockerfile` when a pinned Library Manager library is first needed.
- No dependency on the other #312 changes. `esp32-cam-cat-detector` depends on this change.

## Source and derivation

- Source: issue https://github.com/jabrena/juno/issues/312. The snapshot was retrieved 2026-11-10T20:20:15Z and holds
  the body plus 2 accessible comments, matching the provider's count of 2. The authoritative input is the Functional
  Specification comment https://github.com/jabrena/juno/issues/312#issuecomment-6778278178 (§2 binding constraint 2,
  §4 Context Mapping).
- Derivation direction: issue → OpenSpec. The issue is not updated from this change.
- Change map, all approved 2026-11-10:
  - `native-library-injection`
  - `esp32-cam-grayscale-capture`
  - `cat-detection-model-pipeline`
  - `esp32-cam-cat-detector`, which depends on the first three.
