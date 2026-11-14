# Portenta H7 board-feature implementation plan

Source artifacts:

- Published feature matrix: <https://jabrena.github.io/juno/board-features/>
- Published Portenta H7 notes: <https://jabrena.github.io/juno/arduino-portenta-h7/>
- Arduino Portenta H7 documentation and datasheet: <https://docs.arduino.cc/hardware/portenta-h7>
- Repository board profile: `juno-compiler/src/main/java/io/github/jabrena/juno/board/Board.java`
- Repository capability metadata: `juno-compiler/src/main/java/io/github/jabrena/juno/board/Capability.java`

Derivation: published matrix + Portenta core inspection -> implementation sequence. This file is the execution
tracking artifact; no OpenSpec change is required for this work.

## Missing features

The matrix currently marks these features unsupported for Portenta H7:

1. Wi-Fi station mode
2. Wi-Fi access-point mode
3. HTTP client
4. HTTPS client
5. HTTP server
6. HTTPS server
7. Email over implicit TLS and POP3
8. Email over SMTP STARTTLS
9. Bluetooth LE central
10. USB HID mouse
11. Double-precision `java.lang.Math`
12. Threads, locks, and structured concurrency
13. `@Watchdog`
14. Cryptographic `UUID.randomUUID()`

The Portenta H7 has onboard ATECC608 secure elements. `SecretStore` is an existing external-I2C API rather than a
row in the published matrix; it is included as a verification item and should work without a new board capability.

Ethernet is present in the Arduino core, but Juno has no Ethernet API or capability row, so it is intentionally not
part of this change.

## Execution sequence

- [ ] Add Portenta capability declarations and core-specific adapters; keep unsupported HTTPS server and STARTTLS
      behavior explicit until generated sketches compile against the installed libraries.
- [ ] Implement and verify Wi-Fi, AP mode, HTTP/HTTPS clients, HTTP server, and TLS email.
- [ ] Implement and verify double-precision math and cryptographic UUID generation.
- [ ] Implement and verify USB HID mouse and Bluetooth LE central.
- [ ] Implement and verify the Mbed watchdog.
- [ ] Implement and verify threads, locks, structured concurrency, and scoped values with an Mbed-safe carrier.
- [ ] Verify `SecretStore` through the onboard ATECC608/I2C path.
- [ ] Update the board and feature documentation from `juno-site/src/main/resources/content/` and run site tests.

## Verification rules

- Add or extend compiler/backend tests for every capability and Portenta-specific runtime adapter.
- Compile affected examples with `juno:verify` using `arduino:mbed_portenta:envie_m7`.
- Flash each hardware-test program over `/dev/cu.usbmodem11201`, then run `juno:monitor` and compare serial output.
- Do not claim network, BLE, HID, watchdog, or secure-element behavior verified without the corresponding hardware
  observation.
