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
10. Threads, structured concurrency, scoped values and locks.

Ethernet is intentionally not included: the current Juno API has no Ethernet capability or transport to implement.

## Sequential checklist

- [x] Declare and implement the Portenta Wi-Fi transports.
- [ ] Verify station mode with the existing `SecretStore` example, flash over USB, and inspect serial output.
- [x] Implement and compile-check access-point mode.
- [x] Implement and compile-check HTTP/HTTPS transports.
- [ ] Verify HTTP/HTTPS and email transports on hardware.
- [x] Implement and compile-check Bluetooth LE.
- [x] Implement and compile-check USB HID mouse.
- [x] Implement and compile-check double math, watchdog and cryptographic UUID.
- [ ] Implement and verify `SecretStore` on the onboard ATECC608.
- [ ] Implement and verify the Mbed RTOS thread port.

Credentials are never stored in this worktree or printed by the verification programs.
