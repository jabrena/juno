---
title: "Boards"
description: "The boards Juno compiles for, side by side: hardware, memory, backend and which Juno APIs each one supports."
layout: page
---

Juno compiles the same Java program for seven boards through three backends: **AVR** for the 8-bit
UNO R3 and UNO WiFi Rev2, **Thumb-2** for the Arm Cortex-M UNO R4 WiFi and UNO Q, and **Xtensa** for
the ESP32-S3 Nano ESP32 and M5Stack Cardputer. Pick the target with the entry point's `@Board`
annotation or with `-Djuno.board=<id>`.

## Hardware at a glance

Boards are ordered from the most capable to the smallest: the UNO Q's Linux-plus-MCU platform first, then
the ESP32-S3 boards, the ESP32-CAM, and finally the UNO family. Within a class, CPU, memory and peripherals
break the tie.

| | UNO Q | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|---|---|---|---|---|---|---|
| Microcontroller | STM32U585 | ESP32-S3 (NORA-W106) | ESP32-S3 | ESP32 Q | Renesas RA4M1 | ATmega4809 | ATmega328P |
| CPU | 32-bit Cortex-M33F | Dual-core 32-bit Xtensa LX7 | Dual-core 32-bit Xtensa LX7 | Dual-core 32-bit Xtensa LX6 | 32-bit Cortex-M4F | 8-bit megaAVR | 8-bit AVR |
| Clock | 160 MHz | 240 MHz | 240 MHz | 240 MHz | 48 MHz | 16 MHz | 16 MHz |
| Flash | 2 MB | 16 MB | 8 MB | 4 MB | 256 kB | 48 kB | 32 kB |
| SRAM | 786 kB | 512 kB + 8 MB PSRAM | 512 kB | 520 kB + 4 MB PSRAM | 32 kB | 6 kB | 2 kB |
| Logic level | 3.3 V | 3.3 V | 3.3 V | 3.3 V | 5 V | 5 V | 5 V |
| Radio | Wi-Fi/BLE on the Linux side | Wi-Fi/BLE | Wi-Fi/BLE | Wi-Fi/BLE | ESP32-S3 Wi-Fi/BLE | NINA-W102 Wi-Fi/BLE | None (WiFi Shield) |
| Extras | 13×8 LED matrix, Linux MPU | | Display, keyboard, speaker, battery | OV2640 camera, microSD socket | 12×8 LED matrix | Secure element, IMU | |
| Arduino core | `arduino: zephyr` | `arduino: esp32` | `m5stack: esp32` | `esp32: esp32` | `arduino: renesas_uno` | `arduino: megaavr` | `arduino: avr` |
| Juno board id | `arduino-uno-q` | `arduino-nano-esp32` | `m5stack-cardputer` | `ai-thinker-esp32-cam` | `arduino-uno-r4-wifi` | `arduino-uno-r3-wifi-rev2` | `arduino-uno-r3` |

The spaces after `:` in the Arduino core row are visual break points for this table; use the compact
core identifier (for example, `arduino:zephyr`) in build commands.

## API support

✓ supported · — not available on this board

| Juno API or feature | UNO Q | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| GPIO, delays, clock, `Serial` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `BigInteger`, `BigDecimal` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Double-precision `Math` functions | ✓ | ✓ | ✓ | — | ✓ | — | — |
| Threads, `StructuredTaskScope`, `ScopedValue`, locks | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| Wi-Fi | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | WiFi Shield |
| Wi-Fi access point | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| HTTP client | — | ✓ | ✓ | — | ✓ | ✓ | WiFi Shield |
| HTTPS client | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| HTTP server | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| HTTPS server | ✓ | ✓ | ✓ | — | — | — | — |
| HTTP response streaming (`HttpServer.beginStream`) | — | — | — | ✓ | — | — | — |
| Email over TLS (`Smtp.sendTls`, `Pop3Client`) | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| Email over STARTTLS (`Smtp.send`) | — | ✓ | — | — | ✓ | — | — |
| Bluetooth LE (LEGO Powered Up) | ✓ | ✓ | ✓ | ✓ | ✓ | — | — |
| USB HID mouse | — | ✓ | ✓ | — | ✓ | — | — |
| Onboard LED matrix | ✓ | — | — | — | ✓ | — | — |
| Onboard IMU (`ArduinoUno.getGyroscope()`, `ArduinoUno.getAccelerometer()`) | — | — | — | — | — | ✓ | — |
| Camera (`Esp32Cam.getCamera()`) | — | — | — | ✓ | — | — | — |
| `@Watchdog` | — | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| Cryptographic `UUID.randomUUID()` | ✓ | ✓ | ✓ | — | ✓ | ✓ | — |
| Display, keyboard, speaker, battery | — | — | ✓ | — | — | — | — |

The compiler checks this table for you: a program that uses an API its target board does not
support fails with a `CompileException` naming the board and the missing capability. Some cells
are compiled support that has not yet been run on every physical board; each board's page notes
what is confirmed on hardware.
