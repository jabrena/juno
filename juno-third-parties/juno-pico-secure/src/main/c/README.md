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
| 5 | flashErase(offset, count) | 0, or -1 unless the 4 KB-aligned range is the sketch's own flash |
| 6 | flashProgram(offset, data, count) | 0, or -1 unless the 256-byte-aligned range is the sketch's own flash and `data` the caller's |

Every pointer is checked with `TTA` against what the non-secure caller may itself access, so the gateway cannot be
pointed at secure memory. A write rewrites the sector only when its contents change.

## What the sketch needs

The non-secure sketch must skip the SDK start-up steps that belong to the secure world: the ROM state reset, the boot
locks, and the resets of JTAG, DMA, IO_QSPI and PADS_QSPI, whose reset-done bits read as 0 from the non-secure side.
It also cannot erase or program flash itself, so it is linked with `--wrap=flash_range_erase,--wrap=flash_range_program`
and those calls, BTstack's bond storage and the EEPROM library among them, go through gateway operations 5 and 6, which
accept only the sketch's own image area and the EEPROM sector. Juno's Pico 2 W runtime does all of this when the
program uses `SecretStore`.

## The second core

Core 1 waits in the boot ROM for a launch handshake that only the secure world's inter-core FIFO reaches, so this
firmware starts it itself, before the sketch: into a relay that gives core 1 the same SAU regions, interrupt and
coprocessor hand-over as core 0, and then waits, from secure RAM, on the non-secure FIFO. There it answers the boot
ROM's handshake (0, 0, 1, vector table, stack pointer, entry), checks that the three values are the sketch's own, and
enters the entry non-secure. The sketch's `multicore_launch_core1`, FreeRTOS SMP's included, therefore starts its
second core unchanged. Both cores may call the gateway; a lock in secure RAM runs one call at a time. A sketch that
resets core 1 itself (`multicore_reset_core1`, `rp2040.restartCore1()`) sends it back to the boot ROM, which the
non-secure FIFO cannot reach, so its relaunch never returns.

Three things differ for a non-secure image on two cores, and Juno's runtime handles them: the SIO doorbells and the
inter-core FIFO interrupt non-secure code on `SIO_IRQ_BELL_NS` and `SIO_IRQ_FIFO_NS`, which the SDK never enables
(the sketch wraps `irq_set_enabled` and `irq_set_priority`); arduino-pico builds FreeRTOS secure-only, seeding each task
with a return to the secure state (the sketch wraps `pxPortInitialiseStack`); and a store write idles the other core,
as the EEPROM library does, since the flash leaves XIP mode while it is rewritten.

## What it does not do

No OTP fuse is written, secure boot stays off and debug stays on, so the board can always be returned to stock
firmware through BOOTSEL. The fence protects the secrets from the program running on the board; anyone with the board
and a USB cable can still read or replace the whole flash. The values are not encrypted: without locking the chip, any
key would live in the same readable flash.

A crash in the sketch (HardFault, BusFault and NMI stay with this firmware), a TrustZone violation or a fault in this
firmware reboots the board into BOOTSEL with a record in the watchdog scratch registers. `JUNO_SECURE_FLAGS=-DJUNO_SECURE_FAULT_PC ./build.sh` builds, into `target/` only, a variant whose fault record
keeps the faulting PC in scratch 0 and the core, `EXC_RETURN`, SFSR and exception in scratch 1, the two registers a
BOOTSEL reboot leaves alone. `JUNO_SECURE_FLAGS=-DJUNO_SECURE_DIAGNOSTICS ./build.sh` builds a development variant into `target/`
only, which instead reboots a hung or faulting start into an ordinary secure start of the sketch, so a probe sketch
can print where it stopped. That variant defeats the fence by design and is never shipped.
