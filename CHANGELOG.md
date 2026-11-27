# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-11-27

### Added

- Added a Maven BOM to manage the versions of Juno modules and dependencies.
- Added a Maven archetype for generating starter Juno projects, including a Blink entry point.
- Added release automation and JReleaser configuration for publishing Maven artifacts to Maven Central.

## [0.1.0] - 2026-11-26

### Added

- Added the Juno compiler and runtime for compiling a documented Java subset to board firmware, including primitive types, arrays, records, enums, exceptions, strings, lambdas, method references, and selected JDK APIs.
- Added board-side networking with `java.net`, HTTP/HTTPS clients and servers, JSON processing, TLS, SMTP/POP3S email, URI handling, and internet-backed time.
- Added structured concurrency, virtual threads, blocking concurrency primitives, preemptive scheduling, and dual-core execution on supported Portenta H7 boards.
- Added support for Arduino UNO R3, UNO R4 WiFi, Nano ESP32, Portenta H7, Raspberry Pi Pico 2 W, AI Thinker ESP32-CAM, M5Stack Cardputer, and their board-specific capabilities.
- Added hardware APIs for displays, LED matrices, touch input, cameras, IMUs, SD cards, Wi-Fi, USB host devices, Grove sensors, Arduino shields, and motor controllers.
- Added robotics APIs for navigation, obstacle detours, gyroscope-assisted driving, LEGO RCX, LEGO Power Functions, and LEGO Powered Up hubs.
- Added the Juno Maven plugin with compilation, verification, flashing, monitoring, board discovery, dependency installation, and secret provisioning goals.
- Added the JLM1 on-board language-model toolchain and runtime, and the JEV1 decision-model toolchain and runtime.
- Added Java, Kotlin, Scala, Groovy, and Clojure examples, conformance tests, emulator/toolchain verification, and a generated documentation site with API Javadocs.
- Added board-side resource loading, secure secret storage, mailbox applications, and TLS certificate loading from SD cards.

### Changed

- Unified board services behind the Arduino/MCU facade and reorganized the public API around hardware, robotics, remote, and utility packages.
- Introduced board-aware memory profiles, arena allocation, conservative garbage collection, watchdog support, and zero-allocation board APIs.
- Made assembly generation the compiler's primary backend and added Cortex-M4, Xtensa, and AVR code-generation paths with board capability validation.
- Expanded the release build with Maven Central metadata, source and Javadoc artifacts, detached GPG signatures, and Central Portal publishing configuration.
- Improved HTTP concurrency, ESP32 and Pico 2 W scheduling, Portenta TLS handshakes, safepoints, and runtime string handling.
- Added Maven Enforcer, Spotless, Error Prone, NullAway, Sonar, mutation testing, JaCoCo, architecture rules, and board/emulator integration checks.
- Expanded CI coverage for Arduino CLI, simavr, QEMU, Xtensa emulation, language examples, board matrices, Maven Central metadata, and generated documentation.

### Removed

- Removed Arduino UNO Q support.
- Removed UNO WiFi Rev2 support and UNO R3 network/Wi-Fi Shield support.
- Replaced direct environment-file and ATECC608 credential access with the board `SecretStore` API.

### Fixed

- Fixed RCX firmware download, infrared communication, motor control, tone handling, and remote-control behavior.
- Fixed HTTP server connections that never send a request, failed SMTP connection cleanup, StringBuilder result lifetime and size limits, and UNO R3 numeric-value limits.

[Unreleased]: https://github.com/jabrena/juno/compare/5d5b91db1...HEAD
[0.2.0]: https://github.com/jabrena/juno/compare/0.1.0...5d5b91db1
[0.1.0]: https://github.com/jabrena/juno/compare/0123da8fc...be8359ba1
