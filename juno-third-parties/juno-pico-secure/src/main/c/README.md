# Juno secure firmware for the Pico 2 W

A small bare-metal program for the RP2350's Arm cores that runs in TrustZone's secure world and keeps Juno's
`SecretStore` slots out of reach of the program on the board. `build.sh` builds it with the Arm toolchain the
arduino-pico core installs and refreshes `src/main/resources/pico-secure/juno-pico-secure.bin`; commit both.

## Layout

| Region | Owner | Contents |
|---|---|---|
| `0x10000000`–`0x10002FFF` | secure | this firmware (the 12 KB slot arduino-pico reserves for its OTA stage) |
| `0x10002F00` | secure, non-secure callable | the gateway: `int juno_secure_call(int op, int a, int b, int c)` |
| `0x10003000`–`0x103FDFFF` | non-secure | the sketch, as arduino-pico links it |
| `0x103FE000`–`0x103FEFFF` | secure | the store: 16 slots × 64 bytes (length byte + up to 63 value bytes) |
| `0x103FF000`–`0x103FFFFF` | non-secure | arduino-pico's EEPROM sector |
| `0x20000000`–`0x2007DFFF`, scratch X/Y | non-secure | the sketch's RAM (`build.ram_length=504k`) |
| `0x2007E000`–`0x2007FFFF` | secure | this firmware's data and stack |

The same split covers the uncached flash alias at `0x14000000`; every alias above 4 MB, the XIP maintenance aliases,
the OTP fuses, the flash controller (QMI, XIP control and stream), the QSPI pins and pads, and the ROM's
`checked_flash_op` and `otp_access` stay secure. The DMA MPU keeps DMA out of the same secure ranges.

## Gateway

| `op` | Call | Returns |
|---|---|---|
| 0 | version | `0x4A554E4F` ("JUNO") |
| 1 | read(slot, buffer, capacity) | the value's length, or -1 when the slot is empty, the value does not fit, or `buffer` is not the caller's own memory |
| 2 | write(slot, value, length) | `length` (0 clears), or -1 for a bad slot, a length over 63, or a `value` the caller cannot read |
| 3 | clear(slot) | 0, or -1 |
| 4 | clearAll() | 0 |

Every pointer is checked with `TTA` against what the non-secure caller may itself access, so the gateway cannot be
pointed at secure memory. A write rewrites the sector only when its contents change.

## What the sketch needs

The non-secure sketch must skip the SDK start-up steps that belong to the secure world: the ROM state reset, the boot
locks, and the resets of JTAG, DMA, IO_QSPI and PADS_QSPI, whose reset-done bits read as 0 from the non-secure side.
Juno's Pico 2 W runtime does that when the program uses `SecretStore`.

## What it does not do

No OTP fuse is written, secure boot stays off and debug stays on, so the board can always be returned to stock
firmware through BOOTSEL. The fence protects the secrets from the program running on the board; anyone with the board
and a USB cable can still read or replace the whole flash. The values are not encrypted: without locking the chip, any
key would live in the same readable flash.

A TrustZone violation, or a fault in this firmware, reboots the board into BOOTSEL with a record in the watchdog
scratch registers. `JUNO_SECURE_FLAGS=-DJUNO_SECURE_DIAGNOSTICS ./build.sh` builds a development variant into `target/`
only, which instead reboots a hung or faulting start into an ordinary secure start of the sketch, so a probe sketch
can print where it stopped. That variant defeats the fence by design and is never shipped.
