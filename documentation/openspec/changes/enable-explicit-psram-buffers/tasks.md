## 1. Public API and board model

- [x] 1.1 Add the final static `Psram` class with the agreed primitive query/allocation/access/release methods, 16-handle limit, negative result constants, JSpecify contract, alignment/lifetime/concurrency/DMA Javadoc, and API unit tests.
- [x] 1.2 Keep the implementation hidden behind static native intrinsics; do not expose `ArduinoNano.getPsram()`, `Esp32Cam.getPsram()`, or a `NativePsram` service object.
- [x] 1.3 Add `Capability.PSRAM` to exactly `NANO_ESP32` and `AI_THINKER_ESP32_CAM`, record nominal capacities of 8 MiB and 4 MiB, and update board-profile/report tests for the intentional ESP32-CAM report delta.

## 2. Compiler and generated-runtime integration

- [x] 2.1 Add every PSRAM operation to `Intrinsic`, `IntrinsicRegistry`, and `IntrinsicLowering`, preserving primitive signatures and complete byte-array range operands.
- [x] 2.2 Require `Capability.PSRAM` for the accessors/intrinsics in the linker and add acceptance/rejection tests for both supported boards, every unsupported board, and mixed multi-board declarations.
- [x] 2.3 Add a reachability-gated `ShimFeature.PSRAM` and ESP32 runtime source integration without branching on a concrete board in an assembly backend.
- [x] 2.4 Extend compiler/golden tests to prove non-opt-in `.S` and `Shim.cpp` remain byte-identical, Cardputer emits no PSRAM helpers, and opt-in programs emit the helpers exactly once.
- [x] 2.5 Extend `ArenaPlacementTest` and focused non-opt-in runtime-string/configuration tests to prove the arena, GC/runtime state, stacks, ordinary strings/arrays, current runtime-string slot/size limits, and `juno.Xmx` remain internal and unchanged.

## 3. PSRAM native implementation

- [x] 3.1 Verify the exact capability-aware `calloc`, release, and query declarations available in both pinned Arduino ESP32 core lines and implement detected-total/free/largest queries with the same `MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT` mask used for allocation.
- [x] 3.2 Implement zero-initialized external-only allocation and release with no generic/internal fallback, distinguishing invalid size, unavailable PSRAM, heap exhaustion, and live-handle exhaustion.
- [x] 3.3 Implement the 16-entry internal-SRAM registry with positive non-address tokens, non-reuse during one run, invalidation-before-free, stale/forged/double-release rejection, and serialized metadata mutation.
- [x] 3.4 Implement capacity, byte read/write, and all-or-nothing bulk copies with complete Java/PSRAM range validation, overflow-safe bounds checks, arbitrary byte offsets, and zero mutation on rejection.
- [ ] 3.5 Add a native host harness with a fake capability allocator covering exact flags, zero initialization after fresh/reused storage, unavailable/fragmented/exhausted memory, no fallback, token lifetime, slot/token exhaustion, range overflow, isolation, and repeated allocate/read/write/release.

## 4. ESP32-CAM lifecycle hardening

- [x] 4.1 Make `juno_camera_begin` verify usable PSRAM, remain a no-op after success, clean partial driver/sensor state after failure, and permit a later clean retry without internal-frame fallback.
- [x] 4.2 Refactor capture to one exactly-once frame-return cleanup path while preserving complete-copy-or-`-1` behavior and leaving undersized destinations unchanged.
- [x] 4.3 Update both `AiThinkerEsp32CamCapture` and `AiThinkerEsp32CamStream` to call and check `Camera.begin()` before any capture loop.
- [ ] 4.4 Add a fake-camera host harness for missing PSRAM, init/sensor failure cleanup, retry, repeated successful begin, capture-before-begin, no frame, oversized frame, unchanged destination, success, and exactly-once return.

## 5. Examples and real toolchains

- [x] 5.1 Add a zero-allocation Nano ESP32 example that stages and consumes a large UTF-8 payload through reusable bounded chunks, handles every negative result, and releases its handle exactly once.
- [x] 5.2 Add focused `PsramApi.java` fixtures for Nano ESP32 and ESP32-CAM plus a `CameraApi.java` fixture under `juno-compiler/src/test/arduino/programs`.
- [ ] 5.3 Compile the focused fixtures with `arduino:esp32:nano_nora` and `esp32:esp32:esp32cam` through the `arduino-cli` profile, reporting unavailable Docker/cores as skipped rather than passed.
- [ ] 5.4 Run `juno:verify` end to end for the representative Nano PSRAM example and ESP32-CAM camera example without uploading hardware.

## 6. Documentation

- [x] 6.1 Update `juno-site/src/main/resources/content/memory-model.md` with nominal/detected/free/largest meanings, explicit ownership/release, internal-arena invariants, fragmentation, performance/cache, concurrency, ISR, and DMA rules.
- [x] 6.2 Update the Nano ESP32, AI Thinker ESP32-CAM, boards, and Maven-plugin source pages plus API Javadoc to document supported targets, camera-first allocation order, failure results, and storage-selection guidance.
- [x] 6.3 Document and link the bounded large-text workflow, explicitly distinguishing normal strings, internal caller-owned arrays, generic PSRAM handles, and streaming without promising direct subsystem integration.
- [x] 6.4 Regenerate the site with `./mvnw clean verify -Psite -DskipTests` and run `./mvnw -pl juno-site test`; do not hand-edit `docs/`.

## 7. Project and specification validation

- [x] 7.1 Run `./mvnw test` after all Java and `pom.xml` changes and resolve unit, architecture, nullness, and deterministic-generation failures.
- [x] 7.2 Run `./mvnw --batch-mode --no-transfer-progress verify -b turbo -T1C` and resolve Spotless, Enforcer, dependency-analysis, compiler, and module failures without adding a dependency or plugin.
- [x] 7.3 Run `openspec validate --all` from `documentation/` and resolve every proposal/spec validation finding.
- [x] 7.4 No physical board upload was authorized or available in this run; hardware smoke tests are recorded as unverified.
