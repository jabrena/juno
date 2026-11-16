# Is Java possible on microcontrollers?

**Subtitle:** JUno, an ahead-of-time compiler that turns `javac` output into bare-metal assembly for Arduino-class boards

**Track:** Free Java devroom (alternative: Embedded, Mobile and Automotive devroom)

**Format:** Talk with live demo, 25–30 minutes

**Project:** https://github.com/jabrena/juno (Apache License 2.0)

## Abstract (short)

A microcontroller has a few kilobytes of RAM and no operating system. A JVM does not fit there, so most people assume
Java does not either. JUno is an open source ahead-of-time compiler that tests that assumption. It takes the `.class`
files that `javac` produces, links the reachable code in a closed world on the developer's machine, and emits ARM
Thumb-2, Xtensa or AVR assembly. The result is flashed to the board with no JVM and no interpreter. The same Java
source runs on eight boards:

- **Arduino UNO family:** UNO R3, UNO WiFi Rev2 and UNO R4 WiFi
- **Arduino Portenta and Nano:** Portenta H7 and Nano ESP32
- **ESP32 boards:** AI Thinker ESP32-CAM and M5Stack Cardputer Adv
- **Raspberry Pi:** Pico 2 W

This talk explains how the compiler works, which parts of Java survive on a microcontroller and which do not, and what
we learned by checking every feature on real hardware.

## Description

Java developers who want to program an Arduino usually have to switch to C++. JUno keeps them in Java. You write an
ordinary Java program with a `main` method, build it with `javac` and Maven, and the JUno Maven plugin compiles,
verifies and flashes it to the board, then opens the serial monitor.

The talk covers five areas.

**1. The pipeline.** JUno does not replace `javac`. It reads class files, decodes a defined subset of bytecode into
an intermediate representation, and runs a whole-program linker that keeps only the code the program reaches. An
optimizer inlines methods, replaces some objects with scalars, removes bounds checks it can prove are safe and fuses
compares, which removed about 40% of the generated instructions. Pluggable backends then emit assembly for Cortex-M
(Thumb-2), ESP32 (Xtensa) and AVR. The board's own toolchain (`arduino-cli`) assembles and links that output, so JUno
works with existing board support packages instead of replacing them.

**2. Java on a fixed budget.** The board has no heap that can grow. Objects, arrays, records and lambdas live in one
arena whose size is fixed at build time and that a conservative mark-and-sweep garbage collector reclaims. Read-only
`static final` tables move to flash. Each build prints a report with arena and stack usage and flags risks such as
recursion, so a program that would run out of memory fails at compile time rather than on the desk. Anything outside
the subset stops the build with a diagnostic that names the method and the bytecode, so nothing fails silently.

**3. How much of the language fits.** The subset is larger than you might expect: all primitive types (including
`long`, `float` and `double` with Java semantics), enums, records, sealed interfaces, exceptions with
try-with-resources, lambdas and method references, `BigInteger` and `BigDecimal`, and virtual threads implemented with
continuations, plus structured concurrency and scoped values. We will also show the limits: reflection, dynamic class
loading, and runtime strings longer than 31 bytes.

**4. A hardware API that you can test.** The board API (GPIO, I2C, serial, Wi-Fi, HTTP client and server, TLS, UDP,
TCP, SD card, LED matrix, secure elements) is written for zero allocation, and it is built on interfaces, so the same
program can be unit-tested on the JVM with JUnit and Mockito before it is flashed.

**5. Testing a compiler for hardware you cannot put in CI.** JUno uses several layers of tests: unit tests on the
generated assembly, `arduino-cli` builds in Testcontainers, QEMU Cortex-M runs whose serial output is compared with
the output of the same program on OpenJDK, and finally manual checks on real boards. Each layer found bugs that the
others missed, and we will show some of them.

Attendees will leave knowing how an ahead-of-time Java compiler for a microcontroller is built, which Java features
are cheap or expensive on a few kilobytes of RAM, and how to try JUno on a board they already own.
