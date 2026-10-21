---
title: "Arduino Nano ESP32"
description: "Board notes for the Arduino Nano ESP32 and what Juno supports on it so far."
layout: page
---

The Nano ESP32 puts an Espressif ESP32-S3 (in a u-blox NORA-W106 module) on the Nano form factor.
Unlike the UNO boards, its CPU is an Xtensa LX7, so Juno generates Xtensa assembly for it with its
own backend instead of the Thumb-2 or AVR one.

## Core specification

| Characteristic | Nano ESP32 |
|---|---:|
| Product SKU | ABX00083 |
| Module | u-blox NORA-W106-10B (ESP32-S3) |
| CPU architecture | Dual-core 32-bit Xtensa LX7 |
| CPU clock | 240 MHz |
| Program flash | 16 MB (external) |
| SRAM | 512 kB, plus 8 MB PSRAM |
| Logic and operating voltage | 3.3 V |
| USB connector | USB-C (native USB) |
| Built-in LED | D13 (ESP32-S3 GPIO48) |
| Arduino CLI FQBN | `arduino:esp32:nano_nora` |

## What Juno supports

This is the first step of Nano ESP32 support: `Gpio`, `DigitalOutput`, `Delay` and `Serial` over
the native USB port, with `int`, `long`, `float` and `double` arithmetic, objects, arrays, static and
interface calls, lambdas and method references. Pins keep the numbers printed on the board: the core builds with
`BOARD_HAS_PIN_REMAP`, and Juno calls `pinMode`/`digitalWrite` through wrappers compiled under
that remapping, so `MCU.getGpio().getBuiltinLed()` is `13` and drives GPIO48.

`long`, `float` and `double` operations go through the same runtime helpers as on the other boards, which
keep Java semantics (a `long` division by zero stops the program, float-to-int casts saturate, NaN
comparisons are false); the ESP32 toolchain compiles the `float` helpers to the ESP32-S3's
single-precision FPU (`double` has no FPU and runs as soft-float), and the double-precision
`java.lang.Math` functions use the toolchain's libm. [`SerialWideMath`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialWideMath.java)
prints a block of `long` and `float` results and
[`SerialDoubleMath`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialDoubleMath.java)
one of `double` and `Math` results, both to compare with a desktop JVM run;
[`SerialLambdas`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialLambdas.java)
exercises every lambda and method-reference shape.

Threads, `synchronized`, `ReentrantLock`, `StructuredTaskScope` and `ScopedValue` work too. Each
Juno thread is a FreeRTOS task with its own 8 KiB stack (`-Djuno.Xss` changes it), and only one
runs Java code at a time. A busy thread hands the CPU over at its next loop iteration or method
call once its 1 ms time slice is over, so a spin loop cannot starve the others. At most four
threads run at once, `main` included, unless `-Djuno.maxThreads` raises it; starting a fifth stops
the program with `[juno-thread] too many threads`.
[`SerialThreads`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialThreads.java)
runs each of them once at startup and then prints the results.

`@Watchdog`, Wi-Fi and the other optional capabilities are not wired yet; Juno rejects a program
that uses them at compile time.

## Build and run

Install the core once with `arduino-cli core install arduino:esp32`. `Blink` builds for any board,
and [`SerialBlink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialBlink.java)
targets the Nano ESP32 and reports every LED change at 115200 baud:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-nano-esp32
```

The board can list two ports at once, its USB DFU port (for example `1-1`) and its serial port, so
the plugin cannot pick one alone. Upload through the DFU port and monitor the serial one:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.serial.SerialBlink -Djuno.port=1-1
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 \
  -Djuno.port=/dev/cu.usbmodem<ID> -Djuno.baudRate=115200
```

The USB port re-enumerates when the program starts, so a program should wait a moment after
`Serial.begin` before its first line, as `SerialBlink` does.
