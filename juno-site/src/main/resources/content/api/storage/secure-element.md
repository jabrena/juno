---
title: "Secrets in a Secure Element"
description: "Storing Wi-Fi and API secrets in a secure element on the board, not on the SD card or in the firmware."
layout: page
---

Juno reads named secrets through the `SecretStore` interface, so no secret is written on the SD card, in the firmware
image or in build output. Three implementations sit behind that one interface:

| Implementation | Boards | Strength |
|---|---|---|
| `Se050C2` | Portenta H7 (onboard NXP SE050C2) | The value lives in a secure element; the processor asks it for the value. |
| `Esp32Nvs` | Nano ESP32, M5Stack Cardputer, AI Thinker ESP32-CAM, UNO R4 WiFi | Weaker: an ESP32's own flash. See the warning below. |
| `Pico2WTrustZoneStore` | Raspberry Pi Pico 2 W | Fenced flash behind Juno's TrustZone secure firmware. See below. |
| `AvrEeprom` | UNO R3 | Weakest: plain text in the microcontroller's EEPROM. See the warning below. |

`MCU.getSecretStore()` returns the one the board has, the same way `MCU.getWifi()` returns its Wi-Fi; every supported
board has one. The board query is answered when the program is linked and the branches the target cannot take are dropped
with it, so only the chosen implementation reaches the image — and a core without the others' shim code never has to
supply it.

**The ESP32 store is not a secure element.** Those boards carry no crypto chip, so `Esp32Nvs` keeps each value in an
NVS entry in the board's own flash partition. That still takes the secret out of the firmware image, out of the build
output and off the SD card — a shared sketch or a copied card carries nothing — but a flash readout of the board
exposes every value unless flash encryption is enabled. Treat it as "not in the program", not as "not extractable
from the hardware". The UNO R4 WiFi uses the same store in its ESP32-S3 coprocessor, reached through the core's
`Preferences` library over the modem link: it needs coprocessor firmware 0.4.1 or later, each call is a round trip
to the coprocessor, so `clearAll()` takes several seconds, and the program on the main MCU can still read every value
through that link.

