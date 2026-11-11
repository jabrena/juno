## Why

The Raspberry Pi Pico 2 W currently exposes GPIO, Wi-Fi, and basic runtime support, but rejects several APIs that the shared Thumb-2 backend already supports on other boards. Completing the Pico 2 W port will make its documented feature set useful for real networked, concurrent, and peripheral programs.

## What Changes

- Add Pico 2 W support for HTTP client/server APIs.
- Add TLS for HTTPS client/server and TLS email APIs.
- Add Bluetooth LE, `@Watchdog`, Java threads/structured concurrency/locks, double-precision `Math`, and USB HID mouse support.
- Map the Grove Shield for Pi Pico UART0, UART1, and SPI0 sockets.
- Update Pico 2 W documentation and the `RaspberryPico2GroveOled` example to print `Hello World` on one row.
- Add focused compiler, generated-toolchain, API, and mapping tests for each feature.

## Capabilities

### New Capabilities

- `pico-2-runtime`: Pico 2 W implementations of the requested networking, security, concurrency, radio, watchdog, math, HID, and Grove transport capabilities.

### Modified Capabilities

- `pico-2-board-support`: Update the board's documented support matrix and hardware-validation notes.

## Impact

The change affects `juno-compiler` board capabilities and RP2350 runtime shims, `juno-api` Grove shield mappings, compiler/API tests, Pico 2 examples, and the source documentation under `juno-site/src/main/resources/content/`. No new Maven dependency is assumed; any Arduino-side library required by the core must be installed through the existing dependency workflow.
