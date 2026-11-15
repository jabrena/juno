---
title: "Secrets in a Secure Element"
description: "Storing Wi-Fi and API secrets in a secure element on the board, not on the SD card or in the firmware."
layout: page
---

Juno reads named secrets through the `SecretStore` interface, so no secret is written on the SD card, in the firmware
image or in build output. Two implementations sit behind that one interface:

| Implementation | Boards | Strength |
|---|---|---|
| `Se050C2` | Portenta H7 (onboard NXP SE050C2) | The value lives in a secure element; the processor asks it for the value. |
| `Esp32Nvs` | Nano ESP32, M5Stack Cardputer, AI Thinker ESP32-CAM | Weaker: the board's own flash. See the warning below. |

`SecretStoreFactory.open(slots)` picks the one the board has, and `SecretStoreFactory.isAvailable()` says whether there
is one. The board query is answered when the program is linked and the branches the target cannot take are dropped
with it, so only the chosen implementation reaches the image — and a core without the others' shim code never has to
supply it. The other boards have no secret store; see [Boards without a secret store](#boards-without-a-secret-store).

**The ESP32 store is not a secure element.** Those boards carry no crypto chip, so `Esp32Nvs` keeps each value in an
NVS entry in the board's own flash partition. That still takes the secret out of the firmware image, out of the build
output and off the SD card — a shared sketch or a copied card carries nothing — but a flash readout of the board
exposes every value unless flash encryption is enabled. Treat it as "not in the program", not as "not extractable
from the hardware".

**Status:** the SE050C2 path is confirmed on a Portenta H7 — writes, updates, per-name clears, a rewrite after a clear
and a 63-byte value round-trip, plus a reboot that joins Wi-Fi from the stored credentials. The ESP32 NVS path is
confirmed on a Nano ESP32.

## What is protected

- A copied SD card or firmware image contains no secret value.
- Code running on the board can read the store, and physical access to the board is trusted. This is a deliberate
  limit: a program on the board can read any name.
- A secret that is missing, unreadable or fails its checksum is reported as missing. No copy is kept in plaintext anywhere.

## Provisioning

Upload the provisioning program with `juno:secret`. It asks for the value at a hidden prompt, sends it to the board over USB
serial as hex, and never puts it on the command line or in the log:

```bash
./mvnw -f juno-examples/pom.xml juno:secret \
  -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProvisioner \
  -Djuno.board=arduino-portenta-h7 \
  -Djuno.port=/dev/cu.usbmodemXXXX \
  -Djuno.secret.key=wifi.ssid
```

- `-Djuno.secret.list=true` prints `STATE <name> written|missing` for every name, without values.
- `-Djuno.secret.clear=true` asks you to type `CLEAR` before uploading anything, then empties **every data slot on the
  store** — see [Clearing](#clearing) below.
- Only one of `-Djuno.secret.key`, `-Djuno.secret.list` and `-Djuno.secret.clear` may be used at a time.

Once provisioning is done, upload your own program again with `juno:flash`: the provisioning program replaces it on the
board.

## Reading secrets in a program

```java
SecretSlots slots = new SecretSlots().add("wifi.ssid", 8).add("wifi.password", 9);
SecretStore secrets = SecretStoreFactory.open(slots);

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
- `clearAll()` empties **every data slot on the store**, not only the names in the table it was called through. It
  decommissions the store: a credential another program provisioned under a name this table never listed is
  erased too. To empty just the names a program owns, loop over `clear(name)`.

`-Djuno.secret.clear=true` is the host side of `clearAll()`, which is why it asks for the word `CLEAR` first. On a
provisioned board the Wi-Fi credentials go with it and have to be provisioned again.

Two examples cover the two operations from the board itself, with dummy values and no host provisioning:
[`SecretStoreCheck`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretStoreCheck.java) checks writes, updates, per-name `clear` and the 63-byte maximum
without ever wiping the store, and [`PortentaSecretWipe`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/PortentaSecretWipe.java) writes two dummy names,
reads them back, calls `clearAll()` and checks that both are then missing.

[`SecretProbe`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretProbe.java) is the read-only one: it prints what each name holds, without writing
anything, which is the first thing to run on a board whose store has never answered.

## Wiring

- **Portenta H7:** the SE050C2 is on the board; no extra hardware and no I2C bus of your own.
- **Nano ESP32, M5Stack Cardputer, AI Thinker ESP32-CAM:** nothing to wire — the store is the board's own NVS
  partition.

## Boards without a secret store

The UNO R4 WiFi, UNO Q, UNO WiFi Rev2, UNO R3 and Raspberry Pi Pico 2 W have no secret store. The UNO WiFi Rev2 does carry
an ATECC608 chip, but that chip refuses every read and write until its zones are locked, which is permanent, so Juno
does not use it for secrets.

On those boards the connected examples read the same names through `ExampleSecrets` from `application.properties` on the
SD card, using the card of the stacked UNO shield (TFT Touch, LCD Keypad or Data Logger — all wire their socket to
chip select D10):

```properties
wifi.ssid=MyNetwork
wifi.password=MyPassword
```

**This is not a secret store.** The values are plain text on the card, so a copied card carries them. Each value is a
runtime `String`, so it must fit in 31 characters. The Pico 2 W takes no UNO shield, so the multi-board Wi-Fi examples
do not target it.
