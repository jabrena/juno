---
title: "Third parties"
description: "The third-party hardware Juno programs interact with: Arduino shields, Grove modules and LEGO bricks."
layout: page
---

A Juno program runs on the board, but much of what it does reaches hardware made by someone else: a display or an SD
card on a shield, a sensor plugged into a Grove socket, a LEGO motor across a Bluetooth or infrared link. Juno talks to
all of it from plain Java. Each integration is a small class in `juno-api` with no Arduino library behind it, or a thin
compiler intrinsic over the board's own library, so the same program style works whatever is attached.

| Third party | What Juno does with it | How it connects | Page |
|---|---|---|---|
| Arduino shields | Drives displays, touch panels, buttons, SD cards, motion sensors and Wi-Fi on boards that plug onto the UNO or Nano headers. | The board's headers: GPIO, SPI, I2C and analog pins. | [Arduino Shields](../arduino-shields) |
| Grove | Reads Seeed Studio's Grove sensors (temperature and humidity, light, sound, motion, gas) with allocation-free drivers. | A Grove socket on the Grove Base Shield or the Nano Connector Carrier. | [Grove](../grove-system) |
| LEGO | Remote-controls LEGO Powered Up hubs, and the Mindstorms RCX and Scout bricks: motors, lights, sensors, sound and battery. | Bluetooth LE for Powered Up; infrared for the RCX and Scout. | [LEGO](../lego-ecosystem) |

## Bundled native libraries

Some features need a native library that Juno does not write. `juno:install-deps` installs such a library from the Arduino
Library Manager at an exact, pinned version, never the latest release. The TensorFlow Lite Micro port the ESP32-CAM
cat detector uses is pinned to `Chirale_TensorFLowLite@2.0.0`; it compiles on the `esp32:esp32` 3.3.12 core, which is
the version this release was checked against. The CI image installs `esp32:esp32` without a version pin.

Bundled C++ source follows the same rule as the rest of the runtime: it is written into a program's generated sketch only
when the program uses the feature that needs it, so a program that does not use the feature gets the same sketch, shim and
report as before.
