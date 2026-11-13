---
title: "Boards"
description: "The boards Juno compiles for, side by side: hardware, memory, backend and which Juno APIs each one supports."
layout: page
---

Juno compiles the same Java program for eight boards through three backends: **AVR** for the 8-bit
UNO R3 and UNO WiFi Rev2, **Thumb-2** for the Arm Cortex-M UNO R4 WiFi, UNO Q and Raspberry Pi Pico 2 W, and
**Xtensa** for the Nano ESP32, M5Stack Cardputer and AI Thinker ESP32-CAM. Pick the target with the entry point's
`@Board` annotation or with `-Djuno.board=<id>`.

## Hardware at a glance

Boards are ordered by clock speed, then flash, SRAM and PSRAM, highest first. PSRAM is external RAM on the
module, slower than the SRAM inside the chip.

Juno never places ordinary Java objects, arrays or stacks in PSRAM. The Nano ESP32 and AI Thinker ESP32-CAM expose the
static `Psram` byte-buffer API; the camera board also keeps its driver-owned frame buffers there behind `Camera`.

| | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | UNO Q | Raspberry Pi Pico 2 W | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|---|---|---|---|---|---|---|---|
| Microcontroller | ESP32-S3 (NORA-W106) | ESP32-S3 | ESP32 Q | STM32U585 | RP2350A | Renesas RA4M1 | ATmega4809 | ATmega328P |
| CPU | Dual-core 32-bit Xtensa LX7 | Dual-core 32-bit Xtensa LX7 | Dual-core 32-bit Xtensa LX6 | 32-bit Cortex-M33F | Dual-core 32-bit Cortex-M33F | 32-bit Cortex-M4F | 8-bit megaAVR | 8-bit AVR |
| Clock | 240 MHz | 240 MHz | 240 MHz | 160 MHz | 150 MHz | 48 MHz | 16 MHz | 16 MHz |
| Flash | 16 MB | 8 MB | 4 MB | 2 MB | 4 MB | 256 kB | 48 kB | 32 kB |
| SRAM | 512 kB | 512 kB | 520 kB | 786 kB | 520 kB | 32 kB | 6 kB | 2 kB |
| PSRAM | 8 MB | — | 4 MB | — | — | — | — | — |
| Logic level | 3.3 V | 3.3 V | 3.3 V | 3.3 V | 3.3 V | 5 V | 5 V | 5 V |
| Radio | Wi-Fi/BLE | Wi-Fi/BLE | Wi-Fi/BLE | Wi-Fi/BLE on the Linux side | CYW43439 Wi-Fi/BLE | ESP32-S3 Wi-Fi/BLE | NINA-W102 Wi-Fi/BLE | None |
| Extras | | Display, keyboard, speaker, battery | OV2640 camera, microSD socket | 13×8 LED matrix, Linux MPU | Built-in LED on the radio chip | 12×8 LED matrix | Secure element, IMU | |
| Arduino core | `arduino: esp32` | `m5stack: esp32` | `esp32: esp32` | `arduino: zephyr` | `rp2040: rp2040` | `arduino: renesas_uno` | `arduino: megaavr` | `arduino: avr` |
| Juno board id | `arduino-nano-esp32` | `m5stack-cardputer` | `ai-thinker-esp32-cam` | `arduino-uno-q` | `raspberry-pi-pico-2-w` | `arduino-uno-r4-wifi` | `arduino-uno-r3-wifi-rev2` | `arduino-uno-r3` |

The spaces after `:` in the Arduino core row are visual break points for this table; use the compact
core identifier (for example, `arduino:zephyr`) in build commands.

## API support

✓ supported · — not available on this board

| Juno API or feature | Nano ESP32 | M5Stack Cardputer | AI Thinker ESP32-CAM | UNO Q | Raspberry Pi Pico 2 W | UNO R4 WiFi | UNO WiFi Rev2 | UNO R3 |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| GPIO and board-context pin resolution | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `BigInteger`, `BigDecimal` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Double-precision `Math` functions | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | — |
| Threads, `StructuredTaskScope`, `ScopedValue`, locks | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | — |
| Wi-Fi | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| Wi-Fi access point | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | — |
| HTTP client | ✓ | ✓ | — | — | ✓ | ✓ | ✓ | — |
| HTTPS client | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | — |
| HTTP server | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | — |
| HTTPS server | ✓ | ✓ | — | ✓ | ✓ | — | — | — |
| HTTP response streaming (`HttpServer.beginStream`) | — | — | ✓ | — | — | — | — | — |
| Email over TLS (`Smtp.sendTls`, `Pop3Client`) | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | — |
| Email over STARTTLS (`Smtp.send`) | ✓ | — | — | — | — | ✓ | — | — |
| Bluetooth LE (LEGO Powered Up) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | — |
| USB HID mouse | ✓ | ✓ | — | — | ✓ | ✓ | — | — |
| Onboard LED matrix | — | — | — | ✓ | — | ✓ | — | — |
| Onboard IMU (`MCU.getGyroscope()`, `MCU.getAccelerometer()`) | — | — | — | — | — | — | ✓ | — |
| Camera (`MCU.getCamera()`) | — | — | ✓ | — | — | — | — | — |
| `@Watchdog` | ✓ | ✓ | — | — | ✓ | ✓ | ✓ | ✓ |
| Cryptographic `UUID.randomUUID()` | ✓ | ✓ | — | ✓ | — | ✓ | ✓ | — |
| Display, keyboard, speaker, battery | — | ✓ | — | — | — | — | — | — |

The compiler checks this table for you: a program that uses an API its target board does not
support fails with a `CompileException` naming the board and the missing capability. Some cells
are compiled support that has not yet been run on every physical board; each board's page notes
what is confirmed on hardware.

Every board exposes `Gpio.digitalPin("D0".."D13")` and `Gpio.analogPin("A0".."A5")` through `MCU`.
The selected runtime translates those names to the board's pin mapping. `getInput()`, `getOutput()` and
`getInputPullup()` are also board-context methods. Invalid names raise `IllegalArgumentException`; the M5Stack Cardputer
additionally accepts its documented `G*` and `GPIO*` aliases. The Raspberry Pi Pico 2 W maps `A0`..`A2` to its ADC
pins GP26..GP28 (it has no `A3`..`A5`) and also accepts `GP*` and `GPIO*` names for its header pins. See [GPIO](../gpio) for examples.
