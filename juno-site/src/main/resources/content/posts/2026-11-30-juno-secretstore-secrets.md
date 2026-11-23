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
`read(name, buffer)`, which copies the value into a caller-owned byte array. A name that was never stored comes back as
`null`, so the program can say what is missing instead of failing later.

## Setting the secrets with the Maven plugin

The values reach the board through the `juno:secret` goal of the Juno Maven plugin, over USB. You never write them in
Java, in `pom.xml`, on the command line or on an SD card.

### 1. Find the board's port

Plug the board in over USB and list the connected boards:

```bash
./mvnw -f juno-examples/pom.xml juno:boards
```

Use the serial port it reports, such as `/dev/cu.usbmodem1101` on macOS. Always provision over USB serial: a network
address the list may also show needs credentials the goal cannot type.

### 2. Store each name

Run the goal once per name. It asks for the value at a hidden prompt, so nothing you type is echoed or logged:

```bash
./mvnw -f juno-examples/pom.xml juno:secret \
  -Djuno.board=arduino-nano-esp32 \
  -Djuno.port=/dev/cu.usbmodem1101 \
  -Djuno.secret.key=wifi.ssid
```

```text
Value for wifi.ssid (hidden):
[INFO] Uploading the secret provisioning program
[INFO] Stored wifi.ssid in the chip
```

Then repeat it with `-Djuno.secret.key=wifi.password`, and with any other name your program reads, such as an API
token. Storing a name again replaces its value.

Behind that one command, the goal:

1. Checks the name and the value before touching the board. If either is invalid it stops with "Nothing was uploaded or
   written".
2. Uploads the plugin's own provisioning program, `SecretProvisioner`, which replaces whatever was on the board.
3. Sends the value to that program over USB serial, as hex. The program writes it into the board's store under the name
   and answers `OK`.

### 3. Check what is stored

Pass the names to check. The goal reports each one as `written` or `missing`, never its value:

```bash
./mvnw -f juno-examples/pom.xml juno:secret \
  -Djuno.board=arduino-nano-esp32 \
  -Djuno.port=/dev/cu.usbmodem1101 \
  -Djuno.secret.list=wifi.ssid,wifi.password
```

```text
[INFO] STATE wifi.ssid written
[INFO] STATE wifi.password written
```

The store cannot enumerate what it holds, so you always name the keys you ask about.

### 4. Upload your program again

The provisioning program is still on the board, so flash your own program back. The secrets stay in the store:

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=com.example.MyProgram \
  -Djuno.board=arduino-nano-esp32 \
  -Djuno.port=/dev/cu.usbmodem1101
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32
```

Watch the serial output to confirm the program found its credentials. Provisioning is a one-time step per board: a new
firmware upload does not erase the store.

### Rules for names and values

- **Names** are 1 to 31 printable ASCII characters with no spaces, such as `wifi.ssid` or `api.token`. Every name is
  yours: the plugin and the store know no names of their own, so the provisioning command and the program only have to
  agree on the spelling.
- **Values** are printable ASCII. Each name shares a 63-byte record with its value, so a value can be up to
  `62 - name length` bytes: 53 bytes for `wifi.ssid`, for example.
- The store holds up to **16 names**.
- The goal needs a real terminal for the hidden prompt, so run it from a shell, not from a CI job or an IDE run without
  a console.
- Use exactly one of `-Djuno.secret.key`, `-Djuno.secret.list` and `-Djuno.secret.clear` per run.

To decommission a board, `-Djuno.secret.clear=true` empties every slot of the store, including names other programs
wrote. It asks you to type `CLEAR` before it uploads anything.

On the Raspberry Pi Pico 2 W, the plugin also installs Juno's TrustZone secure firmware while it builds the provisioning
program, so there is no extra step. The bundled provisioning program targets the Portenta H7, Nano ESP32, Pico 2 W,
Cardputer, ESP32-CAM and UNO R4 WiFi. On any other board, `-Djuno.main` can name your own program that speaks the
same protocol. The [SecretStore reference](../../api/storage/secure-element) has the full details.

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
