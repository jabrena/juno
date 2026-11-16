# Is Java possible on microcontrollers?

**Subtitle:** Compiling plain `javac` output to bare-metal assembly with JUno, and lighting up an Arduino for Christmas

**Alternative titles:** "Santa's Smallest JVM-less Java: Blinking Lights on an Arduino" · "No JVM Under the Tree:
Java on a Microcontroller"

**Publication:** JVM Advent (https://www.javaadvent.com), one article in the December 1–24 calendar

**Format:** Hands-on technical article, about 2,500 words, with code samples, a short build log and board photos

**Audience:** Java developers who have never programmed a microcontroller. No C++ or electronics background needed.

**Project:** https://github.com/jabrena/juno (Apache License 2.0)

## Abstract

A microcontroller has a few kilobytes of RAM and no operating system. A JVM does not fit there, so most Java developers
assume Java does not either, and switch to C++ when they want to program an Arduino. This article tests that
assumption with JUno, an open source ahead-of-time compiler. JUno takes the `.class` files that `javac` produces,
links the reachable code in a closed world on the developer's machine, and emits ARM Thumb-2, Xtensa or AVR assembly.
The result is flashed to the board with no JVM and no interpreter.

We start from the embedded "Hello, World": a blinking LED, written as an ordinary Java program with a `main` method
and built with Maven. From there we follow the program through the compiler, see which parts of the language survive
on a few kilobytes of RAM, and finish with a festive program on the UNO R4 WiFi's LED matrix that you can unit-test
on the JVM before you flash it.

## Outline

1. **Why not just run a JVM?** What a microcontroller offers (memory, no OS, no dynamic loading), and why an
   ahead-of-time, closed-world compiler is a better fit than an interpreter.
2. **From `javac` to the board.** The JUno Maven plugin compiles, verifies, flashes and opens the serial monitor.
   Under the hood: bytecode decoding, a whole-program linker that keeps only reachable code, an optimizer (inlining,
   scalar replacement, bounds-check elimination) and backends that hand assembly to the board's own `arduino-cli`
   toolchain.
3. **Java on a fixed budget.** Objects, arrays, records and lambdas live in one arena whose size is fixed at build
   time and that a mark-and-sweep garbage collector reclaims. Read-only `static final` tables move to flash. Each
   build prints a memory report, so a program that would run out of memory fails at compile time rather than on the
   desk.
4. **How much of the language fits.** Primitives with Java semantics (including `long`, `float` and `double`),
   enums, records, sealed interfaces, exceptions with try-with-resources, lambdas, and virtual threads built on
   continuations. And the honest limits: no reflection, no dynamic class loading, and short runtime strings.
5. **A hardware API you can test.** GPIO, I2C, serial, Wi-Fi and the LED matrix are interfaces, so the same program
   runs under JUnit and Mockito on your laptop before it runs on the board.
6. **Your turn.** Which boards work today (Arduino UNO R3, UNO WiFi Rev2, UNO R4 WiFi, Portenta H7, Nano ESP32,
   ESP32-CAM, M5Stack Cardputer Adv and Raspberry Pi Pico 2 W) and how to try JUno on one you already own.

## What readers will take away

- How an ahead-of-time Java compiler for a microcontroller is put together, from class files to assembly.
- Which Java features are cheap and which are expensive when the whole heap is a few kilobytes.
- How to write embedded Java that is testable on the JVM, and how to flash it to a real board with Maven.
