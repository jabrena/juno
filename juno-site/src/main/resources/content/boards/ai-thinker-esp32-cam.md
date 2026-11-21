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
| `HttpServer` (HTTP) | ✓ | Runs on the station address. | [`AiThinkerEsp32CamStream`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamStream.java) |
| HTTPS server | ✓ | `HttpServer.beginTls()` uses the ESP32 mbedTLS server transport. | |
| HTTP response streaming | ✓ | `HttpServer.beginStream()` sends the camera's MJPEG response. | [`AiThinkerEsp32CamStream`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamStream.java) |
| Email over TLS (`Smtp.sendTls`, `Pop3Client`) | ✓ | Implicit TLS uses the ESP32 secure client. | |
| Email over STARTTLS (`Smtp.send`) | ✓ | STARTTLS upgrades through the ESP32 `ESP_SSLClient` wrapper. | |
| Bluetooth LE (LEGO Powered Up) | ✓ | Through `RemotePoweredUpHub` and ArduinoBLE. | [`PoweredUpMotorEncoders`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpMotorEncoders.java) |
| `SdCard` | ✓ | Built-in microSD socket through `MCU.getSdCard()`, using GPIO13 as chip select. | [`AiThinkerEsp32CamWifiFromSd`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/AiThinkerEsp32CamWifiFromSd.java) |
| OV2640 camera | ✓ | `MCU.getCamera()` captures JPEG frames into a caller-owned buffer; the native driver owns the camera frame buffer. `setFrameSize` switches between 160×120 and 800×600 at run time. | [`AiThinkerEsp32CamCapture`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/camera/AiThinkerEsp32CamCapture.java) |
| Explicit PSRAM buffers | ✓ | The static `Psram` API provides caller-owned byte buffers; PSRAM is outside the Java arena. | |
| Threads, `synchronized`, locks, `StructuredTaskScope`, `ScopedValue` | ✓ | | |
| USB HID mouse | — | The board has no native USB port; use a USB-serial adapter for flashing and serial output. | |
| `@Watchdog` | ✓ | ESP32 task watchdog; the timeout is rounded up to whole seconds. | |
| Cryptographic `UUID.randomUUID()` | ✓ | Uses the ESP32 hardware random-number generator. | |
| Double-precision `java.lang.Math` | ✓ | Uses the Xtensa toolchain's double-precision libm functions. | |

The board has no USB port, so flash it through a USB-serial adapter; the port is passed with
`-Djuno.port`.

## Camera

`Camera.begin()` starts the OV2640 at 160×120, small enough to stream from a few kilobytes of buffer.
`Camera.setFrameSize(size)` switches it at run time to `FRAME_SIZE_QQVGA` (160×120), `FRAME_SIZE_QVGA` (320×240),
`FRAME_SIZE_VGA` (640×480) or `FRAME_SIZE_SVGA` (800×600), and drops the frames already taken at the old size, so the next
`capture` is at the new one. A larger frame needs a larger buffer: a 640×480 JPEG takes tens of kilobytes, and
`capture` returns `-1` for a frame that does not fit rather than truncating it.

[`AiThinkerEsp32CamStream`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/AiThinkerEsp32CamStream.java)
serves three paths: `GET /` is a page with the live view, `GET /stream` is a 160×120 MJPEG stream, and `GET /capture`
takes one fresh 640×480 photo on every request. It reads `wifi.ssid` and `wifi.password` from the board's secret store
(provision them once with `juno:secret`), and its 48 KB frame buffer needs a larger arena:

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash -Djuno.board=ai-thinker-esp32-cam -Djuno.port=<PORT> \
  -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.AiThinkerEsp32CamStream -Djuno.Xmx=63k
```

`-Djuno.Xmx=64k` does not link: the arena would overflow the ESP32's static DRAM segment by a few hundred bytes. The
server answers one connection at a time, so a second viewer waits until the first one leaves.

### When the camera does not start

- `Camera probe failed`, `sccb-ng … fail`, or `assert failed: xQueueGenericSendFromISR … (pxQueue)` at boot mean the
  OV2640 is not answering. That is the ribbon cable or the module's seating, not the program: reseat the ribbon with
  the power off, then unplug the board for a few seconds before powering it again, since a reset alone does not power
  the sensor down. Juno clocks the sensor at 10 MHz rather than 20, which marginal modules probe more reliably.
- The stream example retries a camera start that fails and carries `@Watchdog`, so a start that hangs inside the
  driver reboots the board instead of leaving it silent.
- To flash, take the microSD card out: once initialized, the card holds GPIO2, a boot strapping pin, high and the chip
  will not enter download mode. Hold IO0 through the whole upload.
- Opening or closing the serial monitor resets the board through the adapter's DTR/RTS lines.
