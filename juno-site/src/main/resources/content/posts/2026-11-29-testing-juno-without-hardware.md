---
title: "Testing Juno programs with and without hardware"
description: "Use host tests, generated assembly checks, Arduino CLI verification, QEMU and serial monitoring to test Juno programs at the right boundary."
date: 2026-11-23
layout: post
link: /blog/2026-11-29-testing-juno-without-hardware/
tags: [testing, toolchain, embedded]
blogVisible: true
---

Embedded testing is not one test. A Juno program crosses several boundaries, and each boundary needs a different kind of
evidence. A host test can validate an algorithm; it cannot prove that generated assembly links or that a Wi-Fi exchange
works on a board.

## Test the pure logic on the host

Keep calculations, state machines and protocol parsing separate from hardware calls. These parts can run quickly in JUnit
or an ordinary Java process. Test normal values, limits, malformed input and repeated cycles.

This is where property and table-driven tests are most useful: they do not need a board and can run on every commit.

## Test compiler output offline

Juno's compiler tests should check that a source feature produces the expected intermediate behavior, assembly and shim
requirements. Deterministic output matters: a compiler change should not silently add a library include or alter a
calling convention.

When an intrinsic or bytecode feature changes, add a focused compiler test and inspect the generated `.S`, `Shim.cpp`
and report. The report exposes arena estimates, stack requirements and risk findings before hardware is involved.

The ordinary offline suite is:

```bash
./mvnw test
```

The opt-in Arduino CLI and QEMU profiles need Docker:

```bash
./mvnw -pl juno-compiler -am -Parduino-cli verify
./mvnw -pl juno-compiler -am -Pqemu verify
```

The QEMU profile exercises selected Cortex-M4 behavior. Language fixtures can compare with an OpenJDK run, while device
fixtures use checked-in expected serial output. A separate Xtensa profile covers selected ESP32-S3 cases.

## Verify with the real Arduino toolchain

`juno:verify` compiles generated output with the selected Arduino core without uploading it. On a clean checkout, install
the development plugin first and install the selected Arduino core:

```bash
./mvnw install
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

This catches missing headers, incompatible native types, linker failures and board-core integration problems. It does
not prove runtime behavior. Check the test summary and logs when assembly verification is important; some optional
external-tool checks can skip when the tool is unavailable.

## Use QEMU for supported CPU behavior

Juno's QEMU tests can execute selected Cortex-M4 programs and compare serial output with a host run. This is useful for
core language behavior and deterministic runtime features, but it models neither the complete Arduino board nor every
peripheral.

Treat QEMU as an additional confidence boundary, not a replacement for Wi-Fi, timing, camera, display or electrical
tests.

## Finish on real hardware

When a feature uses a physical peripheral, compile and upload through the Juno Maven goals over USB serial. For a
repeatable serial check, use `SerialCounter`:

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.hardware.serial.SerialCounter \
  -Djuno.board=arduino-uno-r4-wifi -Djuno.port=<PORT>

./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=arduino-uno-r4-wifi -Djuno.baudRate=115200
```

Expect consecutive integers about one second apart. A successful upload only proves that the bootloader accepted the
image; the monitor establishes that the program booted and emitted the expected trace. Physical timing, actuator and
fault-injection tests still need a controlled bench, with loads secured during the first run.

For concurrency and networking, test slow responses, timeouts, failed connections, full queues and repeated batches.
Observe both output and resource behavior: a program that prints the right value once may still leak work or exhaust
native RAM after an hour.

## Match evidence to the risk

| Risk | Useful evidence |
|---|---|
| Algorithm or parser bug | Host unit test |
| Bytecode or intrinsic regression | Compiler and generated-output test |
| Board-core integration | `juno:verify` / Arduino CLI compile |
| Deterministic CPU runtime behavior | QEMU test |
| Peripheral, timing or network behavior | USB upload plus serial monitor |

The most reliable workflow moves from cheap, deterministic tests to increasingly realistic environments. Each step
answers a different question, and no single green build can answer all of them.
