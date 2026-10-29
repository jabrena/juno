---
title: "Known Limitations"
description: "Known compiler bugs and sharp edges in what Juno compiles today, with workarounds."
layout: page
---

The [Feature Inventory](/features) lists what Juno supports. This page lists where reality is narrower than that
inventory or where the generated code behaves differently from the JVM, so you can design around it. Anything
outside the supported subset fails at link time with a diagnostic; a *known bug* would be the exception,
because it compiles without one.

## Known bugs

None open. Bugs the QEMU suite has found are fixed and kept as regression programs; open ones are listed in
`QemuRunIT.KNOWN_GAPS`. Fixed so far: `long` and `double` parameters, results and fields lost their high word on
the Thumb-2 path (for example `factorial(20)` printed `2192834560`, the low 32 bits, with no diagnostic), and
`long[]`, `double[]` and `float[]` arrays were rejected by the backend although the inventory listed them.

## Compile-time limits that are easy to hit

These fail with a `CompileException`; they are listed because they come up when writing ordinary Java.

- **Compound assignment on an array element** (`values[i] += x`, `values[i]++`). javac compiles it with `dup2`, which
  is outside the accepted opcode subset; write `values[i] = values[i] + x`.
- **`length` of an array parameter.** `arraylength` works only on a local array created once in the same method
  with a constant size. A method that receives an array must also receive its length, and an enhanced `for`
  over a parameter (which javac compiles to `arraylength`) is rejected. Use an indexed loop with a count.
- **Inheritance on small boards.** Class inheritance, inherited methods and default interface methods are supported,
  but the AVR boards have much less flash, SRAM and arena space. A generated sketch may fail the Arduino link or
  exhaust the arena at runtime; refactor the object graph when that happens.
- **JDK functional interfaces** such as `IntBinaryOperator` in signatures. Declare your own interface.
- **`Object` in signatures**, which rules out `equals(Object)` on records and classes when it is reachable.
- **Concatenating arbitrary objects.** Juno lowers `StringConcatFactory` for strings and primitive
  values, but not the general `String.valueOf(Object)`/`toString()` path.

## Memory

These compile without an error, but cost RAM the JVM would not.

- **Only `static final` constant arrays live in flash.** A `static final` one-dimensional primitive array
  initialized with constants, and only ever read as `T[i]` or `T.length`, is placed in flash and costs no RAM.
  The same array declared as an instance field (`final byte[] DATA = {...}`) is built in the arena each time
  the object is created, even if there is only one instance, so a large image or lookup table can exhaust the
  8 KiB arena and stop the board at startup. Keep large constant tables `static final`, and read them only by
  index: passing a table to a method or storing it elsewhere also keeps it in RAM (reported as `JUNO-RISK-012`).

## Exceptions

See [Feature Inventory](/features) for the supported subset. Beyond it:

- An integer division by zero raises a catchable `ArithmeticException`, from a callee too, but only when some
  `catch` or `finally` in the program can receive it; otherwise it still panics (and costs no guard).
- `addSuppressed` is accepted and dropped, so a `close()` failure while another exception unwinds is lost.
- Exception causes and stack traces are not modelled.

## Board differences

The API is the same on every board, but some calls can only return what the board's own stack exposes.

- **Network addresses on the UNO Q.** The UNO Q's Wi-Fi belongs to its Linux side; the MCU that runs your program
  reaches the network only through the `arduino-router` bridge, and version 0.4.x of the bridge does not expose the
  Linux interface's addresses. `Wifi.localIP`, `Wifi.gatewayIP` and `Wifi.macAddress` therefore write zeroes there
  (`0.0.0.0`), while the UNO R4 WiFi reports the real values. Wi-Fi itself, `Wifi.status` and UDP work normally; the
  board is still reachable at its address, and `arduino-cli board list` shows it. A program that needs its own
  address on the UNO Q can learn it from the source address of its own UDP broadcast, when the network echoes it
  back.

## How these are found

`./mvnw -pl juno-compiler -am -Pqemu verify` builds each program in `juno-compiler/src/test/qemu/programs`
with the real generated assembly, runs it in QEMU (Cortex-M4) and requires output identical to the same source run on OpenJDK. Known
bugs are listed in `QemuRunIT.KNOWN_GAPS` and skipped, so the suite stays green while the bug stays visible.
The harness models no hardware, so it covers language and runtime behavior only.
