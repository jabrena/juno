---
title: "Hardware APIs"
description: "An overview of the hardware and network APIs Juno programs can use: delays, serial, syslog, secrets, SD card, Wi-Fi, time, HTTP, email and MVC."
layout: page
---

| API | What it provides | Complete example |
|---|---|---|
| [Delay](../delay) | Pausing a program for a fixed time with `Delay.millis`, `Delay.micros` and `Delay.seconds`. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| [Serial](../serial) | Printing and reading over USB serial, the main way to see what a program is doing on a board. | [`SerialCounter`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialCounter.java) |
| [Syslog](../syslog) | Sending allocation-free RFC 5424 log datagrams over Wi-Fi to `juno:monitor`, so a board can be followed without USB. | [`NanoCarrierRcxRegulatedDifferentialDrive`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxRegulatedDifferentialDrive.java) |
| [Gpio](../gpio) | Driving and reading the board's pins, with `DigitalOutput` and the Blink example. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| [IMU](../arduino-uno-r3-wifi#read-the-onboard-imu) | Reading the UNO WiFi Rev2's onboard accelerometer and gyroscope with `MCU.getGyroscope()` and `MCU.getAccelerometer()`, over the `Spi` bus from `MCU.getSpi()`; see the UNO WiFi Rev2 page. | [`OnboardImuSerial`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/imu/OnboardImuSerial.java) |
| [SecretStore](../secure-element) | Reading named credentials from an ATECC608 secure element without putting them on an SD card, in firmware or in build output. | [`SecretWifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretWifiStatus.java) |
| [SD Card](../sdcard) | Loading configuration and credentials from a microSD card at boot, and reading and writing files. | [`SdFileOperations`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java) |
| [Wifi](../wifi) | Joining a Wi-Fi network on the boards that have a radio. | [`WifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatus.java) |
| [Time](../time) | Reading UTC with `LocalDateTime.now()` over SNTP, and measuring elapsed time with `Clock`. | [`SntpTime`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTime.java) |
| [HTTP client](../httpclient) | HTTP and HTTPS requests (`GET`, `POST`, `PATCH`, `DELETE` and `QUERY`) with caller-owned buffers, and reading JSON responses. | [`SingleHttpsGet`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/SingleHttpsGet.java) |
| [HTTP server](../httpserver) | Serving HTTP and HTTPS requests from a program, with routing by path and a certificate on the SD card. | [`HttpServerStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpServerStatus.java) |
| [Email](../email) | Sending mail over SMTP with `Smtp`, and reading a mailbox over POP3 with `Pop3Client`. | [`EmailClient`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/EmailClient.java) |
| [MVC](../mvc) | Organizing a multi-screen app with a model, views, a navigator and a router from `util.mvc`. | [`MailboxCardputer`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/mailbox/client/MailboxCardputer.java) |

## SecretStore

`SecretStore` reads named values from an ATECC608 secure element on the board's I2C bus. Use it for Wi-Fi
credentials, API keys and other configuration that should not be stored on an SD card or embedded in the firmware.
The board remains the trust boundary: code running on the board can read the secrets, but a copied firmware image does
not contain their values.

Provision a name with the `juno:secret` goal. The value is requested through a hidden prompt and sent to the board over
USB serial; it is not placed on the command line or in the build log:

```bash
./mvnw -f juno-examples/pom.xml juno:secret \
  -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProvisioner \
  -Djuno.board=arduino-uno-r4-wifi \
  -Djuno.port=/dev/cu.usbmodemXXXX \
  -Djuno.secret.key=wifi.ssid
```

Read the value from a board program with a caller-owned slot map:

```java
SecretSlots slots = new SecretSlots()
        .add("wifi.ssid", 8)
        .add("wifi.password", 9);
SecretStore secrets = new Atecc608(MCU.getI2c(), slots).getSecretStore();

String ssid = secrets.getString("wifi.ssid");
String password = secrets.getString("wifi.password");
```

`getString` returns `null` when a value is missing. For zero-allocation reads, pass a caller-owned byte array to
`read(name, buffer)` instead. The provisioning program and the application must use the same name-to-slot mapping.
See [Secrets in a Secure Element](../secure-element) for supported names, limits, wiring, listing and clearing values.
