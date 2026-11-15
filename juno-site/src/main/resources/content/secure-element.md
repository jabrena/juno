---
title: "Secrets in a Secure Element"
description: "Storing Wi-Fi and API secrets in a secure element on the board, not on the SD card or in the firmware."
layout: page
---

Juno can read named secrets from a secure element through the `SecretStore` interface, so no secret is written on the
SD card, in the firmware image or in build output. Two chips are supported behind the same interface: the Portenta H7's
onboard NXP SE050C2, and an ATECC608 on an I2C bus — built into the UNO WiFi Rev2, an external breakout on the other
I2C boards. `SecretStoreFactory.open(MCU.getBoardModel(), MCU.getI2c(), slots)` picks the provider for the board, and
the compiler folds that choice, so the unused driver is left out of the image.

**Status:** the SE050C2 path is confirmed on a Portenta H7: writes, updates, per-name clears, a rewrite after a clear
and a 63-byte value round-trip, plus a reboot that joins Wi-Fi from the stored credentials. The ATECC608 path is still
tested only against a simulated chip and has not been confirmed on a board with a real one.

## What is protected

- A copied SD card or firmware image contains no secret value.
- Code running on the board can read the chip, and physical access to the board is trusted. This is a deliberate limit: a
  program on the board can read any slot.
- A secret that is missing, unreadable or fails its checksum is reported as missing. No copy is kept in plaintext anywhere.

## Provisioning

Upload the provisioning program with `juno:secret`. It asks for the value at a hidden prompt, sends it to the board over USB
serial as hex, and never puts it on the command line or in the log:

```bash
./mvnw -f juno-examples/pom.xml juno:secret \
  -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProvisioner \
  -Djuno.board=arduino-uno-r4-wifi \
  -Djuno.port=/dev/cu.usbmodemXXXX \
  -Djuno.secret.key=wifi.ssid
```

- `-Djuno.secret.list=true` prints `STATE <name> written|missing` for every name, without values.
- `-Djuno.secret.clear=true` asks you to type `CLEAR` before uploading anything, then empties **every data slot on the
  chip** — see [Clearing](#clearing) below.
- Only one of `-Djuno.secret.key`, `-Djuno.secret.list` and `-Djuno.secret.clear` may be used at a time.

Once provisioning is done, upload your own program again with `juno:flash`: the provisioning program replaces it on the
board.

The chip's data zone is not locked by this tool. Locking is a permanent step and is left to a separate, reviewed process.

## Reading secrets in a program

```java
SecretSlots slots = new SecretSlots().add("wifi.ssid", 8).add("wifi.password", 9);
SecretStore secrets = SecretStoreFactory.open(MCU.getBoardModel(), MCU.getI2c(), slots);

String ssid = secrets.getString("wifi.ssid");
String password = secrets.getString("wifi.password");
```

`SecretSlots` maps each name to a data slot. The program owns that mapping, and the provisioning program must use the same
slot numbers. `getString` returns `null` for a missing name. The `read(name, buffer)` form copies the bytes into your own
buffer and allocates nothing.

The complete example is [`SecretWifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretWifiStatus.java) in `juno-examples`.

## Clearing

There are two different operations, and the difference matters:

- `clear(name)` empties one name. The other names, and any slot this program's table does not list, are untouched.
- `clearAll()` empties **every data slot on the chip**, not only the names in the table it was called through. It
  decommissions the secure element: a credential another program provisioned under a name this table never listed is
  erased too. To empty just the names a program owns, loop over `clear(name)`.

`-Djuno.secret.clear=true` is the host side of `clearAll()`, which is why it asks for the word `CLEAR` first. On a
provisioned board the Wi-Fi credentials go with it and have to be provisioned again.

Two examples cover the two operations from the board itself, with dummy values and no host provisioning:
[`PortentaSecretStore`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/PortentaSecretStore.java) checks writes, updates, per-name `clear` and the 63-byte
maximum without ever wiping the chip, and [`PortentaSecretWipe`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/PortentaSecretWipe.java) writes two dummy
names, reads them back, calls `clearAll()` and checks that both are then missing.

## Wiring

- **Portenta H7:** the SE050C2 is on the board; no extra hardware and no I2C bus of your own.
- **UNO WiFi Rev2:** the chip is on the board's I2C bus; no extra hardware.
- **UNO R4 WiFi, UNO Q, Nano ESP32, Pico 2 W:** an ATECC608 breakout on the board's I2C pins, at 3.3 V for the Q, Nano
  ESP32 and Pico 2 W, and at 5 V logic on the R4 WiFi with a level-shifting breakout.
- **M5Stack Cardputer:** the breakout on the Grove port (GPIO2 SDA, GPIO1 SCL). GPIO1 and GPIO2 are then not available as
  GPIO or analog pins.
- **AI Thinker ESP32-CAM:** the breakout shares the camera's SCCB pins (GPIO26 SDA, GPIO27 SCL). Juno rejects a program that
  uses both the camera and the I2C bus.

The UNO R3 is not supported.
