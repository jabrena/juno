---
title: "Storage"
description: "Storage: SD Card is for files on a microSD card, and SecretStore is for named secrets kept out of the program image."
layout: page
---

SD Card is for files on a microSD card, such as configuration and Wi-Fi credentials read at boot. SecretStore is for named secrets kept in the secure element or the ESP32's NVS, so credentials stay off the SD card and out of the firmware image.

| API | What it provides | Complete example |
|---|---|---|
| [SD Card](sdcard) | Loading configuration and credentials from a microSD card at boot, and reading and writing files. | [`SdFileOperations`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java) |
| [SecretStore](secure-element) | Reading named secrets from the Portenta H7's secure element or an ESP32 board's NVS, without placing credentials on the SD card or in the firmware image. | [`SecretWifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretWifiStatus.java) |
