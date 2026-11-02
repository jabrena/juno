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

| Feature | UNO R3 | UNO WiFi Rev2 | UNO R4 WiFi | UNO Q | Nano ESP32 | M5Stack Cardputer |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| Wi-Fi | Shield | ✓ | ✓ | ✓ | ✓ | ✓ |
| Wi-Fi access point | – | ✓ | ✓ | ✓ | ✓ | ✓ |
| HTTP client (plain) | Shield | ✓ | ✓ | – | ✓ | ✓ |
| HTTPS client | – | ✓ | ✓ | ✓ | ✓ | ✓ |
| HTTP server | – | – | ✓ | ✓ | ✓ | ✓ |
| HTTPS server | – | – | – | ✓ | ✓ | ✓ |
| Email over TLS | – | ✓ | ✓ | ✓ | ✓ | – |
| Email STARTTLS | – | – | ✓ | – | ✓ | – |
| SNTP clock (`LocalDateTime.now()`) | Shield | ✓ | ✓ | ✓ | ✓ | ✓ |
| Bluetooth LE | – | – | ✓ | ✓ | ✓ | – |
| USB HID mouse | – | – | ✓ | – | ✓ | ✓ |
| LED matrix | – | – | ✓ (12 × 8) | ✓ (13 × 8) | – | – |
| Built-in display, keyboard, speaker, battery | – | – | – | – | – | ✓ |
| Hardware random numbers | Weak | ✓ | ✓ | ✓ | ✓ | Weak |
| Watchdog | ✓ | ✓ | ✓ | – | ✓ | ✓ |
| SD card | Shield | Shield | Shield | Shield | Carrier | Built-in |
| Grove sockets | Shield | Shield | Shield | Shield | Carrier | – |
| 9 Axis Motion Shield | Shield | Shield | Shield | Shield | – | – |
| LCD Keypad Shield | Shield | Shield | Shield | Shield | – | – |
| TFT Touch Shield | – | Shield | Shield | Shield | – | – |
| Infrared link to LEGO RCX | – | – | ✓ | ✓ | ✓ | – |

Notes:

- **Shield** means the feature comes from an Arduino or ELEGOO shield on the board's headers: the WiFi Shield on the R3 for
  Wi-Fi, HTTP and the clock, and the Data Logger, TFT or LCD Keypad shields for storage and displays. See
  [Arduino Shields](../arduino-shields).
- **Carrier** means the Nano Connector Carrier, which provides the Nano ESP32's microSD slot and Grove sockets. See the
  [Nano ESP32](../arduino-nano-esp32#shields) page.
- **Weak** means a non-cryptographic random source, suitable only for best-effort identities. See the
  [Data types](../java-subset#data-types) section.
- **Email STARTTLS** needs an upgrade of a plain connection, which the boards without a TLS stack of their own cannot do.
- **Bluetooth LE on the Nano ESP32** reaches a Powered Up hub through the core's own BLE library; it is not yet confirmed
  on hardware.
- **Infrared** drives a LEGO RCX from the board. The RCX needs Juno's firmware for the buttons and display text; see the
  [LEGO ecosystem](../lego-ecosystem) page.
