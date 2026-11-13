---
title: "Board features"
description: "What each board supports beyond the Java language: Wi-Fi, HTTP and email, Bluetooth, USB, displays, shields and storage."
layout: page
---

Every board runs the same Java subset, but the hardware around the program differs. This page summarises the features
that depend on the board rather than on the language: networking, Bluetooth, USB, displays, storage and shields. For the
language itself, see the [Java subset](../java-subset) page.

## Summary

✓ supported · – not available · the notes after the table explain the exceptions.

| Feature | UNO Q | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 | Pico 2 W |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Board-context GPIO pin resolver (`digitalPin`, `analogPin`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Wi-Fi | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | – | ✓ |
| Wi-Fi access point | ✓ | ✓ | ✓ | – | ✓ | ✓ | – | ✓ |
| HTTP client (plain) | – | ✓ | ✓ | – | ✓ | ✓ | – | ✓ |
| HTTPS client | ✓ | ✓ | ✓ | – | ✓ | ✓ | – | ✓ |
| HTTP server | ✓ | ✓ | ✓ | – | ✓ | – | – | ✓ |
| HTTPS server | ✓ | ✓ | ✓ | – | – | – | – | ✓ |
| Email over TLS | ✓ | ✓ | ✓ | – | ✓ | ✓ | – | ✓ |
| Email STARTTLS | – | ✓ | – | – | ✓ | – | – | – |
| SNTP clock (`LocalDateTime.now()`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | – | ✓ |
| Bluetooth LE | ✓ | ✓ | ✓ | ✓ | ✓ | – | – | ✓ |
| USB HID mouse | – | ✓ | ✓ | – | ✓ | – | – | ✓ |
| LED matrix | ✓ (13 × 8) | – | – | – | ✓ (12 × 8) | – | – | – |
| Built-in display, keyboard, speaker, battery | – | – | ✓ | – | – | – | – | – |
| Onboard microSD socket (`MCU.getSdCard()`) | – | – | ✓ | ✓ | – | – | – | – |
| Onboard camera (`MCU.getCamera()`) | – | – | – | ✓ | – | – | – | – |
| Onboard IMU (`MCU.getAccelerometer()`, `MCU.getGyroscope()`) | – | – | – | – | – | ✓ | – | – |
| Hardware random numbers | ✓ | ✓ | ✓ | – | ✓ | ✓ | Weak | – |
| Watchdog | – | ✓ | ✓ | – | ✓ | ✓ | ✓ | ✓ |
| Structured concurrency (threads, scopes, scoped values, locks) | ✓ | ✓ | ✓ | – | ✓ | ✓ | – | ✓ |
| Double-precision `Math` | ✓ | ✓ | ✓ | – | ✓ | – | – | ✓ |
| SD card | Shield | Carrier | Built-in | Built-in | Shield | Shield | Shield | – |
| Grove sockets | Shield | Carrier | – | – | Shield | Shield | Shield | Shield |
| 9 Axis Motion Shield | Shield | – | – | – | Shield | Shield | Shield | – |
| Onboard IMU (`MCU.getGyroscope()`, `MCU.getAccelerometer()`) | – | – | – | – | – | ✓ | – | – |
| LCD Keypad Shield | Shield | – | – | – | Shield | Shield | Shield | – |
| TFT Touch Shield | Shield | – | – | – | Shield | Shield | – | – |
| Infrared link to LEGO RCX | ✓ | ✓ | – | – | ✓ | – | – | – |

Notes:

- **Shield** means the feature comes from an Arduino or ELEGOO shield on the board's headers: the Data Logger, TFT or
  LCD Keypad shields for storage and displays, and the Grove and 9 Axis Motion shields for sensors. See
  [Arduino Shields](../arduino-shields).
- **Carrier** means the Nano Connector Carrier, which provides the Nano ESP32's microSD slot and Grove sockets. See the
  [Nano ESP32](../arduino-nano-esp32#shields) page.
- **Weak** means a non-cryptographic random source, suitable only for best-effort identities. See the
  [Data types](../java-subset#data-types) section.
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
Programs written for the removed per-board facades need the steps on the [migration](../migrate-to-mcu) page.
