## ADDED Requirements

### Requirement: MCU is the unique onboard-service facade

Juno SHALL expose onboard MCU/board services through the final static `io.github.jabrena.juno.hardware.MCU` class. `ArduinoUno`, `ArduinoNano`, `RaspberryPico2`, `Esp32Cam`, and `M5StackCardputer` SHALL be removed without deprecated forwarders or aliases.

#### Scenario: A program accesses a portable service

- **WHEN** a program needs GPIO, I2C, SPI, clock, board-model, or board-memory access supported by its declared boards
- **THEN** it obtains that service from the corresponding `MCU` getter

#### Scenario: Source uses a removed facade

- **WHEN** source still imports or calls one of the five removed facade classes after the breaking migration
- **THEN** Java compilation fails and the migration guide identifies the corresponding `MCU` call

### Requirement: MCU exposes the existing onboard service union

`MCU` SHALL expose the portable/shared getters `getGpio`, `getI2c`, `getSpi`, `getClock`, `getBoardModel`, and `getBoardMemory`, and the capability-gated getters `getMouse`, `getLedMatrix`, `getWifi`, `getBluetoothLe`, `getAccelerometer`, `getGyroscope`, `getCamera`, `getSdCard`, `getDisplay`, `getKeyboard`, `getAudio`, and `getBattery`. This union SHALL NOT imply that every board supports every getter.

#### Scenario: A declared board supports a getter

- **WHEN** every declared board supports the capability required by a reachable `MCU` getter
- **THEN** the linker accepts the getter and the selected board's existing internal provider supplies the service

#### Scenario: A declared board lacks a getter capability

- **WHEN** any declared board lacks the capability required by a reachable `MCU` getter
- **THEN** compilation fails before backend generation with the service and offending board named

### Requirement: MCU access is stateless and zero-allocation

The `MCU` facade SHALL contain no mutable service state, SHALL perform no runtime board-name switch, and SHALL allocate no object per getter call. It SHALL delegate to internal providers while preserving the stable service identity and board-side zero-allocation behavior of existing getters.

#### Scenario: A getter is called repeatedly

- **WHEN** a program calls the same `MCU` service getter more than once
- **THEN** it receives the same service identity without a per-call allocation

#### Scenario: A program runs a host-side unit test

- **WHEN** application logic accepts a service interface returned by `MCU` in production
- **THEN** the test can pass a mock implementation of that interface without executing a native method

### Requirement: Lazy state belongs to internal providers

Lazy onboard IMU and SD-card state SHALL be owned behind internal providers rather than as mutable fields on `MCU`. Accelerometer and gyroscope access SHALL share the existing one-time IMU initialization, and onboard SD-card access SHALL preserve the selected board's existing wiring and stable instance.

#### Scenario: Accelerometer and gyroscope are requested

- **WHEN** a supported program requests `MCU.getAccelerometer()` and `MCU.getGyroscope()`
- **THEN** both views share one lazily initialized onboard IMU with the existing startup behavior

#### Scenario: Onboard SD is requested repeatedly

- **WHEN** a supported program calls `MCU.getSdCard()` more than once
- **THEN** the internal provider returns one lazily created card configured for the selected board's onboard socket

### Requirement: Default construction omits fixed MCU singleton plumbing

When a public component constructor currently receives a service only because normal board code passes the fixed singleton returned by a facade getter, the component SHALL provide a shorter construction path that resolves the corresponding `MCU` service once. The explicit injectable construction path SHALL remain available for mocks, alternate implementations, or non-default hardware topology.

#### Scenario: Normal code uses only fixed board services

- **WHEN** a component needs only fixed MCU-owned singleton collaborators in addition to its real device configuration
- **THEN** normal board code constructs it without passing those singleton services explicitly

#### Scenario: A unit test supplies service doubles

- **WHEN** a test needs mock GPIO, clock, radio, bus, or another service implementation
- **THEN** it uses the retained injectable constructor or factory and the component does not perform an additional `MCU` lookup

#### Scenario: An explicit parameter describes topology

- **WHEN** a parameter selects a pin, address, buffer, channel, device-owned bus, emitter, receiver, transport, or other meaningful hardware topology
- **THEN** that parameter remains explicit rather than being hidden behind `MCU`

#### Scenario: The component begins operating

- **WHEN** a convenience constructor resolves an MCU service
- **THEN** it delegates to the injectable initialization path, stores the collaborator once, and operational methods do not repeat the static lookup

### Requirement: Onboard SD access has one normalized name

`MCU.getSdCard()` SHALL replace both `M5StackCardputer.getSd()` and `Esp32Cam.getSdCard()`. It SHALL represent only an onboard socket; shield and carrier `getSdCard()` methods SHALL remain owned by those hardware types.

#### Scenario: Cardputer accesses its onboard card

- **WHEN** a Cardputer program migrates from `M5StackCardputer.getSd()`
- **THEN** it uses `MCU.getSdCard()` with the existing Cardputer chip-select and bus behavior

#### Scenario: ESP32-CAM accesses its onboard card

- **WHEN** an ESP32-CAM program migrates from `Esp32Cam.getSdCard()`
- **THEN** it uses `MCU.getSdCard()` with the existing ESP32-CAM chip-select and bus behavior

#### Scenario: A shield owns the socket

- **WHEN** a program accesses an SD socket on a shield or carrier
- **THEN** it continues to call that shield or carrier's `getSdCard()` rather than `MCU.getSdCard()`

### Requirement: Repository-owned consumers migrate completely

All maintained Java sources, tests, fixtures, Javadocs, site sources, README guidance, and agent guidance SHALL use `MCU` for onboard services. References to removed facades SHALL remain only where a migration guide names old APIs for users.

#### Scenario: The repository is scanned after migration

- **WHEN** maintained sources and documentation are checked for removed facade names
- **THEN** no reference exists outside the explicit migration guide

#### Scenario: Documentation is published

- **WHEN** the site is regenerated from `juno-site` sources
- **THEN** examples and board pages present `MCU` as the unique onboard-service facade

### Requirement: The facade migration is explicitly breaking

The release containing this change SHALL identify the removal of legacy facades as a breaking API change and SHALL provide a mechanical old-to-new getter mapping.

#### Scenario: A downstream user prepares to upgrade

- **WHEN** the user reads the release or migration notes
- **THEN** the notes state that the five facades were removed and map their getters to `MCU`, including `getSd()` to `getSdCard()`

### Requirement: Target-sensitive builds cannot reuse stale output

Changing `@Board`, `--board`, or `-Djuno.board` SHALL produce or reuse generated files only when their target-sensitive inputs match. The unified source facade SHALL NOT allow one board's constants, FQBN, assembly, shim, wrapper, or report to be uploaded or reported for another board.

#### Scenario: The selected board changes

- **WHEN** a build repeats for the same entry point with a different declared or explicitly selected board
- **THEN** target-sensitive generated output is regenerated or retrieved only from a matching cache entry

### Requirement: Multi-board declarations receive build coverage

Representative multi-board programs using `MCU` SHALL be compiled for every board they declare in CI or equivalent parameterized toolchain verification.

#### Scenario: A representative program declares several boards

- **WHEN** the build matrix validates that program
- **THEN** it invokes compilation once for every declared board and reports any target-specific failure
