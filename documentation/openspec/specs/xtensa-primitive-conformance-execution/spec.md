# xtensa-primitive-conformance-execution Specification

## Purpose
Executes the catalogued primitive programs as Juno-generated Xtensa code for the Nano ESP32 on an emulated ESP32-S3,
so the Xtensa backend is held to the same OpenJDK comparison as the Thumb-2 and AVR backends without hardware.
## Requirements
### Requirement: ESP32-S3 primitive execution runner

The conformance suite SHALL execute required Nano ESP32 primitive programs as Juno-generated Xtensa code on an
ESP32-S3 emulator. The runner SHALL build a bootable emulator image from the generated assembly and shim, capture
UART output, and require deterministic termination without a physical board or external network service.

#### Scenario: Nano ESP32 primitive program executes under emulation

- **WHEN** a primitive conformance source declares the Nano ESP32 board
- **THEN** the Xtensa runner builds the required bootable ESP32-S3 image, runs it under the emulator, and captures
  its serial transcript

#### Scenario: Xtensa transcript matches OpenJDK

- **WHEN** the Xtensa runner executes a required primitive conformance program
- **THEN** it passes only when the captured transcript equals the OpenJDK transcript

#### Scenario: Xtensa execution does not terminate

- **WHEN** the emulator does not emit the required completion marker before the configured timeout
- **THEN** the runner fails with the captured emulator output and command context

### Requirement: Xtensa pilot isolation

The Xtensa primitive runner SHALL cover only the Nano ESP32/ESP32-S3 primitive slice. It SHALL NOT claim
Cardputer-specific behavior, device-peripheral behavior, or general ESP32 application compatibility.

#### Scenario: A Cardputer-specific program is selected

- **WHEN** a source requires a Cardputer-specific API or board behavior
- **THEN** the Xtensa primitive runner does not select it for this pilot

