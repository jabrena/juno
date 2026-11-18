## Context

Today `ArduinoCli.compile` builds its arguments from the FQBN and the sketch path, with two board-specific extras:
- the Portenta H7 gets the SDFAT block-device `--build-property`;
- the Pico 2 W secure firmware goes through `compileForPicoSecureFirmware`, with its own build properties.

No `--library` argument is passed for any board.

`InstallDepsMojo` already installs pinned managed libraries with `arduino-cli lib install name@version`. The compiler
already knows which `ShimFeature`s a program uses, because `RuntimeShim` gates includes and helpers on
`uses(ShimFeature.X)`. Shim sources reach the generated sketch as text in `Shim.cpp`. The repo norm is that the backend
never branches on a specific board.

## Goals / Non-Goals

**Goals:**
- A use-gated shim feature can carry bundled C++ source (the `cat_inference` wrapper and its model array).
- A pinned Library Manager library (TensorFlow Lite Micro) is installed at its exact version.
- Programs that use neither are unchanged, byte for byte.
- Offline tests use the fake process executor.

**Non-Goals:**
- A generic `--library` argument in `ArduinoCli.compile`. It is deferred until a bundled library cannot be emitted as
  sketch source (for example a large prebuilt archive).
- Restoring the removed UNO Q BearSSL path.
- The `.env` key allowlist idea.
- Adding TensorFlow Lite Micro's code to the repo. That belongs to `esp32-cam-cat-detector`, through the pinned
  library.

## Decisions

- **Feature-driven, not FQBN-driven.** Bundled source is attached to a shim feature. The board only decides whether
  the feature is allowed, through the existing capability gate in the `Linker`.
- **Bundled source as sketch text (consensus, 4 of 5 panel members).** The feature's source is a `ShimFeatureBlock`
  that `RuntimeShim` appends to `Shim.cpp` only when the feature is used. The mechanism exists today. This needs no `ArduinoCli` change and no new artifacts
  under `target/`. Dissent (recorded): the plugin owner preferred a generic `--library` mechanism now. Deferred until a
  library cannot be shipped as source.
- **Managed libraries are pinned in `InstallDepsMojo`.** Its existing `OPTIONAL_LIBRARIES` list takes `name@version`
  entries. TensorFlow Lite Micro is added there once gate 0 picks the package and version.

## Risks / Trade-offs

- The model is about 100 KB as a C array in sketch text. Compile time and sketch size must be measured in the
  TensorFlow Lite Micro spike (S-C) before the real model is wired.
- Library Manager versions can drift against the `esp32:esp32` core, which the repo does not pin → pin the library
  version, and record the core version used by CI.
- The C array must stay in flash, not RAM, on the ESP32. The spike checks the link map.

## Open Questions

- The Library Manager package name and version for TensorFlow Lite Micro (gate 0, U3).
