## 1. Grayscale capture

- [ ] 1.1 Run the hardware spike: initialise the OV2640 in `PIXFORMAT_GRAYSCALE` at QQVGA and print the frame length and a checksum. If it fails, record the result and select YUV422 (Y channel) or JPEG decode.
- [ ] 1.2 Decide the mode strategy (one mode per program, or re-initialise to switch) and record it in `design.md`.
- [ ] 1.3 Add `Camera.captureGrayscale` and the `NativeCamera` intrinsic in `juno-api`, with Javadoc for the return contract.
- [ ] 1.4 Add `IntrinsicRegistry` and `IntrinsicLowering` entries gated on `Capability.CAMERA`, plus the use-gated ESP32 shim implementation (convert, downscale, bounds check).
- [ ] 1.5 Add `JunoCompilerTest` and `GeneratedAsmToolchainTest` cases, including rejection on a board without a camera.
- [ ] 1.6 Add a grayscale capture example (allocation at startup only, `Serial.begin` then a short delay), and pass `juno:verify` for `ai-thinker-esp32-cam`.
- [ ] 1.7 Confirm every existing example's `.S`, `Shim.cpp` and report are byte-identical. Run `./mvnw test`.
- [ ] 1.8 Hardware check: `juno:upload` followed by `juno:monitor`. Record frame size, checksum stability and ms per frame.
- [ ] 1.9 Update `juno-site/src/main/resources/content/ai-thinker-esp32-cam.md`.
