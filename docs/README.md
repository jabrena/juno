# Documentation

- [SERIAL.md](SERIAL.md) — the `Serial` USB output API, and why it's the main way to observe a
  running Juno program.
- [JUNO-MAVEN-PLUGIN.md](JUNO-MAVEN-PLUGIN.md) — `juno-maven-plugin`'s goals
  (`compile`/`verify`/`upload`/`monitor`/`env`) and configuration.
- [LCD-KEYPAD-SHIELD.md](LCD-KEYPAD-SHIELD.md) — driving the 16x2 LCD Keypad Shield's display and
  5 buttons with `LcdKeypadShield`.
- [HID.md](HID.md) — acting as a USB HID mouse with `Mouse`.
- [CLOCK.md](CLOCK.md) — reading elapsed time with `Clock`, and when to prefer it over `Delay`.
- [INTERNET.md](INTERNET.md) — Wi-Fi, HTTP/HTTPS REST calls, and bounded JSON response extraction.
- [EMAIL.md](EMAIL.md) — basic email support: sending with `Smtp`, reading a mailbox with
  `Pop3Client`.
- [APIS.md](APIS.md) — how Juno's compiler-intrinsic API classes work internally, and the
  checklist for adding a new one.
- [FEATURES.md](FEATURES.md) — the current inventory of Juno's supported Java subset (what
  compiles today, and what's explicitly not yet supported).
- [ARDUINO.md](ARDUINO.md) — installing `arduino-cli` and using it to build, upload, and monitor
  Juno-generated sketches on real UNO R4 hardware.
- [ARDUINO-ONE-R4.md](ARDUINO-ONE-R4.md) — UNO R4 Minima/WiFi hardware specifications,
  toolchain targets, and Juno compatibility.
- [ARDUINO-ONE-R3.md](ARDUINO-ONE-R3.md) — UNO R3 hardware specifications, AVR constraints,
  toolchain target, and current experimental Juno compatibility.
- [ARDUINO-ONE-R3-WIFI.md](ARDUINO-ONE-R3-WIFI.md) — UNO WiFi Rev2 hardware specifications,
  wireless peripherals, megaAVR toolchain target, and prospective Juno compatibility.
- [TYPES.md](TYPES.md) — how Java's primitive types map onto Arduino/C++ types in generated
  sketches, and why Juno only supports `boolean`, `byte`, `char`, `short`, and `int`.

## Javadocs

- [Javadoc](javadocs/0.1.0-SNAPSHOT/apidocs/index.html) — generated API docs for the `juno`
  module (`./mvnw -pl juno javadoc:javadoc` regenerates it; see the README's Development
  section).
