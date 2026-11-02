---
title: "Boards"
description: "The boards Juno compiles for, side by side: hardware, memory, backend and which Juno APIs each one supports."
layout: page
---

Juno compiles the same Java program for six boards through three backends: **AVR** for the 8-bit
UNO R3 and UNO WiFi Rev2, **Thumb-2** for the Arm Cortex-M UNO R4 WiFi and UNO Q, and **Xtensa** for
the ESP32-S3 Nano ESP32 and M5Stack Cardputer. Pick the target with the entry point's `@Board`
annotation or with `-Djuno.board=<id>`.

## Hardware at a glance

Boards are ordered from the most capable to the smallest: by CPU (word size, then clock speed), then
by memory.

| | Nano ESP32 | M5Stack Cardputer | UNO Q | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|---|---|---|---|---|---|
| Microcontroller | ESP32-S3 (NORA-W106) | ESP32-S3 | STM32U585 | Renesas RA4M1 | ATmega4809 | ATmega328P |
| CPU | Dual-core 32-bit Xtensa LX7 | Dual-core 32-bit Xtensa LX7 | 32-bit Cortex-M33F | 32-bit Cortex-M4F | 8-bit megaAVR | 8-bit AVR |
| Clock | 240 MHz | 240 MHz | 160 MHz | 48 MHz | 16 MHz | 16 MHz |
| Flash | 16 MB | 8 MB | 2 MB | 256 kB | 48 kB | 32 kB |
| SRAM | 512 kB + 8 MB PSRAM | 512 kB | 786 kB | 32 kB | 6 kB | 2 kB |
| Logic level | 3.3 V | 3.3 V | 3.3 V | 5 V | 5 V | 5 V |
| Radio | Wi-Fi/BLE | Wi-Fi/BLE | Wi-Fi/BLE on the Linux side | ESP32-S3 Wi-Fi/BLE | NINA-W102 Wi-Fi/BLE | None (WiFi Shield) |
| Extras | | Display, keyboard, speaker, battery | 13×8 LED matrix, Linux MPU | 12×8 LED matrix | Secure element, IMU | |
| Arduino core | `arduino:esp32` | `m5stack:esp32` | `arduino:zephyr` | `arduino:renesas_uno` | `arduino:megaavr` | `arduino:avr` |
| Juno board id | `arduino-nano-esp32` | `m5stack-cardputer` | `arduino-uno-q` | `arduino-uno-r4-wifi` | `arduino-uno-r3-wifi-rev2` | `arduino-uno-r3` |

## API support

✓ supported · — not available on this board

| Juno API or feature | Nano ESP32 | M5Stack Cardputer | UNO Q | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| GPIO, delays, clock, `Serial` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `BigInteger`, `BigDecimal` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Double-precision `Math` functions | ✓ | ✓ | ✓ | ✓ | — | — |
| Threads, `StructuredTaskScope`, `ScopedValue`, locks | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Wi-Fi | ✓ | ✓ | ✓ | ✓ | ✓ | WiFi Shield |
| Wi-Fi access point | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTP client | ✓ | ✓ | — | ✓ | ✓ | WiFi Shield |
| HTTPS client | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTP server | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| HTTPS server | ✓ | ✓ | ✓ | — | — | — |
| Email over TLS (`Smtp.sendTls`, `Pop3Client`) | ✓ | — | ✓ | ✓ | ✓ | — |
| Email over STARTTLS (`Smtp.send`) | ✓ | — | — | ✓ | — | — |
| Bluetooth LE (LEGO Powered Up) | ✓ | ✓ | ✓ | ✓ | — | — |
| USB HID mouse | ✓ | ✓ | — | ✓ | — | — |
| Onboard LED matrix | — | — | ✓ | ✓ | — | — |
| `@Watchdog` | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| Cryptographic `UUID.randomUUID()` | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Display, keyboard, speaker, battery | — | ✓ | — | — | — | — |

The compiler checks this table for you: a program that uses an API its target board does not
support fails with a `CompileException` naming the board and the missing capability. Some cells
are compiled support that has not yet been run on every physical board; each board's page notes
what is confirmed on hardware.
