---
title: "AI Thinker ESP32-CAM"
description: "Board notes for the AI Thinker ESP32-CAM and what Juno supports on it: GPIO, Wi-Fi, microSD, Bluetooth LE and the OV2640 camera."
layout: page
---

The AI Thinker ESP32-CAM is an original ESP32 module with an OV2640 camera, a microSD socket and a
status LED on a small board. Its CPU is an Xtensa LX6, so Juno compiles for it with the same Xtensa
backend as the Nano ESP32.

<img src="../images/boards/ai-thinker-esp32-cam.jpg" alt="AI Thinker ESP32-CAM" width="280">

Juno's support on this board:

- **GPIO and serial** through `Esp32Cam.getGpio()`, including the red status LED on GPIO33.
- **Wi-Fi** through `Esp32Cam.getWifi()`, station mode with plain HTTP.
- **microSD** through `Esp32Cam.getSdCard()`, using the board's built-in socket on GPIO13.
- **Bluetooth LE** for `RemotePoweredUpHub`, used to drive LEGO Powered Up hubs.

The board has no USB port, so flash it through a USB-serial adapter; the port is passed with
`-Djuno.port`.
