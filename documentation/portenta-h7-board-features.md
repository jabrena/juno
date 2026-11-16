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
- [x] Verify HTTP and HTTPS client transports on hardware.
- [x] Implement and verify email on hardware: `Smtp.send` STARTTLS, `Smtp.sendTls` on implicit TLS, and `Pop3Client`.
- [x] Verify SNTP time (`LocalDateTime.now()`) on hardware.
- [x] Verify the plain HTTP server transport on hardware.
- [x] Verify the HTTPS server transport on hardware, with the certificate and key sent over USB at boot.
- [x] Implement and compile-check Bluetooth LE.
- [x] Implement and compile-check USB HID mouse.
- [x] Implement and compile-check double math, watchdog and cryptographic UUID.
- [x] Implement and verify `SecretStore` on the onboard SE050C2 (not the ATECC608 this line first assumed:
  the Portenta carries an NXP SE050C2, and `MCU.getSecretStore()` picks the provider by board).
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
| Secure store | `SecretStoreCheck` | Write, update, per-name clear, rewrite after clear and the 63-byte maximum all pass |
| Secure store wipe | `PortentaSecretClearAllRead` | Write reports 15 bytes; read-before returns the same 15 bytes; `clearAll` returns 0; read-after returns -1 and counts as the expected failure |
| HTTP server | `PortentaHttpServer` | Listening on port 80 at the board's address: `GET /status` returns 200 with `ok`; an unknown path and `POST /status` return 404 |
| HTTPS server | `PortentaHttpsServer` | Listening on port 443 with a self-signed EC P-256 certificate. `GET /status` returns 200 with `ok` to a client that trusts that certificate, and an unknown path returns 404. A client without the certificate is refused. The certificate and key come over USB at boot with `scripts/portenta-https-send-pem.sh` and stay in RAM, so a reboot needs them sent again |
| HTTP and HTTPS client | `SingleHttpsGet`, `HttpsMethods` | `https://example.com/` returns 200; `httpbin.org` and `httpbun.com` return OK for GET, POST, DELETE, PATCH and QUERY. Plain `http://example.com/` returns 200 in the same run |
| SNTP time | `SntpTime` | `LocalDateTime.now()` prints UTC from `pool.ntp.org` every ten seconds |
| Email | `PortentaEmailSmoke` | `Smtp.send` STARTTLS on port 587 returns 0; `Smtp.sendTls` on implicit TLS, port 465, returns 0; `Pop3Client.messageCount` on port 995 returns the mailbox count. The account and host come from the secure store under `smtp.host`, `smtp.username` and `smtp.password`, provisioned with `juno:secret` |

The arena rises and falls between 18 and 60 KiB of the Portenta's 64 KiB default in the concurrency programs, so the
collector keeps up with the thread stacks the Mbed port allocates per round.

Three fixes came out of that verification:

- **Trusted roots.** The embedded Mozilla bundle includes one P-521 root. The Portenta's Mbed TLS build compiles out
  `SECP521R1`, so that root made the whole list fail to load and every HTTPS connection failed. The Portenta bundle now
  leaves it out (`CertificateBundle.pemWithoutCurve`). `JUNO_TLS_CA` replaces the bundle with a custom CA, as on the
  ESP32 cores.
- **Byte-count `endPacket()`.** Mbed's `MbedUDP::endPacket()` returns the number of bytes sent, where Arduino cores
  return 1. The SNTP fetch and the UDP send now accept any positive result, so SNTP works on the Portenta.
- **STARTTLS on Mbed.** `Smtp.send` could not link on this core, because its STARTTLS client was `ESP_SSLClient`,
  a BearSSL client. The Portenta now upgrades a TCP socket in place with a `TLSSocketWrapper` after the server accepts
  STARTTLS. The server certificate is checked against the same roots as HTTPS, and the host name must match.

The HTTPS server's private key is never part of the firmware or the repository. The board's TLS credentials live in
RAM only, so they are not kept across a reboot.

`SecretStore.clearAll()` is a whole-chip wipe: it erases the Wi-Fi and SMTP credentials too, so they have to be provisioned
again afterwards. `PortentaSecretClearAllRead` runs write, read, `clearAll` and a read that must fail, and it passed on
hardware. The same wipe is also covered by `PortentaSecretWipe`.
