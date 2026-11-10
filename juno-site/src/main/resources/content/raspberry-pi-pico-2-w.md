---
title: "Raspberry Pi Pico 2 W"
description: "Board notes for the Raspberry Pi Pico 2 W and what Juno supports on it: GPIO, Wi-Fi and the Grove Shield for Pi Pico."
layout: page
---

The Raspberry Pi Pico 2 W pairs the RP2350's Cortex-M33 with an Infineon CYW43439 Wi-Fi radio. The UNO Q is a
Cortex-M33 too, so Juno compiles for the Pico with the same Thumb-2 backend, linking against Earle Philhower's
[arduino-pico](https://github.com/earlephilhower/arduino-pico) core (`rp2040:rp2040:rpipico2w`).

<img src="../images/boards/raspberry-pico2-wifi.png" alt="Raspberry Pi Pico 2 W" width="280">

Juno's support on this board:

- **GPIO and serial** through `RaspberryPico2.getGpio()`. `A0`–`A2` are the ADC pins GP26–GP28, and `GP*`/`GPIO*`
  names reach the header pins. `getBuiltinLed()` is the LED wired to the radio chip, which the core drives as pin 64.
- **Wi-Fi** through `RaspberryPico2.getWifi()`: station and access-point modes, with UDP, TCP sockets and SNTP.
- **[Grove Shield for Pi Pico](../arduino-shields#grove-shield-for-pi-pico)** through `GrovePicoShield`, a pin map of
  its D16, D18, D20, A0–A2 and I2C sockets.
- **`java.math`** (`BigInteger`, `BigDecimal`).

HTTP, TLS, Bluetooth LE, `@Watchdog` and threads are not wired yet; a program that uses them is rejected at compile
time with the board's name.

Install the core with `juno:install-deps`. To flash a new board, hold BOOTSEL while plugging it in; `juno:upload`
finds its `UF2_Board` drive by itself, and later uploads reset the board through its USB serial port.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.RaspberryPico2Blink \
  -Djuno.board=raspberry-pi-pico-2-w
```

`RaspberryPico2Blink` is confirmed on hardware; the Wi-Fi and Grove shield examples are compiled but not yet run on a
board.
