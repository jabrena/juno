## 1. Native library injection

- [ ] 1.1 Model a library declaration (bundled installer, or managed name plus pinned version) and attach declarations to shim features in `juno-compiler`.
- [ ] 1.2 Expose a compiled program's required libraries to the Mojos, keeping existing reports byte-identical for programs with no library.
- [ ] 1.3 Replace the hard-coded BearSSL `--library` branch in `ArduinoCli.compile` with the feature-driven list, preserving the UNO Q and UNO R3 WiFi Rev2 build properties and the UNO Q BearSSL linkage.
- [ ] 1.4 Install managed libraries at their pinned version through `InstallDepsMojo`, and bundled libraries once per version into `target/juno/libraries`, with deterministic, de-duplicated ordering.
- [ ] 1.5 Add fake-executor Mojo tests for bundled, managed, and no-library programs, plus the UNO Q regression.
- [ ] 1.6 Regenerate every example and confirm `.S`, `Shim.cpp` and reports are byte-identical. Run `./mvnw test` and the full `verify`.
- [ ] 1.7 Document the mechanism in `juno-site/src/main/resources/content/third-parties.md`.
