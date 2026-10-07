# Juno: Java for Arduino One

Juno is an ahead-of-time compiler for running a practical subset of Java on the Arduino UNO R4 WiFi
and the Arduino UNO Q. It keeps `javac` as the Java frontend, performs
closed-world linking on the development machine, and emits GNU ARM (Cortex-M4, Thumb-2) assembly.

**Arduino UNO Q**

![](./documentation/boards/arduino-one-q.png)

**Arduino UNO R4 WiFi**

![](./documentation/boards/arduino-one-r4-wifi.png)

## Modules

This is a multi-module Maven build:

- [`juno/`](juno) — the compiler (`classfile`, `bytecode`, `linker`, `backend`, …), plus the
  Java-facing hardware API it recognizes as intrinsics and the `@Board` annotations used to select
  a compilation target. Builds the executable `juno/target/juno-<version>.jar`.
- [`juno-maven-plugin/`](juno-maven-plugin) — Maven goals for generating a sketch (`juno:compile`),
  compiling it with Arduino CLI (`juno:verify`), uploading it (`juno:upload`), and opening the
  serial monitor (`juno:monitor`).
- [`juno-examples/`](juno-examples) — example Java programs written against `juno`'s API, built
  like any other Maven module so they're checked for compile errors on every build.
- [`juno-site/`](juno-site) — the [Roq](https://iamroq.dev)/Quarkus static site generator behind
  the [documentation site](https://jabrena.github.io/juno/). `docs/` is entirely generated from
  Markdown under `juno-site/src/main/resources/content/` — see
  [Documentation site](#documentation-site) below to regenerate it.

## Quick start

Requirements: JDK 25+ and Maven 3.9+.

```bash
./mvnw clean install
```

### Blink

[`Blink`](juno-examples/src/main/java/io/github/jabrena/juno/api/Blink.java) runs on either board
unmodified: expect the UNO R4 WiFi's built-in LED, or the UNO Q's red status LED, to alternate on
and off every 500 ms (one full cycle per second).

```java
package io.github.jabrena.juno.api;

import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.io.Gpio;

public final class Blink {

    public static void main(String[] args) {

        DigitalOutput led = DigitalOutput.of(Gpio.builtinLed());

        while (true) {
            led.high();
            Delay.millis(500);

            led.low();
            Delay.millis(500);
        }
    }
}
```

The project provides a `Maven plugin` to help the user with the common operations. `juno:upload` generates the sketch, verifies it with `arduino-cli`, and flashes it in one step.
`Blink` targets both boards, so pick one with `-Djuno.board`:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.Blink \
  -Djuno.board=arduino-uno-q

./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

It auto-detects the port when exactly one matching board is connected. Otherwise, select it with
`-Djuno.port=<PORT>`. Select another example with `-Djuno.main=<fully-qualified-class-name>`.

## Supported Java subset

Juno is an ahead-of-time compiler, not a JVM, so it intentionally supports a focused, predictable
subset of Java that can be translated into native code for resource-constrained Arduino boards.
Programs are first compiled normally with `javac`; Juno then performs closed-world linking from
`main`, follows every reachable method, and validates the bytecode before generating the sketch.

Valid Java is therefore not automatically valid Juno input. If reachable code needs an unsupported
bytecode instruction, JDK feature, or `invokedynamic` bootstrap, compilation stops instead of
emitting code with uncertain behavior. The diagnostic identifies the method, bytecode offset, and
unsupported operation or bootstrap. The currently supported Java subset includes:

- **Entrypoints and methods:** `static void main(String[])`, the embedded-friendly
  `static void main()`, static methods with primitive arguments and results, direct closed-world
  calls, and removal of unreachable methods. See
  [`HelloWorld`](juno-examples/src/main/java/io/github/jabrena/juno/HelloWorld.java) and
  [`Methods`](juno-examples/src/main/java/io/github/jabrena/juno/Methods.java).
- **Values and control flow:** primitive values (including `long`, `float`, and `double`), constants,
  mutable static fields, local variables, arithmetic, bitwise and shift operations, comparisons,
  conditionals, `switch`, and loops. See
  [`Variables`](juno-examples/src/main/java/io/github/jabrena/juno/Variables.java),
  [`DataTypes`](juno-examples/src/main/java/io/github/jabrena/juno/DataTypes.java),
  [`Operators`](juno-examples/src/main/java/io/github/jabrena/juno/Operators.java), and
  [`ControlFlow`](juno-examples/src/main/java/io/github/jabrena/juno/ControlFlow.java).
- **Arrays:** fixed-size primitive arrays (`boolean` through `double`, including fixed-size
  multidimensional arrays) allocated from the fixed 8 KiB arena. See
  [`Arrays`](juno-examples/src/main/java/io/github/jabrena/juno/Arrays.java).
- **Strings:** string literals, `String.valueOf(int)`, `length()`, and `charAt(int)` for runtime
  string references, plus `+` concatenation of strings.
- **Objects:** simple enums, records, final closed-world classes, constructors, and instance fields.
  Objects, records, and capturing closures share the fixed 8 KiB arena, which uses conservative
  garbage collection. See
  [`Objects`](juno-examples/src/main/java/io/github/jabrena/juno/Objects.java).
- **Interfaces:** directly implemented interfaces with closed-world dispatch through an interface
  reference. See
  [`Interfaces`](juno-examples/src/main/java/io/github/jabrena/juno/Interfaces.java).
- **Lambdas and method references:** non-capturing and capturing lambdas, plus static, bound,
  unbound, and constructor references emitted through `LambdaMetafactory.metafactory`. See
  [`Lambdas`](juno-examples/src/main/java/io/github/jabrena/juno/Lambdas.java).
- **Math and random numbers:** the documented `java.lang.Math` subset and Arduino-backed bounded
  pseudorandom numbers. See
  [`MathFunctions`](juno-examples/src/main/java/io/github/jabrena/juno/MathFunctions.java) and
  [`RandomNumbers`](juno-examples/src/main/java/io/github/jabrena/juno/RandomNumbers.java).
- **Exceptions:** `throw`, `try`/`catch` (including multi-catch and catching by a supertype),
  `finally`, nested handlers, propagation between methods, custom final exception classes, and
  try-with-resources. See
  [`Exceptions`](juno-examples/src/main/java/io/github/jabrena/juno/Exceptions.java).
- **Concurrency:** up to four threads including `main`, `Runnable`, `start()`, `join()`,
  sleeping, yielding, daemon threads, restricted monitors and `ReentrantLock`, and the supported JDK
  25 preview `StructuredTaskScope` subset. See
  [`Threads`](juno-examples/src/main/java/io/github/jabrena/juno/Threads.java),
  [`Synchronization`](juno-examples/src/main/java/io/github/jabrena/juno/Synchronization.java), and
  [`StructuredConcurrency`](juno-examples/src/main/java/io/github/jabrena/juno/StructuredConcurrency.java).
- **Board services:** GPIO, clocks and delays, serial I/O, LED matrices, mouse input, Wi-Fi,
  HTTP/HTTPS, bounded JSON inspection, and read-only SPI SD-card files through Juno's intrinsic APIs.
  See [`Blink`](juno-examples/src/main/java/io/github/jabrena/juno/api/Blink.java) (GPIO and delays),
  [`SerialCounter`](juno-examples/src/main/java/io/github/jabrena/juno/api/io/usb/SerialCounter.java) (serial I/O),
  [`HttpServerStatus`](juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/http/HttpServerStatus.java) (clocks),
  [`LedMatrixHeart`](juno-examples/src/main/java/io/github/jabrena/juno/api/led/LedMatrixHeart.java) (LED matrix),
  [`WifiStatus`](juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/WifiStatus.java) (Wi-Fi),
  [`HttpsMethods`](juno-examples/src/main/java/io/github/jabrena/juno/api/io/net/http/HttpsMethods.java) (HTTP/HTTPS and JSON), and
  [`SdFileOperations`](juno-examples/src/main/java/io/github/jabrena/juno/api/io/storage/SdFileOperations.java) (SD card).
- **Compile-time safety checks:** deterministic closed-world linking and runtime-risk warnings for
  loop allocations, arena pressure, recursion, unchecked array access, possible division by zero,
  and proven-null dereferences.

For example,
[`UnsupportedFeature`](juno-examples/src/main/java/io/github/jabrena/juno/UnsupportedFeature.java)
compiles with plain `javac`, but it deliberately fails `juno:compile`: its generated record
`toString()` uses the unsupported `ObjectMethods` bootstrap.

```bash
# Expected to fail with an unsupported-bootstrap diagnostic
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.UnsupportedFeature
```

Compile any supported example by passing its fully qualified class name. These examples all come
from the `io.github.jabrena.juno` package. Examples whose `@Board` lists both boards need
`-Djuno.board`:

```bash
# Entrypoints and methods
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.HelloWorld

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Methods

# Values and control flow
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Variables

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.DataTypes

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Operators

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.ControlFlow

# Arrays
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Arrays

# Objects and interfaces
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Objects

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Interfaces \
  -Djuno.board=arduino-uno-r4-wifi

# Lambdas and method references
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Lambdas \
  -Djuno.board=arduino-uno-r4-wifi

# Math and random numbers
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.MathFunctions

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.RandomNumbers

# Exceptions
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Exceptions

# Concurrency
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Threads \
  -Djuno.board=arduino-uno-r4-wifi

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.Synchronization \
  -Djuno.board=arduino-uno-r4-wifi

./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.StructuredConcurrency \
  -Djuno.board=arduino-uno-r4-wifi
```

Examples in subpackages use the same command. A board-specific program can also select its target
explicitly:

```bash
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.games.chess.Chess \
  -Djuno.board=arduino-uno-r4-wifi
```

## Architecture

- `classfile` parses standard JVM class files and resolves constant-pool references.
- `bytecode` decodes and validates the v0.1 instruction set.
- `linker` starts at `main`, follows reachable static calls, resolves hardware intrinsics, and
  eliminates unreachable methods.
- `backend` emits GNU ARM (Cortex-M4, Thumb-2) assembly directly from Juno's IR, plus a small
  `extern "C"` C++ runtime shim (GPIO/Serial/LED matrix/Wi-Fi/HTTP/JSON helpers, the arena
  allocator, `long`/`float`/`double` support) that the generated assembly calls into.

The above, along with the small Java-facing hardware abstraction (`api/`) and the `@Board`
selection types (`annotations/`), live under
[`juno/src/main/java/io/github/jabrena/juno/`](juno/src/main/java/io/github/jabrena/juno).

## Development

```bash
./mvnw clean test
./mvnw clean verify
./mvnw -f juno-examples/pom.xml -Parduino-cli verify
./mvnw javadoc:aggregate
./mvnw clean verify -Psite
./mvnw -f juno-site/pom.xml quarkus:dev
./mvnw -f juno-site/pom.xml clean package quarkus:run -Dquarkus.http.root-path=/
jwebserver -d "$(pwd)/juno-site/target/roq" -p 8000
./mvnw clean verify -Pcyclomatic-complexity
./mvnw -Pcyclomatic-complexity -DskipTests site
jwebserver -d "$(pwd)/target/site" -p 8000
```

## References

- https://dev.java/
- https://docs.oracle.com/javase/specs/jls/se25/html/index.html
- https://store.arduino.cc/products/uno-r4-wifi
- https://store.arduino.cc/products/arduino-uno-rev3
- https://store.arduino.cc/products/arduino-uno-wifi-rev2
- https://lejos.sourceforge.io/
- https://lejos.sourceforge.io/nxt/nxj/api/index.html
- https://tinyvm.sourceforge.net/
- https://haiku-vm.sourceforge.net/
- https://sunspotdev.org/
- https://sunspotdev.org/docs/index.html
- https://github.com/dzindra/lcdkeypad
- https://github.com/arduino-libraries/Mouse
- https://github.com/arduino-libraries/ArduinoBLE
- https://lego.github.io/lego-ble-wireless-protocol-docs/
- https://github.com/mobizt/ESP_SSLClient
