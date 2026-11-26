---
title: "Board features"
description: "What each board supports beyond the Java language: Wi-Fi, HTTP and email, Bluetooth, USB, displays, shields and storage."
layout: page
---

Every board runs the same Java subset, but the hardware around the program differs. This page summarises the features
that depend on the board rather than on the language: networking, Bluetooth, USB, displays, storage and shields. For the
language itself, see the [Java subset](../java-subset) page.

## API support

| Juno API or feature | Portenta H7 | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | Raspberry Pi Pico 2 W | UNO R4 WiFi | UNO R3 |
| --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| GPIO and board-context pin resolution | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `BigInteger`, `BigDecimal`, `MathContext` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Explicit PSRAM byte buffers (`Psram`) | — | ✓ | — | ✓ | — | — | — |
| Double-precision `Math` functions | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| Threads, `synchronized`, locks, `StructuredTaskScope`, `ScopedValue` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Wi-Fi station | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Wi-Fi access point | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| HTTP client | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTPS client | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTP server | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTPS server | ✓ | ✓ | ✓ | — | ✓ | — | — |
| Onboard microSD (`MCU.getSdCard()`) | — | — | ✓ | ✓ | — | — | — |
| Email over TLS (`Smtp.sendTls`, `Pop3Client`) | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| Email over STARTTLS (`Smtp.send`) | ✓ | ✓ | — | ✓ | — | ✓ | — |
| Bluetooth LE | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| USB HID mouse | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| Onboard LED matrix | — | — | — | — | — | ✓ | — |
| Camera (`MCU.getCamera()`) | — | — | — | ✓ | — | — | — |
| `@Watchdog` | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| `UUID.randomUUID()` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `SecretStore` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Display, keyboard, speaker, battery | — | — | ✓ | — | — | — | — |

Legend: ✓ supported · — not available

The AI Thinker ESP32-CAM supports Wi-Fi station and access-point modes, HTTPS server sessions, both implicit-TLS and
STARTTLS email, the onboard microSD socket, Bluetooth LE for LEGO Powered Up, the OV2640 camera, HTTP response
streaming, explicit PSRAM buffers, the task watchdog, hardware-random UUIDs and double-precision `Math`. It has no
native USB HID controller, onboard LED matrix or onboard IMU.

The UNO R3 also supports `UUID.randomUUID()`, but its AVR implementation is non-cryptographic and suitable only for
best-effort identities, never for secrets, tokens or authentication values.

The UNO R4 WiFi serves HTTP but not HTTPS: its ESP32-S3 coprocessor's firmware offers TLS only to clients, and the
RA4M1 has no room for a software TLS server.

The compiler checks this table for you: a program that uses an API its target board does not support fails with a
`CompileException` naming the board and the missing capability. Some cells are compiled support that has not yet been run
on every physical board; each board's page notes what is confirmed on hardware.

## Secret storage

Juno's [`SecretStore`](../../api/storage/secure-element) API reads named Wi-Fi, API and email credentials from the
board's built-in store: the Portenta H7's NXP SE050C2, ESP32 boards' NVS partitions, the Pico 2 W's TrustZone store,
or the UNO R3's EEPROM. Secrets stay outside the firmware image and the SD card, although the NVS and EEPROM stores
are not secure elements and can be exposed by flash or EEPROM readout.

| Setup | Boards | Notes |
|---|---|---|
| Built in | Portenta H7 | The board includes an NXP SE050C2; `MCU.getSecretStore()` selects it by board, so programs use the same `SecretStore` API. |
| Built in, in flash | Nano ESP32, M5Stack Cardputer, AI Thinker ESP32-CAM and UNO R4 WiFi | No crypto chip: `MCU.getSecretStore()` uses the board's NVS partition, which keeps the value out of the program but not out of flash. |
| Built in, protected by TrustZone | Raspberry Pi Pico 2 W | The store is fenced off by Juno's secure firmware, but it is not locked or encrypted. |
| Built in, in EEPROM | UNO R3 | No crypto chip: `MCU.getSecretStore()` uses the microcontroller's EEPROM, plain text that any flashed program or a programmer can read. |

The chip must be provisioned with `juno:secret` before the application reads its secrets by name. The [secure-element
guide](../../api/storage/secure-element) covers provisioning, wiring, missing-secret handling and the current hardware-validation
status.

## Physical expansions

| Expansion | Portenta H7 | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | Raspberry Pi Pico 2 W | UNO R4 WiFi | UNO R3 |
| --- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| SD card | — | Carrier | Built-in | Built-in | — | Shield | Shield |
| Grove sockets | — | Carrier | — | — | Shield | Shield | Shield |
| Portenta Hat Carrier | Carrier | — | — | — | — | — | — |
| 9 Axis Motion Shield | — | — | — | — | — | Shield | Shield |
| LCD Keypad Shield | — | — | — | — | — | Shield | Shield |
| Data Logger Shield | — | — | — | — | — | Shield | Shield |
| TFT Touch Shield | — | — | — | — | — | Shield | — |
| USB Host Shield | — | — | — | — | — | Shield | — |
| PCA9685 PWM and Servo Shield | — | — | — | — | — | Shield | — |

Legend: ✓ supported · — not available

The Grove infrared emitter and receiver provide an Infrared link to LEGO RCX systems through the supported Grove
expansion hardware.

## Reading the board services through `MCU`

Every board's own services come from one facade, `MCU`, and the board is chosen by `@Board` or the build's `juno.board`
option. A getter the target does not provide is rejected at compile time, and the diagnostic names the getter and the
board. A multi-board program must support each reachable getter on every declared board, even when the build selects one
of them. Get services once at startup and pass the interfaces to application objects so desktop tests can substitute
mocks.

| Method | Returns | Availability |
|---|---|---|
| `getGpio()` | `Gpio` | Every board |
| `getI2c()` | `I2c` | Every board |
| `getSpi()` | `Spi` | Every board |
| `getClock()` | `Clock` | Every board |
| `getBoardModel()` | `BoardModel` | Every board |
| `getBoardMemory()` | `BoardMemory` | Every board |
| `getWifi()` | `Wifi` | Every board except UNO R3 |
| `getBluetoothLe()` | `BluetoothLe` | Every board except UNO R3 |
| `getMouse()` | `Mouse` | Portenta H7, Nano ESP32, M5Stack Cardputer, Raspberry Pi Pico 2 W and UNO R4 WiFi |
| `getCamera()` | `Camera` | AI Thinker ESP32-CAM |
| `getSdCard()` | `SdCard` | M5Stack Cardputer and AI Thinker ESP32-CAM; shields and carriers expose their own card getters |
| `getDisplay()` | `Display` | M5Stack Cardputer and UNO R4 WiFi |
| `getKeyboard()` | `Keyboard` | M5Stack Cardputer |
| `getAudio()` | `Audio` | M5Stack Cardputer |
| `getBattery()` | `Battery` | M5Stack Cardputer |
| `getSecretStore()` | `SecretStore` | Every board; the backing store differs by board |
| `getLanguageModel()` | `LanguageModel` | When a compatible JLM1 model is linked for the target board |
| `getDecisionModel()` | `DecisionModel` | When a compatible JEV1 model is linked for the target board |
