# Documentation

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
