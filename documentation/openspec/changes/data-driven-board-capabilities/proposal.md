## Why

Issue [#337](https://github.com/jabrena/juno/issues/337) identifies two coupled problems: public programs name board-family facades even though `@Board` selects the target, and the linker recognizes some capability-dependent getters through a fragile map of concrete facade owners. One `MCU` facade plus data-driven capability validation makes source selection consistent with `@Board` and removes owner-specific linker maintenance.

## Source and Authority

- Requirements source: GitHub issue [#337](https://github.com/jabrena/juno/issues/337), retrieved through authenticated read-only tooling at `2026-11-13T09:43:02Z` with the complete accessible description and `1` of `1` accessible comments.
- Maintainer clarification in this conversation on `2026-11-13` approves the previously gated unified-facade phase and requires the board-specific facades to be removed in favor of one facade. `MCP` is interpreted as `MCU`, the name used by issue #337; a different literal class name would require a follow-up correction.
- A second maintainer clarification in this conversation requires constructor call sites to stop passing MCU-owned singleton services when the constructed object can resolve the same service directly. Injectable overloads remain a repository design constraint for mocks and non-default hardware topology.
- The maintainer clarification overrides the issue's conservative recommendation to defer Phase 2 and its suggestion to keep deprecated forwarders for one release. This change performs a direct, explicitly breaking migration.
- The issue supplies the capability-validation behavior and the requirement that `juno:compile` fail when neither `@Board` nor `-Djuno.board` supplies a target.
- The current `@Board` Javadoc remains authoritative for multi-board portability: every declared board must support every reachable capability even when one board is selected for code generation.
- Derivation is one way from the issue, maintainer clarification, and repository contracts into this OpenSpec change. Source artifacts are not automatically synchronized from these files.

## What Changes

- **BREAKING**: add `io.github.jabrena.juno.hardware.MCU` as the only public facade for onboard MCU/board services.
- **BREAKING**: remove `ArduinoUno`, `ArduinoNano`, `RaspberryPico2`, `Esp32Cam`, and `M5StackCardputer` rather than retaining deprecated forwarders.
- Migrate all API references, examples, tests, documentation, and architectural guidance to `MCU`.
- Expose the union of current onboard getters through `MCU`, with `getSdCard()` as the normalized name for the ESP32-CAM and Cardputer onboard sockets.
- Audit constructors currently called with facade getters and add simpler constructors or factories that obtain fixed onboard singleton collaborators from `MCU`, while retaining explicit injection forms for tests, alternate services, and device topology.
- Keep each compiler `Board`'s capability set as the source of truth and replace facade-owner capability detection with an owner-independent resolver.
- Reject every reachable `MCU` getter or intrinsic that any declared board cannot support, while keeping portable getters usable by multi-board programs.
- Preserve target resolution for the standalone compiler and Maven `juno:compile`: a single declared board is sufficient, while no declaration or an ambiguous multi-board declaration requires an explicit valid board.
- Preserve interface-based mocking, zero-allocation board code, backend independence, and deterministic generated output.

## Capabilities

### New Capabilities

- `unified-mcu-facade`: The single public `MCU` facade, normalized onboard service access, removal and migration of legacy facades, and source/runtime compatibility rules.
- `board-capability-validation`: Target resolution, per-board capability authority, owner-independent reachable-call validation, multi-board portability, and actionable diagnostics for the compiler and Maven plugin.

### Modified Capabilities

None. The OpenSpec workspace contains no established facade or board-validation capability specification.

## Impact

- `juno-api`: new `MCU` facade; removal of five public facade classes; simplified default construction for MCU-owned collaborators; retained injectable overloads; internal provider ownership for lazy IMU and SD-card state; updated Javadoc and architecture checks.
- `juno-compiler`: `Board`, `Capability`, and linker capability resolution; compiler/linker regression tests, diagnostic assertions, cache-key verification, and deterministic generated-output checks.
- `juno-maven-plugin`: direct `juno:compile` regression coverage for missing and explicit board selection; no new plugin parameter.
- `juno-examples`: every board-service call migrates to `MCU`, including multi-board programs that no longer carry a misleading facade name.
- `juno-site`, `README.md`, `AGENTS.md`, and source Javadoc: facade guidance and examples migrate to `MCU`; generated `docs/` is regenerated, never hand-edited.
- CI/toolchains: per-declared-board compilation coverage is added or extended for multi-board examples; representative targets are verified with `juno:verify`.
- Release compatibility: this requires a breaking release and migration notes from each removed facade to `MCU`.
- No Maven dependency, Maven plugin, backend board branch, or new hardware support is added.
