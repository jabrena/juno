## Context

Juno currently exposes onboard services through five public static facades: `ArduinoUno`, `ArduinoNano`, `RaspberryPico2`, `Esp32Cam`, and `M5StackCardputer`. The entry point's `@Board` annotation or explicit board option already selects the actual target, so facade names duplicate target identity and become misleading in multi-board programs. Current `main` contains hundreds of Java and documentation references to these types.

The facades overlap heavily (`getGpio`, `getI2c`, `getClock`, `getWifi`, `getBluetoothLe`, and others) while board-specific services are distributed among them. `M5StackCardputer.getSd()` and `Esp32Cam.getSdCard()` expose the same kind of onboard socket under different names. `ArduinoUno` also owns lazy IMU state, and the Cardputer and ESP32-CAM facades own lazy SD-card state.

The compiler already records supported features as `Set<Capability>` data on each `Board`. Intrinsic calls are mapped to capabilities in `Linker`, and `requireCapability` checks every board declared by `@Board`. The remaining exception is `REQUIRED_METHOD_CAPABILITIES`, which enumerates selected concrete facade `MethodRef` owners and can miss equivalent getters on another facade.

Target selection is already enforced by `Linker`: an unannotated entry point requires a requested board, a single declared board is selected implicitly, multiple declared boards require an explicit selection, and an explicit selection must belong to the declared set. `AbstractJunoMojo` passes `juno.board` to this path, but the Maven goal lacks a direct missing-board regression test.

The issue originally gated the unified facade and recommended deprecated forwarders. The maintainer has now approved one unique facade and direct removal. That clarification makes this an intentional breaking change.

## Goals / Non-Goals

**Goals:**

- Make `MCU` the only public facade for onboard services across all supported boards.
- Remove every old facade and migrate the complete repository in one breaking change.
- Remove redundant constructor parameters where callers only pass fixed services obtained from `MCU`, without removing dependency-injection seams.
- Keep board capability support authoritative and independent of public facade owner names.
- Reject unsupported `MCU` services at compile time for every declared board.
- Preserve the all-declared-boards portability contract and explicit target selection behavior.
- Preserve zero-allocation board execution, interface-based testing, backend independence, and deterministic output.

**Non-Goals:**

- Renaming `@Board` target annotation types such as `ArduinoUnoR4WiFiBoard` or compiler `Board` enum constants; these identify compilation targets rather than service facades.
- Adding boards, physical peripherals, intrinsic operations, Maven dependencies, or Maven plugins.
- Moving shield-owned sockets such as `DataLoggerShield.getSdCard()` onto `MCU`; `MCU.getSdCard()` represents only an onboard socket.
- Relaxing `@Board` so only the selected board must compile.
- Adding runtime board detection or branching in an assembly backend.

## Decisions

### 1. Deliver capability refactoring and facade replacement atomically

The change first makes ordinary getter validation independent of facade owners and then replaces all facades with `MCU`. Although the capability cleanup can be described separately, shipping it with the facade migration prevents a transition state where the linker must maintain both old and new owner maps. One breaking release, one source migration, and one rollback boundary make this a single reviewable change.

### 2. Make `MCU` the sole public onboard-service facade

Add a final, non-instantiable `MCU` class under `io.github.jabrena.juno.hardware`. It exposes the union of existing onboard getters:

- portable/shared access: `getGpio`, `getI2c`, `getSpi`, `getClock`, `getBoardModel`, and `getBoardMemory`;
- capability-gated access: `getMouse`, `getLedMatrix`, `getWifi`, `getBluetoothLe`, `getAccelerometer`, `getGyroscope`, `getCamera`, `getSdCard`, `getDisplay`, `getKeyboard`, `getAudio`, and `getBattery`.

The old facade classes are deleted in the same change. No deprecated forwarders, aliases, or duplicate entry points remain. This favors a clean unique API over a staged compatibility period, as explicitly requested by the maintainer. Release notes provide the mechanical import/call migration.

`@Board` remains the source of target identity. A call such as `MCU.getWifi()` communicates the required service without pretending the source is tied to an UNO, Nano, Pico, ESP32-CAM, or Cardputer facade.

### 3. Keep `MCU` stateless and zero-allocation

Each `MCU` getter delegates to the existing internal native provider or a dedicated internal provider object. `MCU` contains no mutable singleton state, performs no target-name switch, and allocates no object per call.

Lazy state currently held by public facades moves behind internal providers:

- the UNO WiFi Rev2 IMU provider owns one lazy `Lsm6ds3` shared by `getAccelerometer()` and `getGyroscope()`;
- the onboard SD-card provider owns board-appropriate chip-select/bus configuration and returns one lazy `SdCard` for `getSdCard()`.

