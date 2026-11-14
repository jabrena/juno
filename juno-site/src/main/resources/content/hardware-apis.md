---
title: "Hardware APIs"
description: "An overview of the hardware and network APIs Juno programs can use, organized by category."
layout: page
---

| Category | APIs | What it covers |
|---|---|---|
| [Telemetry](#telemetry) | [Serial](../serial), [Syslog](../syslog) | Observing a running program locally or over Wi-Fi. |
| [Storage](#storage) | [SD Card](../sdcard), [SecretStore](../secure-element) | Files and credentials kept outside the program image. |
| [Internet](#internet) | [Wifi](../wifi), [Time](../time), [HTTP client](../httpclient), [HTTP server](../httpserver), [Email](../email) | Network connectivity, services and Internet protocols. |
| [Utils](#utils) | [Delay](../delay), [MVC](../mvc) | Timing and application structure. |
| [Hardware](#hardware) | [Gpio](../gpio), [IMU](../arduino-uno-r3-wifi#read-the-onboard-imu) | Direct access to pins and board-mounted devices. |

## Telemetry

| API | What it provides | Complete example |
|---|---|---|
| [Serial](../serial) | Printing and reading over USB serial, the main way to see what a program is doing on a board. | [`SerialCounter`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialCounter.java) |
| [Syslog](../syslog) | Sending allocation-free RFC 5424 log datagrams over Wi-Fi to `juno:monitor`, so a board can be followed without USB. | [`NanoCarrierRcxRegulatedDifferentialDrive`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxRegulatedDifferentialDrive.java) |

## Storage

| API | What it provides | Complete example |
|---|---|---|
| [SD Card](../sdcard) | Loading configuration and credentials from a microSD card at boot, and reading and writing files. | [`SdFileOperations`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java) |
| [SecretStore](../secure-element) | Reading named secrets from an ATECC608 secure element without placing credentials on the SD card or in the firmware image. | [`SecretWifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretWifiStatus.java) |

## Internet

| API | What it provides | Complete example |
|---|---|---|
| [Wifi](../wifi) | Joining a Wi-Fi network on the boards that have a radio. | [`WifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatus.java) |
| [Time](../time) | Reading UTC with `LocalDateTime.now()` over SNTP, and measuring elapsed time with `Clock`. | [`SntpTime`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTime.java) |
| [HTTP client](../httpclient) | HTTP and HTTPS requests (`GET`, `POST`, `PATCH`, `DELETE` and `QUERY`) with caller-owned buffers, and reading JSON responses. | [`SingleHttpsGet`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/SingleHttpsGet.java) |
| [HTTP server](../httpserver) | Serving HTTP and HTTPS requests from a program, with routing by path and a certificate on the SD card. | [`HttpServerStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpServerStatus.java) |
| [Email](../email) | Sending mail over SMTP with `Smtp`, and reading a mailbox over POP3 with `Pop3Client`. | [`EmailClient`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/EmailClient.java) |

## Utils

| API | What it provides | Complete example |
|---|---|---|
| [Delay](../delay) | Pausing a program for a fixed time with `Delay.millis`, `Delay.micros` and `Delay.seconds`. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| [MVC](../mvc) | Organizing a multi-screen app with a model, views, a navigator and a router from `util.mvc`. | [`MailboxCardputer`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/mailbox/client/MailboxCardputer.java) |

## Hardware

| API | What it provides | Complete example |
|---|---|---|
| [Gpio](../gpio) | Driving and reading the board's pins, with `DigitalOutput` and the Blink example. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| [IMU](../arduino-uno-r3-wifi#read-the-onboard-imu) | Reading the UNO WiFi Rev2's onboard accelerometer and gyroscope with `MCU.getGyroscope()` and `MCU.getAccelerometer()`, over the `Spi` bus from `MCU.getSpi()`; see the UNO WiFi Rev2 page. | [`OnboardImuSerial`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/imu/OnboardImuSerial.java) |
