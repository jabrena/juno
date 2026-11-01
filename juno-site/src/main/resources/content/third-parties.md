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
| Grove | Reads Seeed Studio's Grove sensors (temperature and humidity, light, sound, motion, gas, compass) with allocation-free drivers. | A Grove socket on the Grove Base Shield or the Nano Connector Carrier. | [Grove](../grove-system) |
| LEGO | Remote-controls LEGO Powered Up hubs, and the Mindstorms RCX and Scout bricks: motors, lights, sensors, sound and battery. | Bluetooth LE for Powered Up; infrared for the RCX and Scout. | [LEGO](../lego-ecosystem) |
