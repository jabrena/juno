## 1. Characterization and type split (byte-identical output)

- [ ] 1.1 Add `DefaultRuntimeCharacterizationTest`: for every `Board`, compile one fixed threaded program at the default and pin `juno_arena[N]`, `JUNO_THREAD_STACK_BYTES`, the carrier count and the report's `Arena:` line in a table.
- [ ] 1.2 Capture a baseline of `.S`, `Shim.cpp` and report for every example × supported board at their documented flags, using the worktree method in `skills/juno-debugging`.
- [ ] 1.3 Add `BoardMemoryProfile` and `MemoryAuthority` in `board`, with compact-constructor invariants and `reservedBytes()`; add `BoardMemoryProfileTest` over `Board.values()`.
- [ ] 1.4 Give each `Board` constant its profile with today's values. AVR keeps its values (R3 stack 512/512, Rev2 512/1024); every other board uses default arena 8192 and its current core stack, with a provisional maximum stack of four times the default (design D8a); set each non-AVR maximum arena to internal SRAM minus the minimal-Blink static footprint taken from `arduino-cli`.
- [ ] 1.5 Remove the memory fields from `ArduinoCore` and the AVR-only constants that only fed them from `RuntimeLimits`.
- [ ] 1.6 Change `RuntimeConfig` to `OptionalInt` arena, keep `of(…)` and the `(int, OptionalInt)` convenience constructor, add `ResolvedRuntimeConfig` with `Source`, and rename `forBoard` to `resolveFor`.
- [ ] 1.7 Make backends, `RuntimeShim`/`AvrRuntimeShim`, the `CoreRuntime` SPI and the six `*CoreRuntime.threadPort` implementations accept `ResolvedRuntimeConfig`; drop their hard-coded `ArduinoCore.X.defaultThreadStackBytes()`.
- [ ] 1.8 Route `JunoCompiler`, `CompilationReport`, `InspectCommand`, `RuntimeRiskAnalyzer` and `ConfigAnalyzer` through `resolveFor`; update the tests that passed `RuntimeConfig.DEFAULT` straight to a backend (`AvrAsmBackendTest`, `XtensaAsmBackendTest`, `PoweredUpHubScanWhileBlinkingConfigTest`, `CoreRuntimeTest`, `ConcurrencyPrimitivesTest`).
- [ ] 1.9 Add `RuntimeConfigTest` cases: unset resolves to the default with source board default; an explicit value equal to the default resolves with source override; explicit 8k on the Rev2 is rejected; explicit 512 on the R3 is accepted; the known-good list from design D4 is accepted; `-Djuno.Xmx=1m` on the R4 is rejected.
- [ ] 1.10 Verify that output is byte-identical against 1.2 and that 1.1 is green; run `./mvnw test`; commit as `refactor(compiler): board memory profiles and resolved runtime config`.

## 2. Diagnostics and analyzers

- [ ] 2.1 Move validation and messages into `BoardMemoryProfile.resolve`, worded as in design D7, including the PSRAM sentence and the authority sentence; keep "juno.Xmx can be at most" and "juno.Xss can be at most".
- [ ] 2.1a Enforce the per-board `juno.Xss` maximum on every architecture (design D8a), adding the heap-slot sentence for dual-carrier ESP32/Pico boards; add parameterized tests over `Board.values()` for "at the maximum is accepted" and "maximum + 8 is rejected", plus R4 1536 and 2k accepted.
- [ ] 2.2 Make JUNO-RISK-011 use the resolved stack and the board's display name; test it with an AVR board, the R4, the Q and an ESP32 board.
- [ ] 2.3 Cap the JUNO-CONFIG-002 suggestion at the board maximum, use its `board` parameter, and add a test of the above-maximum message.
- [ ] 2.4 Add the `LOADER_POOL` warning when estimated static RAM approaches the UNO Q ceiling; add a test.
- [ ] 2.5 Run `./mvnw test`; commit as `fix(compiler): validate juno.Xmx and juno.Xss against the board profile`.

## 3. Report

- [ ] 3.1 Add a `RuntimeConfigurationFormatter`, shared by the CLI and the plugin, that prints the block from design D7 on every build.
- [ ] 3.2 Update `MainTest`, the formatter tests and the plugin tests that assert report text.
- [ ] 3.3 Update the `AbstractJunoMojo` Javadoc for `juno.Xmx`/`juno.Xss` (no "runtime's 8 KB", no R4/Q-only stack list) and document `juno.carriers`.
- [ ] 3.4 Run `./mvnw test`; commit as `feat(compiler): report the board memory profile used by a build`.

## 4. Internal-SRAM guard and measurement probe

