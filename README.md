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

See the [Javadoc](https://jabrena.github.io/juno/javadocs/0.1.0-SNAPSHOT/apidocs/index.html)
for the complete Java API reference.

## Supported Java subset

Juno deliberately fails at link time when reachable code uses something outside the current
subset. Diagnostics identify the method, bytecode offset, and unsupported opcode.

Supported today:

- `static void main(String[])` and the embedded-friendly `static void main()`
- static methods with `boolean`, `byte`, `char`, `short`, and `int` arguments/results
- local variables, integer constants, arithmetic, bitwise operations, shifts, comparisons,
  conditionals, and loops
- `private/static final` primitive constants (`boolean`/`byte`/`char`/`short`/`int`) initialized
  with a compile-time constant expression, same class or a different one — `javac` inlines these
  as ordinary literals (JLS 4.12.4), so Juno never sees a field read
- direct static calls with closed-world reachability; unused methods are omitted
- opaque `DigitalOutput` handles which compile down to integer pin numbers without heap allocation
- Java-compatible 32-bit wrapping arithmetic and divide-overflow behavior
- `.class` inputs from directories, individual files, or JARs

Not yet supported:

- general objects, constructors, instance/virtual/interface calls, or arrays used by application code
- mutable/non-constant static fields (needs `getstatic`/`putstatic`), strings, exceptions, garbage
  collection, threads, reflection, or dynamic loading
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

- https://dev.java/
- https://store.arduino.cc/products/uno-r4-wifi
- https://sunspotdev.org/
- https://sunspotdev.org/docs/index.html
