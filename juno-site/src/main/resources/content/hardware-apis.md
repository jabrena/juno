---
title: "Hardware APIs"
description: "An overview of the hardware and network APIs Juno programs can use: delays, serial, SD card, Wi-Fi, time, HTTP, email and MVC."
layout: page
---

- **[Delay](../delay):** pausing a program for a fixed time with `Delay.millis`, `Delay.micros` and `Delay.seconds`.
- **[Serial](../serial):** printing and reading over USB serial, the main way to see what a program is doing on a board.
- **[Gpio](../gpio):** driving and reading the board's pins, with `DigitalOutput` and the Blink example.
- **[IMU](../arduino-uno-r3-wifi#read-the-onboard-imu):** reading the UNO WiFi Rev2's onboard accelerometer and gyroscope with `ArduinoUno.getGyroscope()` and `ArduinoUno.getAccelerometer()`, over the `Spi` bus from `ArduinoUno.getSpi()`; see the UNO WiFi Rev2 page.
- **[SD Card](../sdcard):** loading configuration and credentials from a microSD card at boot, and reading and writing files.
- **[Wifi](../wifi):** joining a Wi-Fi network on the boards that have a radio, or on a UNO R3 with the Wi-Fi shield.
- **[Time](../time):** reading UTC with `LocalDateTime.now()` over SNTP, and measuring elapsed time with `Clock`.
- **[HTTP client](../httpclient):** HTTP and HTTPS requests (`GET`, `POST`, `PATCH`, `DELETE` and `QUERY`) with caller-owned buffers, and reading JSON responses.
- **[HTTP server](../httpserver):** serving HTTP and HTTPS requests from a program, with routing by path and a certificate on the SD card.
- **[Email](../email):** sending mail over SMTP with `Smtp`, and reading a mailbox over POP3 with `Pop3Client`.
- **[MVC](../mvc):** organizing a multi-screen app with a model, views, a navigator and a router from `util.mvc`.
