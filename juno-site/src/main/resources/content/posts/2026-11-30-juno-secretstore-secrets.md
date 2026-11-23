---
title: "Store credentials safely with Juno SecretStore"
description: "Keep Wi-Fi and API credentials out of Juno firmware with SecretStore, and understand when an SD card is only a fallback."
date: 2026-11-23
layout: post
link: /blog/2026-11-30-juno-secretstore-secrets/
tags: [security, storage, secrets]
blogVisible: true
---

Wi-Fi passwords and API credentials should not be committed to Java source, copied into generated firmware or placed in
an example repository. Juno's `SecretStore` API gives a program named access to credentials while keeping the values out
of the build inputs and firmware image.

## The normal path: SecretStore

The application asks for a name, not a physical slot:

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.security.SecretStore;

SecretStore secrets = MCU.getSecretStore();
String ssid = secrets.getString("wifi.ssid");
String password = secrets.getString("wifi.password");
if (ssid == null || password == null) {
    Serial.println("Wi-Fi credentials are missing");
    return;
}
MCU.getWifi().begin(ssid, password);
```

Read secrets once during startup. `getString()` allocates one Java `String`; the allocation-free alternative is
`read(name, buffer)`, which copies the value into a caller-owned byte array.

Provisioning is performed over USB by the Maven plugin, with the value entered at a hidden prompt:

```bash
./mvnw -f juno-examples/pom.xml juno:secret \
  -Djuno.board=arduino-portenta-h7 \
  -Djuno.port=<PORT> \
  -Djuno.secret.key=wifi.password
```

The program and provisioning command only need to agree on the name. The store supports up to 16 names, and each record
has a maximum size determined by the 63-byte record limit and the name length.

## What protection means on each board

The API is consistent, but the protection depends on the hardware:

- Portenta H7 uses its onboard NXP SE050C2 secure element.
- Nano ESP32, ESP32-CAM, Cardputer and UNO R4 WiFi use ESP32 NVS. This keeps values out of the firmware and SD card,
  but a flash readout can expose them unless the board's flash encryption is enabled.
- Pico 2 W uses Juno's TrustZone-fenced store. It is protected from ordinary program reads but is not encrypted and does
  not defeat physical flash replacement or debug access.
- UNO R3 uses plain EEPROM. It keeps credentials out of the firmware image, but any program on the board or an ISP
  programmer can read them.

SecretStore is therefore a strong improvement over source-code credentials, but the board and its physical access remain
the trust boundary. A program running on the board can read the names it needs.

## SD card as a fallback

Use an SD card only when the deployment needs file-based configuration, a board has no usable secret store, or an
operator must replace a larger configuration without rebuilding firmware. Juno can load a bounded
`application.properties` file:

```java
SdCard sd = new DataLoggerShield().getSdCard();
if (sd.begin()) {
    try (InputStream file = sd.open("application.properties")) {
        if (file != null) {
            Properties properties = new Properties();
            properties.load(file);
            String endpoint = properties.getProperty("api.endpoint");
        }
    } catch (IOException exception) {
        Serial.println("configuration could not be read");
    }
}
```

An SD card is not a secret store. The file is plain text: anyone who copies the card can read the credentials, and the
credentials may remain in backups, logs or a removed card. If an SD card must be used, restrict physical access, keep
the file small, avoid printing its values, and prefer a non-secret configuration file whose credentials still come from
`SecretStore`.

The fallback also has operational costs: the card must be present and mounted, reads can block, files can be removed or
corrupted, and the card's filesystem is another failure mode at startup. Load the file once, validate required keys and
fail closed when authentication material is missing.

## Choosing between them

Use `SecretStore` for Wi-Fi passwords, API tokens and email credentials. Use SD storage for larger or replaceable
configuration, logs and ordinary calibration data. If both are available, combine them: keep the endpoint and display
settings on the SD card, but keep the password or token in `SecretStore`.

Never solve a missing SecretStore by embedding a fallback password in Java source. A visible placeholder is safer than a
credential that becomes part of the firmware and every build artifact.