The service interfaces remain the testing boundary. Application code continues to receive and pass `Gpio`, `Wifi`, `Clock`, `Camera`, and other interfaces, so unit tests inject mocks without executing native methods.

### 4. Make MCU-owned singleton constructor arguments optional

Repository call sites currently construct many components by immediately passing a facade singleton, for example a board `Gpio` to a shield/carrier, or the board `BluetoothLe` and `Clock` to `RemotePoweredUpHub`. Once `MCU` is the unique facade, requiring every caller to repeat those lookups adds plumbing without expressing a choice.

Audit every constructor invocation that passes a legacy facade getter directly. When the collaborator is a fixed MCU-owned singleton and the class has no meaningful production choice for that argument, add a convenience constructor or clearly named factory that resolves the service from `MCU` once and delegates to the injectable constructor. Candidate families include board shields/carriers, `RemotePoweredUpHub`, RCX links/controllers, timers on navigation/device classes, and I2C/GPIO devices whose default bus is unambiguous.

The explicit constructor remains when it is an established public testing seam or supports an alternate implementation. The constructed object stores the resolved collaborator in its existing `final` field; operational methods do not repeatedly call `MCU`. This balances concise board code with dependency injection and deterministic object state.

Do not hide parameters that express actual topology or ownership:

- signal/chip-select pins, addresses, buffers, channels, and external emitter/receiver objects remain explicit;
- a bus or service supplied by a shield, carrier, hub, or other device remains explicit;
- constructors for components that legitimately support multiple buses, clocks, transports, or mock implementations retain those injection forms;
- no convenience overload may create an additional service or device object on each call.

Prefer constructor delegation when the shorter signature is unambiguous. Use a named static factory only when overloads would be ambiguous or obscure whether the default MCU service is selected.

### 5. Normalize onboard SD access as `MCU.getSdCard()`

`M5StackCardputer.getSd()` and `Esp32Cam.getSdCard()` become `MCU.getSdCard()`. The selected board's internal provider supplies its existing chip select and bus configuration. Boards without an onboard SD socket fail capability validation; shield-owned `getSdCard()` methods remain unchanged because the shield, not the MCU/board, owns those sockets.

The compiler gains an onboard-SD capability classification if no existing capability expresses this distinction. This models existing hardware access; it does not add SD support to another board.

### 6. Separate board support data from call requirement data

`Board` remains the sole authority for the capabilities a target supports. A linker-owned, object-oriented capability-requirement resolver determines whether a reachable operation requires a capability. It is a collaborator of `Linker`, not a backend concern and not a static utility spread across API facades.

For intrinsics, the resolver preserves explicit intrinsic-to-capability metadata. For ordinary `MCU` getters, it classifies methods through central service/capability metadata rather than facade owner lists. Portable calls return no capability requirement. Capability labels are service-oriented rather than tied to a removed facade name.

Alternatives rejected:

- Adding `MCU` to `REQUIRED_METHOD_CAPABILITIES` would keep the hand-maintained pattern and make future getters easy to omit.
- Keeping deprecated facades would violate the requested single-facade end state.
- Public method annotations would expand the board-side class-file contract for an internal compiler concern.
- Backend checks would violate board-independent backend design and detect errors too late.

### 7. Check the complete declared-board set

Capability validation runs against every distinct board declared by `@Board`. When there is no annotation, an explicit requested board forms a singleton declared set. `--board` and `-Djuno.board` select the FQBN, memory profile, and generated artifact; they do not waive portability for the remaining declared targets.

This preserves the `@Board` Javadoc promise. A multi-board program can call an `MCU` getter only when every declared board supports it. Board-specific programs expose additional getters through the same type and receive a compile-time diagnostic when the selected/declaration set is incompatible.

### 8. Validate only reachable use through one policy

The closed-world linker validates requirements as it discovers reachable direct calls and interface-dispatch targets. Inheritance and watchdog checks use the same `requireCapability` policy. Merely having `MCU` or a capability-dependent method on the classpath does not fail compilation.

An unsupported reachable operation raises `CompileException` before backend generation. The message names the service/capability, an offending board, and call context when available.

### 9. Migrate all repository-owned consumers in the breaking change

All Java imports and calls in `juno-api`, `juno-compiler` fixtures/tests, `juno-maven-plugin`, and `juno-examples` move to `MCU`. Call sites use the shorter construction form when their only explicit service arguments were direct facade getters. All source documentation, Javadoc links, site content, README guidance, and `AGENTS.md` examples move with them. Generated `docs/` is rebuilt from `juno-site`; it is never edited directly.

