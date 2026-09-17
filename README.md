# Juno: Java for Arduino

Juno is an experimental ahead-of-time compiler for running a practical subset of Java on the
Arduino UNO R4 WiFi and UNO R4 Minima. It keeps `javac` as the Java frontend, performs
closed-world linking on the development machine, and emits an Arduino C++ sketch which the
Renesas toolchain compiles to native Cortex-M4 code.

![](./documentation/boards/arduino-one-r4-wifi.png)

This repository contains a working v0.1 compiler, not a JVM interpreter. The first milestone
supports 32-bit integer code, static methods, branches, loops, and GPIO/time intrinsics. It can
compile [`juno-examples/src/main/java/Blink.java`](juno-examples/src/main/java/Blink.java) all the way to a
`.ino` sketch.

```text
Java source -> javac -> .class -> Juno linker/AOT -> .ino -> Arduino toolchain -> RA4M1
```

## Modules

This is a multi-module Maven build:

- [`juno/`](juno) — the Juno compiler itself (`classfile`, `bytecode`, `linker`, `backend`,
  and the `api` hardware abstraction). Builds `juno/target/juno-<version>.jar`, an executable
  jar whose main class is `io.github.jabrena.juno.Main`.
- [`juno-examples/`](juno-examples) — example Java programs written against the `juno` module's `api`
  package, compiled by Maven like any other Java module (`juno-examples/target/classes`) so they are
  checked for compile errors on every build.

## Quick start

Requirements: JDK 25+ and Maven 3.9+.

```bash
./mvnw clean package
```

This builds both modules: `juno/target/juno-0.1.0-SNAPSHOT.jar` (the compiler) and
`juno-examples/target/classes` (the compiled example programs). To turn an example into a `.ino`
sketch and run it on real UNO R4 hardware with `arduino-cli`, see
[docs/ARDUINO.md](docs/ARDUINO.md).

## Java API

Hardware operations are normal Java native declarations at compile time and compiler intrinsics
at link time:

```java
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

public final class Blink {
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(13);
        while (true) {
            led.toggle();
            Delay.millis(500);
        }
    }
}
```

Available v0.1 intrinsics are:

- `Gpio.pinMode`, `digitalWrite`, `digitalRead`, `analogRead`, `analogWrite`, and `toggle`
- zero-allocation `DigitalOutput.of`, `high`, `low`, `toggle`, and `isHigh`
- `Delay.millis` and `Delay.micros`
- `Clock.millis` and `Clock.micros`
- `Serial.begin`, `print`, and `println` for USB serial output of integers, readable on the
  development machine with `arduino-cli monitor` (see
  [`juno-examples/src/main/java/SerialCounter.java`](juno-examples/src/main/java/SerialCounter.java), which
  counts up once a second)
- `LedMatrix.begin`, `loadFrame`, and `clear` for the UNO R4 WiFi's built-in 12x8 LED matrix
  (see [`juno-examples/src/main/java/LedMatrixHeart.java`](juno-examples/src/main/java/LedMatrixHeart.java),
  the self-playing
  [`juno-examples/src/main/java/LedMatrixSnake.java`](juno-examples/src/main/java/LedMatrixSnake.java), and
  the classic
  [`juno-examples/src/main/java/LedMatrixBouncingBall.java`](juno-examples/src/main/java/LedMatrixBouncingBall.java));
  each frame is 96 pixels packed MSB-first into three 32-bit words, matching
  `Arduino_LED_Matrix::loadFrame(const uint32_t[3])`

On top of the intrinsics, `io.github.jabrena.juno.api` also has plain (non-intrinsic) Java helpers
for the LED matrix, since Juno v0.1 has no arrays to hold a font table:

- `LedCanvas` — pixel/frame-word addressing (`setPixel`, `inBounds`, `packPos`) shared by every
  LED matrix example
- `LedMatrixFont` — a classic 5x7 dot-matrix font for digits (`digitPixel`) and uppercase letters
  (`letterPixel`), encoded as small per-glyph functions instead of an array
- `LedMatrixText` — `drawDigit`/`drawLetter`/`drawChar`, which OR a glyph into a frame word at a
  given origin; two glyphs fit side by side (5 + 1 gap + 5 = 11 of the 12 columns)
- `LedMatrixFontAscii` — the full printable ASCII range (32 space .. 126 `~`) as 5x7 glyphs
  (`charPixel`), reusing `LedMatrixFont`'s digits/uppercase and adding punctuation/symbols and
  distinct lowercase shapes; since Juno has no `String`, `drawChar` prints one character at a time
- `LedMatrixFontSmall` — a compact 3x5 digit-only font (`digitPixel`, `pointPixel`), offered as a
  smaller alternative to `LedMatrixFont` rather than a replacement for it
- `LedMatrixSmallText` — `drawSmallDigit`, and `drawDecimal` for a one-decimal-digit reading like
  "2.5" (digit + point + digit = 9 of the 12 columns, e.g. `drawDecimal(word, idx, 2, 5, 1, 1)`)