- [ ] 4.1 Add a unit test that the generated shim and assembly for every board contain no PSRAM placement markers outside the camera frame-buffer helper.
- [ ] 4.2 In the arduino-cli integration test, link for the three Xtensa boards with `--build-path`, run `nm`, and assert `juno_arena` lies in internal DRAM; the memory integration tests iterate over `Board.values()` and ignore `juno.arduinoCliBoardScope` (design D8b).
- [ ] 4.2a Add `ai-thinker-esp32-cam` to the CI `arduino-java` matrix (`.github/workflows/maven.yaml`), and add a guard test that fails when the matrix board ids differ from `Board.values()`; commit as `ci: compile Java examples for every board`.
- [ ] 4.3 Emit the `juno.gcLog`-gated free-heap probe from design D5 for the ESP32, RP2350 and Zephyr (UNO Q loader pool: total, free, minimum free; first identify the exported heap-statistics symbol in the installed `arduino:zephyr` core) (lines under 31 characters, ESP32 `esp_ptr_external_ram(juno_arena)` panic); test that it is emitted under the flag and absent without it.
- [ ] 4.4 Run `./mvnw test` and `./mvnw -pl juno-compiler -am -Parduino-cli verify`; commit as `test(compiler): keep the arena in internal SRAM` and `feat(compiler): free-heap probe under juno.gcLog`.

## 5. Inventory and default changes (one PR per board)

- [ ] 5.1 Check that the reactor is installed (`./mvnw install`) before running any example on hardware.
- [ ] 5.2 For each board, record `arduino-cli` "Global variables use" for Blink and for a heavy example: Wi-Fi + threads, plus BLE or camera where supported; for the UNO Q, a Doom2-class program.
- [ ] 5.3 Binary-search the minimal-program link-and-boot ceiling of `juno.Xmx` with `juno:upload` + `juno:monitor` (ask before each upload). For `RUNTIME_HEAP` boards, record the probe's minimum free internal heap with Wi-Fi up and 2 carriers. Do the same for `juno.Xss`: on static-stack boards, with a minimal threaded program; on dual-carrier boards, with the board's maximum concurrent threads and Wi-Fi up. Cover all eight boards: UNO R3, UNO WiFi Rev2, UNO R4 WiFi, UNO Q, Nano ESP32, AI Thinker ESP32-CAM, M5Stack Cardputer, Pico 2 W.
- [ ] 5.4 Write the inventory (one row per board with core version, date and minimal-program static RAM baseline) into the change's `design.md` appendix and the docs source; keep the baselines in a test resource the drift check reads.
- [ ] 5.4a Add the core-drift check (design D8c) to the arduino-cli integration test, over all eight boards; test both the failure message and the within-margin pass; commit as `test(compiler): detect Arduino core memory drift`.
- [ ] 5.5 Replace the provisional arena and stack maxima with the measured ceilings minus the documented margin; check that the known-good arena and stack lists still pass.
- [ ] 5.6 For each board whose default should change (ESP32 candidates first), make one commit per board covering:
  - [ ] 5.6.1 Update the profile.
  - [ ] 5.6.2 Update the characterization table and golden outputs.
  - [ ] 5.6.3 Drop `-Djuno.Xmx=24k` from CI and `XtensaRunIT` when the default covers it.
  - [ ] 5.6.4 Pin the QEMU GC-pressure tests to `-Djuno.Xmx=8k`.
  - [ ] 5.6.5 Hardware-check the heavy example for at least 30 s of heartbeat output, no reboot banner, and a successful Wi-Fi (and camera) operation.
- [ ] 5.7 Leave the UNO R4 WiFi default at 8192 and re-confirm Mailbox at 11008 bytes.

## 6. Documentation

- [ ] 6.1 Rewrite `juno-site/src/main/resources/content/memory-model.md` with these sections:
  - [ ] 6.1.1 Where RAM goes.
  - [ ] 6.1.2 Internal SRAM vs PSRAM.
  - [ ] 6.1.3 Arena.
  - [ ] 6.1.4 Stacks and carriers.
  - [ ] 6.1.5 Board memory profiles table for all 8 boards.
  - [ ] 6.1.6 Flags (`juno.Xmx`, `juno.Xss`, `juno.carriers`, `juno.gcLog`).
  - [ ] 6.1.7 Sizing workflow.
  - [ ] 6.1.8 Existing arrays-that-fit and build-check sections.
- [ ] 6.2 Remove `juno.maxThreads` from `juno-maven-plugin.md`, `create-your-first-project.md`, `memory-model.md`, `arduino-nano-esp32.md` and `skills/juno-mailbox-tls-deploy/SKILL.md`.
- [ ] 6.3 Fix the remaining drift: the "4 slots" rule (AVR only), Cardputer structured concurrency, missing ESP32-CAM and Pico 2 W rows, `skills/juno-zero-allocation/SKILL.md:8`, the arena sentence in AGENTS.md.
- [ ] 6.4 Add a migration note for the AVR explicit-8k rejection, and for any changed defaults.
- [ ] 6.4a Document the core-upgrade checklist: rerun the drift check, then re-check the run-time heap with the `juno.gcLog` probe on the ESP32, Pico 2 W and UNO Q boards, then update the inventory rows.
- [ ] 6.5 Add a `juno-site` test that checks the board memory profile table against `Board.values()`.
- [ ] 6.6 Run `./mvnw clean verify -Psite -DskipTests` and `./mvnw -pl juno-site test`; commit as `docs(site): board memory profiles and the RAM budget`.

## 7. Close-out

- [ ] 7.1 Run the full CI-equivalent verify (`./mvnw --batch-mode --no-transfer-progress verify -b turbo -T1C`).
- [ ] 7.2 Tick issue #326's acceptance criteria against tasks 1–6 and archive this change.
