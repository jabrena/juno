---
title: "Secrets in a Secure Element"
description: "Storing Wi-Fi and API secrets in an ATECC608 on the board's I2C bus, not on the SD card or in the firmware."
layout: page
---

Juno can read named secrets from an ATECC608 secure element through the `SecretStore` interface, so no secret
is written on the SD card, in the firmware image or in build output. The chip is not built into most boards: the UNO WiFi
Rev2 has one, and the other I2C boards use an external ATECC608 breakout on their I2C bus.

**Status:** the software is written and tested with a simulated chip. It has not yet been confirmed on a board with a real
chip. Treat this page as a preview until the hardware steps are done.

## What is protected

- A copied SD card or firmware image contains no secret value.
- Code running on the board can read the chip, and physical access to the board is trusted. This is a deliberate limit: a
  program on the board can read any slot.
- A secret that is missing, unreadable or fails its checksum is reported as missing. No copy is kept in plaintext anywhere.

## Names and limits

| Name | Used for | Longest value |
|---|---|---|
| `wifi.ssid` | Wi-Fi network name | 31 characters |
| `wifi.password` | Wi-Fi password | 31 characters |
| `mailbox.apikey` | Mailbox API key | 63 bytes |
| `mailbox.q.ip` | UNO Q mailbox address | 31 characters |
| `smtp.host` | SMTP server | 31 characters |
| `smtp.username` | SMTP username | 31 characters |
| `smtp.password` | SMTP password | 31 characters |
| `supabase.api.host` | Supabase REST host | 31 characters |
| `supabase.api.key` | Supabase publishable key | 63 bytes |
| `supabase.api.url` | Supabase table endpoint | 63 bytes |

Values must be printable ASCII. The Wi-Fi limit is 31 characters because the example builds the SSID and password as runtime
`String`s. The plugin only enforces the 63-byte secure-element slot capacity; the board-side `SecretSlots` table is the
source of truth for valid names.

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
- `-Djuno.secret.clear=true` asks you to type `CLEAR` before uploading anything, then asks the board to empty every name
  in its own `SecretSlots` table.
- Only one of `-Djuno.secret.key`, `-Djuno.secret.list` and `-Djuno.secret.clear` may be used at a time.

Once provisioning is done, upload your own program again with `juno:flash`: the provisioning program replaces it on the
board.

The chip's data zone is not locked by this tool. Locking is a permanent step and is left to a separate, reviewed process.

## Reading secrets in a program

```java
SecretSlots slots = new SecretSlots().add("wifi.ssid", 8).add("wifi.password", 9);
SecretStore secrets = new Atecc608(MCU.getI2c(), slots).getSecretStore();

String ssid = secrets.getString("wifi.ssid");
String password = secrets.getString("wifi.password");
```

`SecretSlots` maps each name to a data slot. The program owns that mapping, and the provisioning program must use the same
slot numbers. `getString` returns `null` for a missing name. The `read(name, buffer)` form copies the bytes into your own
buffer and allocates nothing.

The complete example is `SecretWifiStatus` in `juno-examples`.

## Wiring

- **UNO WiFi Rev2:** the chip is on the board's I2C bus; no extra hardware.
- **UNO R4 WiFi, UNO Q, Nano ESP32, Pico 2 W:** an ATECC608 breakout on the board's I2C pins, at 3.3 V for the Q, Nano
  ESP32 and Pico 2 W, and at 5 V logic on the R4 WiFi with a level-shifting breakout.
- **M5Stack Cardputer:** the breakout on the Grove port (GPIO2 SDA, GPIO1 SCL). GPIO1 and GPIO2 are then not available as
  GPIO or analog pins.
- **AI Thinker ESP32-CAM:** the breakout shares the camera's SCCB pins (GPIO26 SDA, GPIO27 SCL). Juno rejects a program that
  uses both the camera and the I2C bus.

The UNO R3 is not supported.