- `LedMatrixShapes` — `fillRect`/`drawRect` for a solid or 1-pixel-border-outline `width x height`
  rectangle at a given origin (a square is just a rectangle with `width == height`);
  `fillTriangle`/`drawTriangle` for a solid or outlined triangle given three vertices; `drawLine`
  (Bresenham) for a straight line between two points; `fillCircle`/`drawCircle` (midpoint circle
  algorithm) for a solid or outlined circle given a center and radius
- `LedMatrixTransform` — `rotateX`/`rotateY` rotate a point 90/180/270 degrees around a pivot using
  an integer 2D rotation matrix (no `float` needed, since `cos`/`sin` are always -1, 0, or 1 at
  those angles); apply it to a shape's vertices before calling `LedMatrixShapes` to rotate it

Because linking is closed-world, a program that only calls `drawDigit` never pulls the 26-letter
table into flash — see
[`juno-examples/src/main/java/LedMatrixCountUp.java`](juno-examples/src/main/java/LedMatrixCountUp.java),
which counts 1 to 10 on the matrix,
[`juno-examples/src/main/java/LedMatrixDecimalCountUp.java`](juno-examples/src/main/java/LedMatrixDecimalCountUp.java),
which counts 0.0 to 9.9 with the smaller font,
[`juno-examples/src/main/java/LedMatrixRectangles.java`](juno-examples/src/main/java/LedMatrixRectangles.java),
which cycles filled and outlined squares and rectangles,
[`juno-examples/src/main/java/LedMatrixSpinningTriangle.java`](juno-examples/src/main/java/LedMatrixSpinningTriangle.java),
which rotates a triangle through its four 90-degree orientations,
[`juno-examples/src/main/java/LedMatrixCircles.java`](juno-examples/src/main/java/LedMatrixCircles.java),
which cycles a filled and an outlined circle,
[`juno-examples/src/main/java/LedMatrixAsciiScroll.java`](juno-examples/src/main/java/LedMatrixAsciiScroll.java),
which cycles digits, uppercase, lowercase, then punctuation one character at a time with
`drawChar`, and
[`juno-examples/src/main/java/LedMatrixScrollingText.java`](juno-examples/src/main/java/LedMatrixScrollingText.java),
which scrolls "Juno, Java for Arduino ONE R4" across the matrix from right to left.

## Supported Java subset

Juno deliberately fails at link time when reachable code uses something outside the current
subset. Diagnostics identify the method, bytecode offset, and unsupported opcode.

Supported today:

- `static void main(String[])` and the embedded-friendly `static void main()`
- static methods with `boolean`, `byte`, `char`, `short`, and `int` arguments/results
- local variables, integer constants, arithmetic, bitwise operations, shifts, comparisons,
  conditionals, and loops
- direct static calls with closed-world reachability; unused methods are omitted
- opaque `DigitalOutput` handles which compile down to integer pin numbers without heap allocation
- Java-compatible 32-bit wrapping arithmetic and divide-overflow behavior
- `.class` inputs from directories, individual files, or JARs

Not yet supported:

- general objects, constructors, instance/virtual/interface calls, or arrays used by application code
- static fields, strings, exceptions, garbage collection, threads, reflection, or dynamic loading
- `long`, `float`, and `double`
- the desktop JDK class library

The `String[]` parameter of a conventional `main` is accepted as an entrypoint convention, but
there are no command-line arguments on the board and it must not be accessed.

## Architecture

- `classfile` parses standard JVM class files and resolves constant-pool references.
- `bytecode` decodes and validates the v0.1 instruction set.
- `linker` starts at `main`, follows reachable static calls, resolves hardware intrinsics, and
  eliminates unreachable methods.
- `backend` emits standalone Arduino C++ with compact operand/local stacks. The native compiler
  can then optimize those fixed-size structures.
- `api` provides the small Java-facing hardware abstraction.

All of the above live under [`juno/src/main/java/io/github/jabrena/juno/`](juno/src/main/java/io/github/jabrena/juno).

Juno currently uses ArduinoCore-renesas as its HAL. Moving selected intrinsics to Renesas FSP or
direct registers, then adding an SSA IR before C emission, are natural later steps.

## Development

```bash
./mvnw test
```

Runs the full reactor's tests, including the `juno` module's compiler unit tests and the
generated-C++ syntax check. The tests compile Java fixtures, check reachability and failure
diagnostics, and pass generated C++ through `clang++` or `g++` with a minimal Arduino
compatibility header when one is available.

```bash
./mvnw -pl juno javadoc:javadoc
```

Generates the `juno` module's Javadoc HTML into `docs/javadocs/<version>/apidocs`, e.g.
`docs/javadocs/0.1.0-SNAPSHOT/apidocs/index.html`.

## References

- https://store.arduino.cc/products/uno-r4-wifi
