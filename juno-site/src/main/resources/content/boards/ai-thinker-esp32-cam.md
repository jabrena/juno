---
title: "AI Thinker ESP32-CAM"
description: "Board notes for the AI Thinker ESP32-CAM and what Juno supports on it: GPIO, Wi-Fi, TLS networking, microSD, Bluetooth LE and the OV2640 camera."
layout: page
---

The AI Thinker ESP32-CAM is an original ESP32 module with an OV2640 camera, a microSD socket and a
status LED on a small board. Its CPU is an Xtensa LX6, so Juno compiles for it with the same Xtensa
backend as the Nano ESP32.

The board has 4 MB of PSRAM. `Camera.begin()` leaves frame-buffer ownership inside the camera driver; application code
receives copied bytes through `Camera.capture()`. For other large, caller-owned byte payloads use the static `Psram` API.
PSRAM is not part of the Java arena and is not managed by the garbage collector.

<img src="../../images/boards/ai-thinker-esp32-cam.jpg" alt="AI Thinker ESP32-CAM" width="280">

## What Juno supports

| Feature | Status | Notes | Example |
|---|:---:|---|---|
| `Gpio`, `DigitalOutput` | ✓ | Resolve portable `D0`–`D13` and `A0`–`A5` names through the board context; board-specific aliases are available where documented, including the red status LED on GPIO33. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| `Delay`, `Serial` | ✓ | Serial output uses a USB-serial adapter because the board has no USB port. | |
| Classes, interfaces and inheritance | ✓ | | |
| `BigInteger`, `BigDecimal`, `MathContext` | ✓ | | |
| Wi-Fi station | ✓ | Through `MCU.getWifi()`; credentials can come from the onboard microSD card. | [`AiThinkerEsp32CamWifiFromSd`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/AiThinkerEsp32CamWifiFromSd.java) |
| Wi-Fi access point | ✓ | `MCU.getWifi().beginAP(ssid, password)` uses the ESP32 soft-AP. | |
| `java.net.http` (HTTP and HTTPS) | ✓ | Mozilla root certificates; an `SSLContext` can add its own. Certificate dates are checked once SNTP sets the clock. | [`ParallelHttpsGet`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/ParallelHttpsGet.java) |
| `HttpServer` (HTTP) | ✓ | Runs on the station address, through the JDK `com.sun.net.httpserver` API or Juno's own. | [`AiThinkerEsp32CamHttpServerSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamHttpServerSD.java), [`AiThinkerEsp32CamStream`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamStream.java) |
| HTTPS server | ✓ | The JDK `HttpsServer` with an `SSLContext` built from `server.crt` and `server.key` on the microSD card, over the ESP32 mbedTLS server transport. | [`AiThinkerEsp32CamHttpsServerSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamHttpsServerSD.java) |
| HTTP response streaming | ✓ | `sendResponseHeaders(200, 0)` sends the camera's MJPEG response until the viewer disconnects; build it with `-Djuno.Xmx=16k`. | [`AiThinkerEsp32CamStream`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamStream.java) |
| Email over TLS (`Smtp.sendTls`, `Pop3Client`) | ✓ | Implicit TLS uses the ESP32 secure client. | |
| Email over STARTTLS (`Smtp.send`) | ✓ | STARTTLS upgrades through the ESP32 `ESP_SSLClient` wrapper. | |
| Bluetooth LE (LEGO Powered Up) | ✓ | Through `RemotePoweredUpHub` and ArduinoBLE. | [`PoweredUpMotorEncoders`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpMotorEncoders.java) |
| `SdCard` | ✓ | Built-in microSD socket through `MCU.getSdCard()`, using GPIO13 as chip select. | [`AiThinkerEsp32CamWifiFromSd`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/AiThinkerEsp32CamWifiFromSd.java) |
| OV2640 camera | ✓ | `MCU.getCamera()` captures JPEG frames into a caller-owned buffer; the native driver owns the camera frame buffer. | [`AiThinkerEsp32CamCapture`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/camera/AiThinkerEsp32CamCapture.java) |
| Explicit PSRAM buffers | ✓ | The static `Psram` API provides caller-owned byte buffers; PSRAM is outside the Java arena. | |
| Threads, `synchronized`, locks, `StructuredTaskScope`, `ScopedValue` | ✓ | | |
| USB HID mouse | — | The board has no native USB port; use a USB-serial adapter for flashing and serial output. | |
| `@Watchdog` | ✓ | ESP32 task watchdog; the timeout is rounded up to whole seconds. | |
| Cryptographic `UUID.randomUUID()` | ✓ | Uses the ESP32 hardware random-number generator. | |
| Double-precision `java.lang.Math` | ✓ | Uses the Xtensa toolchain's double-precision libm functions. | |

The board has no USB port, so flash it through a USB-serial adapter; the port is passed with
`-Djuno.port`.

**Remove the microSD card before every upload.** The card holds GPIO2 high, which keeps the ESP32 out of download
mode, so `juno:flash` cannot connect while it is inserted. Upload with the card out, put it back, then reset the
board; opening `juno:monitor` resets it too, through the adapter's DTR/RTS lines.
