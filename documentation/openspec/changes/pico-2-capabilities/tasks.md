## Implementation Tasks

- [x] Separate UNO generations from Nano, ESP32-CAM, M5Stack, and Pico board families (`refactor(compiler): model board families separately`).
- [x] Add Pico 2 W HTTP client and server runtime support, tests, and commit it (`7b43ea86`).
- [x] Add Pico 2 W HTTPS/TLS client/server and TLS email support, tests, and commit it (`7945052b`).
- [x] Add Pico 2 W Bluetooth LE adapter and Bluetooth-enabled FQBN configuration, tests, and commit it (`bb1a13bf`).
- [x] Add Pico 2 W `@Watchdog` support, tests, and commit it (`7945052b`).
- [x] Add Pico 2 W threads, locks, `StructuredTaskScope`, and `ScopedValue` support, tests, and commit it (`eae5fe52`).
- [x] Add Grove Shield UART0, UART1, and SPI0 mappings, tests, and commit it (`567a7ffa`).
- [x] Add Pico 2 W double-precision `Math` support, tests, and commit it (`823e16a3`).
- [x] Add Pico 2 W USB HID mouse support, tests, and commit it (`5555b436`).
- [x] Update Pico 2 W documentation, hardware-validation notes, and `RaspberryPico2GroveOled` output; validate the site and commit it (`0baecacb`).
- [x] Run the full Maven and site verification (`./mvnw test`, `./mvnw clean verify -Psite -DskipTests`, and `./mvnw -pl juno-site test`); the Docker-backed Arduino CLI test was skipped because Docker is unavailable.
- [ ] Review the commit series and prepare one PR containing all commits.
