## Why

Issue #326. Juno's 8 KiB arena default was chosen for the UNO R4 WiFi (32 KiB SRAM) and is reused unchanged by every
non-AVR board, from the UNO Q to the ESP32 and RP2350 boards, whose memory layouts and runtime reserves differ widely.
Only the two AVR cores carry a default and a maximum, and they hang off `ArduinoCore` rather than the physical board.
A review panel (embedded memory, compiler architecture, test/verification, developer experience) confirmed the
following defects in the current code:

- An explicit `-Djuno.Xmx` equal to 8192 is indistinguishable from "unset" (`RuntimeConfig.java:110`), so
  `-Djuno.Xmx=8k` on the UNO WiFi Rev2 silently becomes 1024 instead of being rejected above its 3072 maximum.
- Non-AVR boards have no upper bound (`RuntimeConfig.forBoard` returns `this`): `-Djuno.Xmx=1g` passes the compiler
  and fails later at link time, or at run time on the UNO Q (LLEXT loader pool) and the dual-carrier ESP32/Pico ports
  (slot stacks, Wi-Fi and TLS come from the heap left after `.bss`).
- `RuntimeRiskAnalyzer` judges every board's thread bodies against the R4's 2048-byte stack and names the "UNO R4
  WiFi" in JUNO-RISK-011 (`RuntimeRiskAnalyzer.java:236-243`).
- `InspectCommand` and several backend paths use the unresolved `RuntimeConfig.DEFAULT`, bypassing `forBoard`, so
  `inspect` analyses an 8192-byte arena on AVR.
- JUNO-CONFIG-002 can suggest an arena above what the board accepts; its `board` parameter is unused.
- The risk report shows `Arena: N / capacity` but not the board, whether the size is a default or an override, the
  stack, or the carrier count.
- The documentation has drifted: `juno.maxThreads` is documented (site, plugin page, mailbox TLS skill) but does not
  exist, `juno.carriers` is undocumented, the memory-model tables omit the ESP32-CAM and Pico 2 W, the "4 slots" rule is
  AVR-only now, and the Cardputer is listed without structured concurrency although `Board` declares it.

## What Changes

- Introduce a `BoardMemoryProfile` owned by each `Board`: internal SRAM, default and maximum arena, default and maximum
  carrier stack, the memory authority that has the final say (linker, runtime heap, or UNO Q loader pool), and PSRAM
  size as report-only metadata. The AVR memory fields leave `ArduinoCore`; carrier count stays there.
- Split the requested configuration from the resolved one: `RuntimeConfig` holds `OptionalInt` arena/stack/carrier
  requests; `ResolvedRuntimeConfig` holds the effective values, their source (board default or `juno.*` override) and
  the board. Backends, core runtimes, shims, analyzers and reports accept only the resolved type.
- Validate `juno.Xmx` and `juno.Xss` against the selected board's profile on every architecture, with diagnostics that
  name the board, the requested size, the maximum, the internal SRAM and the default. **BREAKING (AVR only):** an
  explicit `-Djuno.Xmx=8k` on the UNO R3 or UNO WiFi Rev2 is now rejected instead of silently replaced; no example
  in the repository uses it.
- Print a "Runtime configuration" block on every build (board, effective arena and its source, maximum, internal
  SRAM, PSRAM not used, stack × carriers), make JUNO-RISK-011 use the board's stack, and cap JUNO-CONFIG-002 at the
  board maximum.
- Measure every board (link and boot ceiling, free internal heap after Wi-Fi and threads start) through a shim-only
  diagnostic gated by `juno.gcLog`, record the inventory, then change per-board defaults only where the measurements
  justify it, each in its own commit with a hardware check. The UNO R4 WiFi default stays 8192 bytes.
- Guarantee that the arena stays in internal SRAM: generated sources carry no PSRAM placement, and the Xtensa
  arduino-cli tests assert the `juno_arena` symbol address lies in internal DRAM.
- Rewrite the memory-model documentation around internal SRAM, arena, stacks, runtime reserve and PSRAM, with a board
  profile table guarded against drift by a test, and remove `juno.maxThreads` from docs and skills.

## Capabilities

### New Capabilities

- `board-memory-profiles`: per-board arena and stack defaults and limits, override validation and diagnostics, the
  resolved runtime configuration and its report, the internal-SRAM guarantee for the arena, and the measured memory
  inventory.

### Modified Capabilities

- None recorded: `documentation/openspec/specs/` holds no baseline specs yet.

## Impact

- `juno-compiler`: `RuntimeConfig`, new `ResolvedRuntimeConfig`, `board.Board`, `board.ArduinoCore`, `RuntimeLimits`,
  `JunoCompiler`, `CompilationReport`, `InspectCommand`, `analysis.*` (risk analyzer, config analyzer, formatter), the
  three `*AsmBackend`s, `RuntimeShim`/`AvrRuntimeShim`, every `*CoreRuntime.threadPort`, the `CoreRuntime` SPI, and the
  allocator shim (diagnostic probe only).
- `juno-maven-plugin`: `AbstractJunoMojo` Javadoc and the report it prints; no new parameter.
- Tests: `RuntimeConfigTest`, `AvrAsmBackendTest`, `XtensaAsmBackendTest`, `Thumb2AsmBackendTest`, `CoreRuntimeTest`,
  `ConfigAnalyzerTest`, `RuntimeRiskAnalyzerTest`, `MainTest`, `ConcurrencyPrimitivesTest`, the arduino-cli ITs, QEMU
  GC tests (pinned to 8 KiB) and `XtensaRunIT`/CI's `-Djuno.Xmx=24k`.
- Docs: `memory-model.md`, `juno-maven-plugin.md`, `create-your-first-project.md`, board pages,
  `skills/juno-zero-allocation`, `skills/juno-mailbox-tls-deploy`, AGENTS.md's arena sentence.
- No new Maven dependency, plugin, `juno-api` method or intrinsic. Generated `.S`, `Shim.cpp` and reports stay
  byte-identical until the reporting and default-change tasks, which update the affected golden outputs deliberately.
