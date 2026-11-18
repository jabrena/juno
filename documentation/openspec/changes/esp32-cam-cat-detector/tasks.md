## 1. On-board cat detector

- [ ] 1.1 Pick a TensorFlow Lite Micro package from the Arduino Library Manager and pin it, together with the `esp32:esp32` core version CI uses. Record both versions in `design.md`.
- [ ] 1.2 Spike: build a dummy-model `cat_inference` with the pinned library through `native-library-injection`, pass `juno:verify` for `ai-thinker-esp32-cam`, then flash it and print the arena bytes used and the ms per inference.
- [ ] 1.3 Add `CatDetector`, `NativeCatDetector` and `Esp32Cam.getCatDetector()` to `juno-api`, with `@NullMarked` packages and Javadoc.
- [ ] 1.4 Name and add the cat-detection capability to `AI_THINKER_ESP32_CAM`. Add the `IntrinsicRegistry`, `IntrinsicLowering` and `Linker` gate, plus a use-gated `ShimFeature` that declares the TensorFlow Lite Micro and `cat_inference` libraries.
- [ ] 1.5 Implement the bundled `cat_inference` library: the tracked model as a C array, a tensor arena in shim memory (PSRAM if needed), and the sigmoid output mapped to 0..1000.
- [ ] 1.6 Add `JunoCompilerTest` and `GeneratedAsmToolchainTest` cases, including rejection on a board without the capability. Add a mock-based unit test for the example's transition logic.
- [ ] 1.7 Add `AiThinkerEsp32CamCatDetector` to `juno-examples` and pass `juno:verify`. Check the runtime risk report before flashing.
- [ ] 1.8 Confirm every existing example's `.S`, `Shim.cpp` and report are byte-identical. Run `./mvnw test`.
- [ ] 1.9 Hardware check: `juno:upload` with `-Djuno.port`, then `juno:monitor -Djuno.baudRate=115200`. Confirm `CAT` and `NO_CAT`, and record ms, arena and flash.
- [ ] 1.10 Domain-shift check: score about 100 real frames offline. Hand board frames back to `cat-detection-model-pipeline` only if accuracy drops noticeably.
- [ ] 1.11 Update `juno-site/src/main/resources/content/ai-thinker-esp32-cam.md`.
