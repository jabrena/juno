## ADDED Requirements

### Requirement: Pico 2 W HTTP networking
The Pico 2 W SHALL support Juno plain HTTP client and server APIs using the board's arduino-pico Wi-Fi transport, with the same bounded-buffer and single-request semantics as the existing Thumb-2 implementation.

#### Scenario: Plain HTTP client compiles
- **WHEN** a Pico 2 W program uses `HttpClient` with an `http://` URL
- **THEN** the compiler SHALL emit a Pico-compatible `WiFiClient` transport and SHALL not reject the capability

#### Scenario: Plain HTTP server compiles
- **WHEN** a Pico 2 W program uses `HttpServer.begin` and `HttpServer.accept`
- **THEN** the compiler SHALL emit a Pico-compatible `WiFiServer` transport and SHALL not reject the capability

### Requirement: Pico 2 W TLS networking and email
The Pico 2 W SHALL support HTTPS client/server operations and TLS email operations through the arduino-pico BearSSL client/server classes, including caller-supplied trust anchors where the Juno API exposes them.

#### Scenario: HTTPS client compiles
- **WHEN** a Pico 2 W program uses an `https://` URL or an HTTPS request intrinsic
- **THEN** the compiler SHALL emit a `WiFiClientSecure`-compatible transport and SHALL preserve certificate validation

#### Scenario: HTTPS server compiles
- **WHEN** a Pico 2 W program uses `HttpServer.beginTls`
- **THEN** the compiler SHALL emit a `WiFiServerSecure`-compatible transport and SHALL accept the Juno certificate/key inputs

#### Scenario: TLS email compiles
- **WHEN** a Pico 2 W program uses implicit-TLS email APIs
- **THEN** the compiler SHALL emit the existing email codec with a Pico-compatible TLS client

### Requirement: Pico 2 W Bluetooth LE
The Pico 2 W SHALL support the Juno Bluetooth LE central contract used by the LEGO Powered Up API through the arduino-pico BLE library.

#### Scenario: BLE central compiles
- **WHEN** a Pico 2 W program connects to a BLE service and characteristic
- **THEN** the compiler SHALL emit the Pico BLE adapter and the board target SHALL select the Bluetooth-enabled core configuration

### Requirement: Pico 2 W watchdog
The Pico 2 W SHALL support `@Watchdog` with a hardware watchdog setup and refresh implementation appropriate for the RP2350 core.

#### Scenario: Watchdog program compiles
- **WHEN** a Pico 2 W entry point is annotated with `@Watchdog`
- **THEN** the compiler SHALL emit watchdog initialization, refresh, and panic-reset behavior

### Requirement: Pico 2 W concurrency
The Pico 2 W SHALL support Juno threads, locks, `StructuredTaskScope`, and `ScopedValue` through the shared Thumb-2 scheduler and runtime.

#### Scenario: Thread program compiles
- **WHEN** a Pico 2 W program starts a virtual thread or uses a supported lock
- **THEN** the compiler SHALL emit the RP2350 Thumb-2 thread port and scheduler without rejecting the capability

#### Scenario: Structured concurrency program compiles
- **WHEN** a Pico 2 W program uses `StructuredTaskScope` or `ScopedValue`
- **THEN** the compiler SHALL emit the existing structured-task and scoped-binding helpers over the RP2350 scheduler

### Requirement: Pico 2 W double math
The Pico 2 W SHALL support the existing Juno double-precision `Math` intrinsic set with double-precision results.

#### Scenario: Double math program compiles
- **WHEN** a Pico 2 W program calls a supported double-precision `Math` function
- **THEN** the compiler SHALL emit the Thumb-2 double runtime and SHALL not reject `DOUBLE_MATH`

### Requirement: Pico 2 W USB HID mouse
The Pico 2 W SHALL support Juno mouse operations through the arduino-pico USB mouse library.

#### Scenario: Mouse program compiles
- **WHEN** a Pico 2 W program obtains the board mouse and moves it
- **THEN** the compiler SHALL emit the core's `Mouse` implementation and SHALL not reject `USB_HID`

### Requirement: Grove Pico Shield transport mappings
`GrovePicoShield` SHALL expose documented UART0, UART1, and SPI0 socket pin mappings for the Pico 2 W.

#### Scenario: Grove transport pins are resolved
- **WHEN** a program asks the shield for UART0, UART1, or SPI0 signal pins
- **THEN** the returned pins SHALL match the physical shield labels and the mapping test SHALL cover every signal

#### Scenario: Grove OLED example output
- **WHEN** `RaspberryPico2GroveOled` runs
- **THEN** it SHALL display exactly `Hello World` on one row