**The Pico 2 W store is fenced, not locked.** The RP2350 has no crypto chip either, so Juno ships its own small
TrustZone secure firmware ([`juno-pico-secure`](https://github.com/jabrena/juno/tree/main/juno-third-parties/juno-pico-secure/src/main/c)):
it takes the 12 KB boot slot arduino-pico reserves for its OTA stage, keeps the 16 slots in a flash sector the CPU
fences off, and starts the program in the non-secure world, which reaches the slots only through one gateway.
`juno:flash` installs it with any program that uses the store; nothing has to be flashed first. Code on the board,
libraries included, cannot read the sector directly — the read faults — but no fuse is written and debug stays on,
so anyone with the board and a USB cable can still read or replace the whole flash, and the values are not encrypted.
Such a program still runs its virtual threads on both cores: the firmware starts the second core into a small relay
that fences it the same way and hands it to the program non-secure. Its flash writes (BTstack's bonds, the EEPROM
library) go through the firmware, so Wi-Fi and Bluetooth LE keep working. A crash reboots the board into BOOTSEL.

**Status:** the SE050C2 path is confirmed on a Portenta H7 — writes, updates, per-name clears, a rewrite after a clear
and a 63-byte value round-trip, plus a reboot that joins Wi-Fi from the stored credentials. The ESP32 NVS path is
confirmed on a Nano ESP32, and its UNO R4 WiFi variant on an R4 with coprocessor firmware 0.4.1
(`SecretStoreCheck` and `SecretClearAllRead`). The Pico 2 W TrustZone path is confirmed on a Pico 2 W: `SecretStoreCheck` and
`SecretClearAllRead` pass, values survive a reboot and a reflash, Wi-Fi and a BLE scan run beside it, and a direct
read of the secure flash raises a SecureFault. Those checks ran before secrets were looked up by name (see
[How names are stored](#how-names-are-stored)); the name-lookup format has not been flashed yet.

## What is protected

- A copied SD card or firmware image contains no secret value.
- Code running on the board can read the store, and physical access to the board is trusted. This is a deliberate
  limit: a program on the board can read any name.
- A secret that is missing, unreadable or fails its checksum is reported as missing. No copy is kept in plaintext anywhere.

## Provisioning

Provision with `juno:secret`. The goal uploads the plugin's own provisioning program (`SecretProvisioner`, shipped in
`juno-maven-plugin`), so it works from any project without a program of its own; `-Djuno.main` can still name another
program that speaks the same protocol. It asks for the value at a hidden prompt, sends it to the board over USB serial
as hex, and never puts it on the command line or in the log:

```bash
./mvnw -f juno-examples/pom.xml juno:secret \
  -Djuno.board=arduino-portenta-h7 \
  -Djuno.port=/dev/cu.usbmodemXXXX \
  -Djuno.secret.key=wifi.ssid
```

Every name is yours: the program knows no names of its own.

- `-Djuno.secret.list=wifi.ssid,wifi.password` prints `STATE <name> written|missing` for each name given, without
  values. The store cannot enumerate what it holds, so the names to check are always named.
- `-Djuno.secret.clear=true` asks you to type `CLEAR` before uploading anything, then empties **every data slot on the
  store** — see [Clearing](#clearing) below.
- Only one of `-Djuno.secret.key`, `-Djuno.secret.list` and `-Djuno.secret.clear` may be used at a time.

Once provisioning is done, upload your own program again with `juno:flash`: the provisioning program replaces it on the
board.

## Reading secrets in a program

```java
SecretStore secrets = MCU.getSecretStore();

String ssid = secrets.getString("wifi.ssid");
String password = secrets.getString("wifi.password");
```

A secret is asked for by its name alone: the program never picks a slot, and the provisioning program and the reading
program only have to agree on the name. `getString` returns `null` for a missing name. The `read(name, buffer)` form copies the bytes into your own
buffer and allocates nothing.

The complete example is [`SecretWifiStatus`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretWifiStatus.java) in `juno-examples`.

## How names are stored

Every store has 16 slots, and each slot holds one record of at most 63 bytes: a length byte, the name, then the value.

- `read` and `getString` look through the slots in order and return the first record with that name.
- `write` replaces the record that has the name, in place, or takes the first empty slot for a new name. It fails
  once all 16 slots hold other names.
- A name is 1 to 31 printable, non-space ASCII characters, and the value can be up to `62 - name.length()` bytes
  (`SecretStore.RECORD_BYTES` is the 63).
- A lookup reads up to 16 slots, so read secrets once at startup. On the UNO R4 WiFi each slot read is a round trip to
  the coprocessor.

A slot written before names were stored holds a raw value with no name, so it is never matched and never overwritten.
A board provisioned that way has to be provisioned again, preferably after `-Djuno.secret.clear=true`.

## Clearing

There are two different operations, and the difference matters:

- `clear(name)` empties one name. The other names are untouched.
- `clearAll()` empties **every data slot on the store**, not only the names the calling program uses. It
  decommissions the store: a credential another program provisioned is erased too. To empty just the names a program owns, loop over `clear(name)`.

`-Djuno.secret.clear=true` is the host side of `clearAll()`, which is why it asks for the word `CLEAR` first. On a
provisioned board the Wi-Fi credentials go with it and have to be provisioned again.

Two examples cover the two operations from the board itself, with dummy values and no host provisioning:
[`SecretStoreCheck`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretStoreCheck.java) checks writes, updates, per-name `clear` and the longest value
without ever wiping the store, and [`PortentaSecretWipe`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/PortentaSecretWipe.java) writes two dummy names,
reads them back, calls `clearAll()` and checks that both are then missing.
[`SecretClearAllRead`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretClearAllRead.java)
runs write, read, `clearAll()` and a read that must fail against an ESP32 board's NVS store or the Pico 2 W's TrustZone
store; it passed on a Nano ESP32, an AI Thinker ESP32-CAM, an M5Stack Cardputer ADV, a Pico 2 W and an UNO R4 WiFi.

[`SecretProbe`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/security/SecretProbe.java) is the read-only one: it prints what each name holds, without writing
anything, which is the first thing to run on a board whose store has never answered.

## Wiring

- **Portenta H7:** the SE050C2 is on the board; no extra hardware and no I2C bus of your own.
- **Nano ESP32, M5Stack Cardputer, AI Thinker ESP32-CAM:** nothing to wire — the store is the board's own NVS
  partition.
- **UNO R4 WiFi:** nothing to wire — the store is the onboard ESP32-S3's NVS.
- **Raspberry Pi Pico 2 W:** nothing to wire or flash separately — `juno:flash` installs the secure firmware.
- **UNO R3:** nothing to wire — the store is the microcontroller's own EEPROM.

**The UNO R3's store is plain EEPROM.** The UNO R3 (ATmega328P) carries no crypto chip, so `AvrEeprom` keeps each name and value in plain text in the microcontroller's own EEPROM, split into 64-byte slots: 16 names in its 1 KB. The secret stays out of the firmware image, the build output and the SD card, and an upload keeps it, since the R3 is flashed through its bootloader, which never erases the EEPROM. But any program flashed to the board can read it, and so can anyone with an ISP programmer. Clearing a name erases its whole slot. On the UNO R3, `SecretProvisioner` does not fit in the 2 KB of RAM, so `juno:secret` cannot provision it yet.