Architecture tests prevent the removed facade types from returning and require `MCU` to stay within the API's dependency and zero-allocation boundaries. Migration notes provide a mapping from each old getter, including `getSd()` to `getSdCard()`.

### 10. Verify target-sensitive caching and multi-board builds

Because one source facade now serves every board, tests verify that changing `@Board` or `juno.board` cannot reuse stale `.S`, `Shim.cpp`, wrapper, FQBN, or report output. Any existing cache key must include all target inputs or output must be regenerated safely.

CI or equivalent parameterized toolchain tests compile every declared target of selected multi-board fixtures. This makes the documented all-declared-board contract observable rather than relying on one selected build.

### 11. Preserve generated behavior

The migration does not change intrinsic lowering or backend selection. For equivalent migrated programs, generated assembly, shim helpers, reports, and runtime behavior remain deterministic and byte-identical wherever the repository's golden-output contract applies. Any unavoidable symbol delta must be surfaced and approved rather than silently accepted.

## Risks / Trade-offs

- **[Immediate removal breaks downstream source]** → Mark the release breaking and publish a complete mechanical migration table; do not imply binary/source compatibility.
- **[`MCU` exposes getters unavailable on some boards]** → Use complete reachable-call capability validation with diagnostics naming the incompatible board and service.
- **[A return/service-based rule could classify an unrelated method]** → Limit metadata to Juno's known service contracts and test every `MCU` getter positively and negatively.
- **[Moving lazy state can allocate repeatedly or change identity]** → Put state in dedicated internal providers and test singleton identity, lazy initialization, failure, and retry behavior where applicable.
- **[Convenience constructors can become a service locator and reduce testability]** → Resolve `MCU` only in the short construction path, delegate to and retain explicit injectable constructors, and store collaborators in final fields.
- **[Removing too many parameters can hide hardware topology]** → Remove only direct MCU-singleton plumbing; keep pins, addresses, buffers, device-owned buses, transports, and alternative collaborators explicit.
- **[A shared SD getter can hide different wiring]** → Keep bus/chip-select details in board-aware internal provider data and document that `MCU.getSdCard()` means an onboard socket only.
- **[One facade can obscure discoverability]** → Document a per-board capability table and rely on compile-time diagnostics; `@Board` remains explicit at the entry point.
- **[Target changes could reuse stale output]** → Verify cache invalidation using changed annotation and option inputs before accepting the migration.
- **[Hundreds of call sites increase migration risk]** → Perform a mechanical repository-wide migration, use architecture/search assertions, and verify representative real toolchains.

## Migration Plan

1. Introduce the owner-independent capability-requirement resolver and full getter characterization tests.
2. Add stateless `MCU` plus internal IMU/SD providers and capability coverage.
3. Add and test convenience construction paths for direct MCU-singleton dependencies while retaining injection overloads.
4. Migrate every repository-owned consumer, test, fixture, and source document, using the shortest correct construction form.
5. Delete the five legacy facade classes and add architecture checks preventing their reintroduction.
6. Update diagnostics, cache tests, per-board build coverage, migration notes, and generated site output.
7. Run unit, architecture, deterministic-output, Maven-plugin, full verify, and Arduino toolchain checks.

Rollback reverts the complete breaking change, restoring the five facades and old call sites together. There is no mixed old/new facade state and no persisted data or firmware-format migration.

## Open Questions

No scope decision remains open for the facade name or compatibility window: the change uses `MCU` and removes the legacy facades directly. Implementation must still inventory whether any current getter needs a newly named capability beyond onboard SD; such metadata additions may model only existing behavior and may not add hardware support.

## Implementation decisions (recorded during implementation)

12. **Example call sites keep the explicit form.** The convenience constructors exist (Decision 4), but `juno-examples`
    keeps `new NanoConnectorCarrier(MCU.getGpio())` and similar calls. Shortening them changed the generated assembly of
    many examples (inlining and function numbering), which breaks the repository's byte-identical example rule. The
    convenience paths remain part of the API and are tested for identical behavior.

13. **Accepted delta: onboard SD programs.** Byte comparison against `HEAD` over all 224 example entry points: 213 are
    identical. The 11 that call `MCU.getSdCard()` (Cardputer and ESP32-CAM) differ. The chip select is still 12 or 13, but
    now it comes from board data through `BOARD_ONBOARD_SD_CHIP_SELECT`, and the accessor is a separate function
    (`NativeOnboardSdCard.sdCard`) instead of the facade's inlined field. This is the "unavoidable symbol delta" of
    Decision 11 and needs explicit approval before it is accepted as the new golden output.
