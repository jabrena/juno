## Context

The Pico 2 W already uses the shared Thumb-2 backend and the arduino-pico `rp2040` core. The compiler has existing capability gates and reusable generated runtime components for HTTP, TLS, BLE, threads, watchdogs, double math, HID, I²C, and SPI, but the RP2350 core adapter currently rejects most of them. The core installed by the project provides `WiFiClient`, `WiFiServer`, `WiFiClientSecure`, `WiFiServerSecure`, `BLE`, `Mouse`, and the RP2350 Cortex-M33 toolchain. Bluetooth must be enabled through the core's `ipbtstack=ipv4btcble` board option.

## Goals / Non-Goals

**Goals:**

- Extend the RP2350 `CoreRuntime` with the smallest core-specific adapters needed by the existing Juno lowerings.
- Keep board decisions in `Board` and core decisions in `Rp2350CoreRuntime`; do not add Pico-specific branches to shared backend lowering.
- Reuse the existing Thumb-2 SysTick thread port, HTTP codec, email codec, BearSSL-compatible client/server interfaces, and common HID/BLE abstractions where their contracts match.
- Add deterministic compiler/toolchain tests and update source documentation and examples.

**Non-Goals:**

- Adding a new public Juno API or changing the Java subset.
- Supporting BLE and Juno's thread scheduler concurrently if the arduino-pico core configuration makes that combination unsafe; such incompatibility must remain an explicit documented build/runtime constraint if discovered.
- Claiming physical-board validation without running the relevant upload and serial-monitor checks.

## Decisions

- **Use the arduino-pico native networking classes.** Plain HTTP will use `WiFiClient`/`WiFiServer`; TLS will use `BearSSL::WiFiClientSecure` and `BearSSL::WiFiServerSecure`, with the shared HTTP and email codecs adapted through a Pico runtime source. This preserves the existing caller-owned-buffer contracts and certificate validation model without adding a Maven dependency.
- **Add a Pico BLE adapter rather than forcing ArduinoBLE compatibility.** The core exposes `BLE`, `BLEClient`, `BLERemoteService`, and `BLERemoteCharacteristic` with a similar but distinct API. A dedicated adapter will translate the existing LEGO central contract and preserve the existing bounded notification queue.
- **Reuse the non-RTOS Thumb-2 scheduler port.** The existing RP2350 core runs generated code directly and can use the same stack-switching/SysTick model as the UNO R4. This avoids enabling the core's FreeRTOS mode, which is not required by Juno and is documented as incompatible with the core BLE library.
- **Use the core's `Mouse` library.** The shared mouse helpers already express the Juno mouse contract; Pico will use the default `Mouse.h` include and object.
- **Map Grove transport pins through `GrovePicoShield` and core runtime pin setters.** UART0, UART1, and SPI0 will expose caller-visible pin getters backed by the board's printed socket map, while the core adapter will configure the selected hardware peripheral before use.
- **Enable Pico Bluetooth in the board FQBN.** The Pico 2 W board target will use the arduino-pico `ipbtstack=ipv4btcble` option so BLE sketches link the required BT stack. The generated feature support remains capability-gated at compile time.

## Risks / Trade-offs

- [Core API drift] → Compile the generated sketches with the installed arduino-pico core and keep adapter code isolated in RP2350 runtime resources.
- [TLS RAM/flash cost] → Emit TLS helpers only when a generated program uses HTTPS or TLS email, as the existing shim does for other boards.
- [BLE configuration cost] → The Bluetooth-enabled FQBN links a larger core even for non-BLE programs; document this as the board target's required configuration and verify ordinary Pico sketches still fit.
- [Thread/BLE interaction] → Keep BLE and scheduler tests separate initially, then test the combined feature explicitly before documenting it as supported.
- [Physical hardware availability] → Mark source-level and Arduino CLI verification separately from upload/monitor verification; do not mark hardware support confirmed until serial output is observed.

## Migration Plan

1. Add focused tests and RP2350 runtime implementations one feature at a time, committing each feature independently.
2. Run module tests after each Java/compiler feature and Arduino CLI compilation for generated Pico sketches where available.
3. Update the Pico 2 W page and Grove OLED example after the implementation commits.
4. If a core limitation prevents a feature from being safely supported, retain the capability rejection and document the concrete limitation rather than shipping a false positive.

## Open Questions

- Whether the exact installed core's BLE stack remains stable when the Juno stack-switching scheduler is also enabled; this requires a combined generated-sketch compile and, ideally, hardware validation.
- Whether the Grove UART/SPI sockets should expose full Juno `Serial`/`Spi` objects or only pin mappings; the implementation must follow the existing API contracts discovered in the repository.
