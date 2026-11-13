## 1. Capability Requirement Model

- [x] 1.1 Inventory every public getter on `ArduinoUno`, `ArduinoNano`, `RaspberryPico2`, `Esp32Cam`, and `M5StackCardputer`, classifying it as portable or capability-bearing and recording its target support.
- [x] 1.2 Introduce an object-oriented linker collaborator that resolves intrinsic and ordinary-method capability requirements from central metadata independent of concrete facade owner names.
- [x] 1.3 Keep each `Board` capability set as support authority, add an onboard-SD capability classification if required, make capability labels facade-neutral, and remove `REQUIRED_METHOD_CAPABILITIES`.
- [x] 1.4 Add exhaustive resolver tests covering every planned `MCU` getter, every current intrinsic requirement, portable methods, and unreachable capability-dependent code.

## 2. Unified MCU Facade

- [x] 2.1 Add final, non-instantiable `MCU` with the union of current portable and capability-gated onboard getters, zero per-call allocation, no mutable facade state, and no board-name switch.
- [x] 2.2 Move lazy IMU ownership from `ArduinoUno` into an internal provider that shares one `Lsm6ds3` between `MCU.getAccelerometer()` and `MCU.getGyroscope()` without changing initialization behavior.
- [x] 2.3 Normalize `M5StackCardputer.getSd()` and `Esp32Cam.getSdCard()` as `MCU.getSdCard()`, moving lazy instance and board wiring details into an internal onboard-SD provider while leaving shield-owned sockets unchanged.
- [x] 2.4 Add API tests for getter delegation, stable instance identity, host-side interface mocking boundaries, nullness, and zero-allocation constraints.
- [x] 2.5 Remove `ArduinoUno`, `ArduinoNano`, `RaspberryPico2`, `Esp32Cam`, and `M5StackCardputer` with no deprecated forwarders or aliases.
- [x] 2.6 Inventory every constructor call that directly passes one or more legacy facade getters, grouping MCU-owned singleton plumbing separately from pins, addresses, buffers, shield/carrier services, transports, and other real topology.
- [x] 2.7 Add unambiguous convenience constructors or named factories for confirmed MCU-singleton cases, including applicable shield/carrier, Powered Up, RCX, navigation, timer, GPIO, and I2C families.
- [x] 2.8 Retain explicit injectable constructors for mocks, emulators, alternate buses/clocks/radios, and non-default topology; have convenience paths delegate once and keep collaborators in final fields.
- [ ] 2.9 Add API unit tests pairing each new convenience path with its injection path and proving identical behavior, no repeated `MCU` lookup, no per-operation allocation, and preserved mockability.

## 3. Repository-Wide Source Migration

- [x] 3.1 Replace every legacy facade import and call in `juno-api`, `juno-compiler`, `juno-maven-plugin`, `juno-examples`, test fixtures, and architecture tests with `MCU`, including the `getSd()` to `getSdCard()` rename and removal of direct MCU-service constructor arguments where a reviewed convenience path exists.
- [x] 3.2 Update API Javadocs, package documentation, `README.md`, `AGENTS.md`, and `juno-site/src/main/resources/content/` to present `MCU` as the only onboard-service facade and explain capability-gated getters.
- [x] 3.3 Add migration documentation mapping every removed facade getter to its `MCU` replacement and mark the release/API change as breaking.
- [x] 3.4 Add an architecture or source-scan test proving repository Java sources and maintained documentation contain no references to the five removed facade types except the migration guide.

## 4. Linker and Target Validation

- [x] 4.1 Route reachable direct calls and interface targets through the shared capability resolver while retaining the existing inheritance and watchdog policy.
- [x] 4.2 Add `JunoCompilerTest` or focused linker coverage for an unsupported `MCU` getter, an unsupported intrinsic, a portable getter, an unreachable capability method, and every board-specific getter family.
- [x] 4.3 Add multi-board tests proving that selecting one capable board cannot bypass an incapable declared board and that a program using only mutually supported `MCU` getters compiles for each declared selection.
- [x] 4.4 Update diagnostics to require the service/capability name, offending board, and available call context without naming a removed facade.
- [x] 4.5 Retain tests for a single declared board, no annotation/no option, multiple declarations/no option, unknown board ID, and a requested board outside the declared set.

## 5. Maven Plugin, Cache, and Generated Output

- [x] 5.1 Extend `CompileMojoTest` so an unannotated entry point without `juno.board` fails with actionable guidance and the same entry point succeeds when a valid `juno.board` is configured.
- [x] 5.2 Verify `juno:compile` delegates annotation/request reconciliation to the compiler and does not add a silent default or duplicate selection policy.
- [x] 5.3 Add tests that changing `@Board` or `juno.board` invalidates or safely regenerates cached `.S`, `Shim.cpp`, wrapper, FQBN, and report output.
- [ ] 5.4 Extend `JunoCompilerTest`/`GeneratedAsmToolchainTest` golden coverage so migrated representative programs retain the required byte-identical generated output and usage-gated helpers.

## 6. Multi-Board and Documentation Verification

- [x] 6.1 Add or extend a CI/toolchain matrix that compiles every declared target for representative multi-board programs using `MCU`.
- [x] 6.2 Run `juno:verify` for representative portable and board-specific `MCU` examples, including every selected target declared by each multi-board example used for verification.
- [x] 6.3 Regenerate the site with `./mvnw clean verify -Psite -DskipTests`, run `./mvnw -pl juno-site test`, and verify the published source contains no legacy facade guidance; do not hand-edit `docs/`.

## 7. Project and Specification Validation

- [x] 7.1 Run `./mvnw test` and resolve compiler, Maven-plugin, API architecture, nullness, and deterministic-generation failures.
- [x] 7.2 Run `./mvnw --batch-mode --no-transfer-progress verify -b turbo -T1C` and resolve Spotless, Enforcer, dependency-analysis, and module failures without adding a dependency or plugin.
- [x] 7.3 Run `openspec validate --all` from `documentation/` and resolve every validation finding.
