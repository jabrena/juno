---
title: "Board features"
description: "What each board supports beyond the Java language: Wi-Fi, HTTP and email, Bluetooth, USB, displays, shields and storage."
layout: page
---

Every board runs the same Java subset, but the hardware around the program differs. This page summarises the features
that depend on the board rather than on the language: networking, Bluetooth, USB, displays, storage and shields. For the
language itself, see the [Java subset](../java-subset) page.

## API support

✓ supported · — not supported by Juno on this board

| Juno API or feature | Portenta H7 | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | UNO Q | Raspberry Pi Pico 2 W | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| GPIO and board-context pin resolution | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `BigInteger`, `BigDecimal` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Explicit PSRAM byte buffers (`Psram`) | — | ✓ | — | ✓ | — | — | — | — | — |
| Double-precision `Math` functions | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | — |
| Threads, `StructuredTaskScope`, `ScopedValue`, locks | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Wi-Fi | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Wi-Fi access point | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | — |
| HTTP client | — | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ | — |
| HTTPS client | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTP server | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTPS server | — | ✓ | ✓ | — | ✓ | ✓ | — | — | — |
| HTTP response streaming (`HttpServer.beginStream`) | — | — | — | ✓ | — | — | — | — | — |
| Onboard microSD (`MCU.getSdCard()`) | — | — | ✓ | ✓ | — | — | — | — | — |
| Email over TLS (`Smtp.sendTls`, `Pop3Client`) | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | — |
| Email over STARTTLS (`Smtp.send`) | — | ✓ | — | — | — | — | ✓ | — | — |
| Bluetooth LE (LEGO Powered Up) | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | — |
| USB HID mouse | — | ✓ | ✓ | — | — | ✓ | ✓ | — | — |
| Onboard LED matrix | — | — | — | — | ✓ | — | ✓ | — | — |
| Onboard IMU (`MCU.getGyroscope()`, `MCU.getAccelerometer()`) | — | — | — | — | — | — | — | ✓ | — |
| Camera (`MCU.getCamera()`) | — | — | — | ✓ | — | — | — | — | — |
| `@Watchdog` | — | ✓ | ✓ | — | — | ✓ | ✓ | ✓ | ✓ |
| Cryptographic `UUID.randomUUID()` | — | ✓ | ✓ | — | ✓ | — | ✓ | ✓ | — |
| Display, keyboard, speaker, battery | — | — | ✓ | — | — | — | — | — | — |

The compiler checks this table for you: a program that uses an API its target board does not support fails with a
`CompileException` naming the board and the missing capability. Some cells are compiled support that has not yet been run
on every physical board; each board's page notes what is confirmed on hardware.

## Physical expansions

✓ supported · — not available

| Expansion | Portenta H7 | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | UNO Q | Raspberry Pi Pico 2 W | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| SD card | — | Carrier | Built-in | Built-in | — | — | Shield | Shield | Shield |
| Grove sockets | — | Carrier | — | — | Shield | Shield | Shield | Shield | Shield |
| 9 Axis Motion Shield | — | — | — | — | Shield | — | Shield | Shield | Shield |
| LCD Keypad Shield | — | — | — | — | Shield | — | Shield | Shield | Shield |
| TFT Touch Shield | — | — | — | — | Shield | — | Shield | — | — |
| Infrared link to LEGO RCX | — | ✓ | — | — | ✓ | — | ✓ | — | — |

Notes:

- **Shield** means the feature comes from an Arduino or ELEGOO shield on the board's headers: the Data Logger, TFT or
  LCD Keypad shields for storage and displays, and the Grove and 9 Axis Motion shields for sensors. See
  [Arduino Shields](../arduino-shields).
- **Carrier** means the Nano Connector Carrier, which provides the Nano ESP32's microSD slot and Grove sockets. See the
  [Nano ESP32](../arduino-nano-esp32#shields) page.
- **Structured concurrency** is available on every board except the UNO R3. The UNO R3 WiFi Rev2, UNO R4 WiFi and
  UNO Q run one carrier; the Nano ESP32, AI Thinker ESP32-CAM, M5Stack Cardputer and Raspberry Pi Pico 2 W can run two
  carriers concurrently. See [Concurrency behaviour](../java-subset#concurrency-behaviour) for scheduling and memory
  limits.
- **Email STARTTLS** needs an upgrade of a plain connection, which the boards without a TLS stack of their own cannot do.
- **Bluetooth LE on the Nano ESP32 and the M5Stack Cardputer** reaches a Powered Up hub through the core's own BLE library:
  Bluedroid on the Nano, NimBLE on the Cardputer. Both are confirmed on hardware with the colour example.
- **Infrared** drives a LEGO RCX from the board. The RCX needs Juno's firmware for the buttons and display text; see the
  [LEGO ecosystem](../lego-ecosystem) page.

## Reading the board services through `MCU`

Every board's own services come from one facade, `MCU`, and the board is chosen by `@Board` or the build's `juno.board`
option. A getter the target does not provide is rejected at compile time, and the diagnostic names the getter and the
board. A multi-board program must support each reachable getter on every declared board, even when the build selects one
of them. Portable getters (`getGpio`, `getI2c`, `getSpi`, `getClock`, `getBoardModel`, `getBoardMemory`) work on every board.
