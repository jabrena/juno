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

- [`juno-api/`](juno-api) — the small Java-facing hardware API (`Gpio`, `Delay`, `Clock`,
  `DigitalOutput`, `LedMatrix`, `Serial`, `Mouse`, …) that Juno recognizes as compiler intrinsics.
  Has no dependency on the compiler, so example/user code only needs this module on its classpath.
- [`juno/`](juno) — the Juno compiler itself (`classfile`, `bytecode`, `linker`, `backend`, …).
  Builds `juno/target/juno-<version>.jar`, an executable jar whose main class is
  `io.github.jabrena.juno.Main`.
- [`juno-examples/`](juno-examples) — example Java programs written against `juno-api`, compiled by
  Maven like any other Java module (`juno-examples/target/classes`) so they are checked for
  compile errors on every build.

## Quick start

Requirements: JDK 25+ and Maven 3.9+.

```bash
./mvnw clean package
```

This builds all three modules: `juno-api/target/classes` (the hardware API),
`juno/target/juno-0.1.0-SNAPSHOT.jar` (the compiler), and `juno-examples/target/classes` (the
compiled example programs). To turn an example into a `.ino` sketch and run it on real UNO R4
hardware with `arduino-cli`, see [docs/ARDUINO.md](docs/ARDUINO.md).

## Java API

Hardware operations are normal Java native declarations at compile time and compiler intrinsics
at link time:

```java
import io.github.jabrena.juno.api.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.api.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.DigitalOutput;

@Board(ArduinoUnoR4WiFi.class)
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

See the [Javadoc](https://jabrena.github.io/juno/javadocs/0.1.0-SNAPSHOT/apidocs/index.html)
for the complete Java API reference.

For Wi-Fi connections, compile-time credentials, HTTP REST calls, and bounded JSON response
extraction, see the [Internet access guide](docs/INTERNET.md).

### Target board

`@Board` (`io.github.jabrena.juno.api.Board`) on the entry-point class selects which UNO R4 variant
Juno compiles for: `ArduinoUnoR4WiFi` or `ArduinoUnoR4Minima`. A class with no `@Board` annotation
targets the WiFi variant by default. The board gates board-specific intrinsics at link time — `LedMatrix`
requires `ArduinoUnoR4WiFi.class`, since the Minima has no onboard matrix.

## Supported Java subset

Juno deliberately fails at link time when reachable code uses something outside the current
subset. Diagnostics identify the method, bytecode offset, and unsupported opcode. See
[docs/FEATURES.md](docs/FEATURES.md) for the full, up-to-date inventory of what's supported and
what isn't.

Before generating or uploading a sketch, inspect conservative runtime-risk and resource estimates:

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar inspect \
  --main ArenaFeaturesPulse \
  --classpath juno-examples/target/classes:juno-api/target/classes \
  --risks
```

The report includes the fixed-arena budget, estimated static RAM and generated local storage,
maximum call depth, emitted array bounds checks, and stable `JUNO-RISK-*` findings for allocation
inside loops, recursion, possible integer division by zero, unchecked array access, and
compile-time-null dereferences. These are conservative source-level checks; the Arduino linker's
memory report remains authoritative for final RAM and flash use.

## Architecture

- `classfile` parses standard JVM class files and resolves constant-pool references.
- `bytecode` decodes and validates the v0.1 instruction set.
- `linker` starts at `main`, follows reachable static calls, resolves hardware intrinsics, and
  eliminates unreachable methods.
- `backend` emits standalone Arduino C++ with compact operand/local stacks. The native compiler
  can then optimize those fixed-size structures.

The above live under [`juno/src/main/java/io/github/jabrena/juno/`](juno/src/main/java/io/github/jabrena/juno).
The small Java-facing hardware abstraction lives separately, in
[`juno-api/src/main/java/io/github/jabrena/juno/api/`](juno-api/src/main/java/io/github/jabrena/juno/api).

### Experimental: Cortex-M4 assembly backend

[`CortexM4AsmBackend`](juno/src/main/java/io/github/jabrena/juno/backend/CortexM4AsmBackend.java) emits
GNU ARM (Cortex-M4) assembly straight from Juno IR — no C++ in between. Every reachable method
becomes its own function with a real AAPCS calling convention (including stack-passed arguments
beyond the first four); every value and local lives in a fixed stack-frame slot rather than a
register, so it doesn't run out of registers as a method grows. Supports branches, `switch`, `int`
arithmetic/comparisons, fixed-size arrays, arena-allocated objects with fields, mutable static
fields, GPIO/delay, `LedMatrix`, and `Serial` (the last two via a small `extern "C"` shim around
things the assembly can't call directly). Try it with `java -jar juno.jar
asm --main Blink ...`; see [docs/ARDUINO.md](docs/ARDUINO.md) for the full walkthrough, including how
far this has (and hasn't) been verified on real hardware.

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
./mvnw javadoc:aggregate
```

Generates a combined Javadoc site for `juno-api` and `juno` into `docs/javadocs/<version>/apidocs`,
e.g. `docs/javadocs/0.1.0-SNAPSHOT/apidocs/index.html` (`juno-examples` is excluded, since it's
example programs rather than library API).

## References

- https://dev.java/
- https://store.arduino.cc/products/uno-r4-wifi
- https://store.arduino.cc/products/arduino-uno-rev3
- https://store.arduino.cc/products/arduino-uno-wifi-rev2
- https://lejos.sourceforge.io/
- https://tinyvm.sourceforge.net/
- https://sunspotdev.org/
- https://sunspotdev.org/docs/index.html
