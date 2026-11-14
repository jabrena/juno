---
title: "AI Thinker ESP32-CAM"
description: "Board notes for the AI Thinker ESP32-CAM and what Juno supports on it: GPIO, Wi-Fi, microSD, Bluetooth LE and the OV2640 camera."
layout: page
---

The AI Thinker ESP32-CAM is an original ESP32 module with an OV2640 camera, a microSD socket and a
status LED on a small board. Its CPU is an Xtensa LX6, so Juno compiles for it with the same Xtensa
backend as the Nano ESP32.

The board has 4 MB of PSRAM. `Camera.begin()` leaves frame-buffer ownership inside the camera driver; application code
receives copied bytes through `Camera.capture()`. For other large, caller-owned byte payloads use the static `Psram` API.
PSRAM is not part of the Java arena and is not managed by the garbage collector.

<img src="../images/boards/ai-thinker-esp32-cam.jpg" alt="AI Thinker ESP32-CAM" width="280">

Juno's support on this board:

- **GPIO and serial** through `MCU.getGpio()`. Resolve portable `D0`–`D13` and `A0`–`A5` names through the board
  context; board-specific MCU GPIO aliases are available where documented, including the red status LED on GPIO33.
- **Wi-Fi** through `MCU.getWifi()`, with station mode, HTTP/HTTPS clients and an HTTP server.
- **HTTP response streaming** through `HttpServer.beginStream()`, used by the camera-stream example.
- **microSD** through `MCU.getSdCard()`, using the board's built-in socket on GPIO13.
- **Bluetooth LE** for `RemotePoweredUpHub`, used to drive LEGO Powered Up hubs.
- **Camera** through `MCU.getCamera()`, using the OV2640 and JPEG frames owned by the camera driver.
- **PSRAM** through the static `Psram` API for caller-owned byte buffers; camera frame buffers remain driver-owned.

The board has no USB port, so flash it through a USB-serial adapter; the port is passed with
`-Djuno.port`.
