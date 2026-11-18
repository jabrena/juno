## 1. Bundled sources and pinned libraries

- [ ] 1.1 Capture a baseline: the `arduino-cli` arguments, `.S`, `Shim.cpp` and report of every existing example, before any edit.
- [ ] 1.2 Register a `ShimFeatureBlock` for the cat detection feature that carries the bundled `cat_inference` source, appended only when the feature is used. This reuses the existing mechanism (`ShimFeatureBlock`, `RuntimeShim`); no new declaration type is needed.
- [ ] 1.3 Add a pinned Library Manager entry to `InstallDepsMojo` once gate 0 picks the TensorFlow Lite Micro package and version. Record the `esp32:esp32` core version CI uses.
- [ ] 1.4 Add fake-executor Mojo tests: the pinned library is installed at its exact version, and programs without a new feature produce identical `arduino-cli` arguments.
- [ ] 1.5 Add a compiler test: the bundled source appears in the sketch only when the feature is used.
- [ ] 1.6 Regenerate every example and confirm `.S`, `Shim.cpp` and reports are byte-identical to the baseline. Run `./mvnw test`.
- [ ] 1.7 Document the mechanism in `juno-site/src/main/resources/content/third-parties.md`.
