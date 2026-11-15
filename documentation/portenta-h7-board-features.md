# Portenta H7 feature implementation

The published [board feature matrix](https://jabrena.github.io/juno/board-features/) listed the Portenta H7 as
supporting only GPIO, Java objects and big numbers. The implementation sequence below records the missing Juno
features and their verification status.

## Missing features at the start

1. Wi-Fi station mode and access-point mode.
2. HTTP client, HTTPS client, HTTP server and HTTPS server.
3. TLS email and SMTP STARTTLS.
4. Bluetooth LE.
5. USB HID mouse.
6. Double-precision `Math` functions.
7. Hardware watchdog.
8. Cryptographic `UUID.randomUUID()`.
9. `SecretStore` access through the Portenta's onboard secure element.
10. Hardware verification of threads, structured concurrency, scoped values and locks.

Ethernet is intentionally not included: the current Juno API has no Ethernet capability or transport to implement.

## Sequential checklist

- [x] Declare and implement the Portenta Wi-Fi transports.
- [x] Verify station mode with the existing `SecretStore` example, flash over USB, and inspect serial output.
- [x] Implement and compile-check access-point mode.
- [x] Implement and compile-check HTTP/HTTPS transports.
- [ ] Verify HTTP/HTTPS and email transports on hardware.
- [x] Implement and compile-check Bluetooth LE.
- [x] Implement and compile-check USB HID mouse.
- [x] Implement and compile-check double math, watchdog and cryptographic UUID.
- [x] Implement and verify `SecretStore` on the onboard SE050C2 (not the ATECC608 this line first assumed:
  the Portenta carries an NXP SE050C2, and `SecretStoreFactory` picks the provider by board).
- [x] Implement the Mbed RTOS thread port.
- [x] Verify threads, locks, structured concurrency and scoped values on hardware.

Credentials are never stored in this worktree or printed by the verification programs.

## What hardware confirmed

Flashed to a Portenta H7 over USB and read back with `juno:monitor` on 2026-11-15.

| Area | Program | Result |
|---|---|---|
| Threads | `PortentaThreads` | 360 rounds of three virtual threads started and joined; 430 primes every round, 0 mismatches, 0 alive after join |
| Locks | `PortentaLocks` | 256 rounds; `synchronized` total exactly 8000 and `ReentrantLock` total exactly 8001 every round, 0 failures |
| Structured concurrency | `PortentaStructuredConcurrency` | 105 rounds of three scopes; a failing subtask cancelled a 200 ms sleeper in ~21 ms, 0 failures |
| Scoped values | `PortentaScopedValues` | 19k rounds; the binding reached every subtask, nested rebinding restored the outer value, 0 failures |
| Wi-Fi from the secure element | `SecretWifiStatus` | Joined with the SSID and password read from the SE050C2 after a reboot: `WiFi status: 3` |
| Secure store | `PortentaSecretStore` | Write, update, per-name clear, rewrite after clear and the 63-byte maximum all pass |

The arena rises and falls between 18 and 60 KiB of the Portenta's 64 KiB default in the concurrency programs, so the
collector keeps up with the thread stacks the Mbed port allocates per round.

Still unverified on hardware: HTTP/HTTPS and email transports, and `SecretStore.clearAll()`, whose whole-chip wipe
erases the provisioned Wi-Fi credentials — `PortentaSecretWipe` exists for that run, and the credentials have to be
provisioned again afterwards.
