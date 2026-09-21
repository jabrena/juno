# Juno: Java for Arduino

Juno is an ahead-of-time compiler for running a practical subset of Java on the Arduino UNO R4 WiFi. It keeps `javac` as the Java frontend, performs
closed-world linking on the development machine, and emits an Arduino C++ sketch which the
Renesas toolchain compiles to native Cortex-M4 code.

![](./documentation/boards/arduino-one-r4-wifi.png)

This repository contains a working v0.1 compiler, not a JVM interpreter. The first milestone
supports 32-bit integer code, static methods, branches, loops, and GPIO/time intrinsics. It can
compile [`juno-examples/src/main/java/io/github/jabrena/juno/api/Blink.java`](juno-examples/src/main/java/io/github/jabrena/juno/api/Blink.java) all the way to a
`.ino` sketch.

```text
Java source -> javac -> .class -> Juno linker/AOT -> .ino -> Arduino toolchain -> RA4M1
```

## Modules

This is a multi-module Maven build:

- [`juno/`](juno) — the Juno compiler itself (`classfile`, `bytecode`, `linker`, `backend`, …),
  plus the small Java-facing hardware API Juno recognizes as compiler intrinsics
  (`io.github.jabrena.juno.api`: `Delay`, `Clock`, `LedMatrix`, `api.io.Gpio`,
  `api.io.DigitalOutput`, `api.io.hid.Mouse`, `api.io.usb.Serial`, …) and the
  `io.github.jabrena.juno.annotations` package
  (`Board`, `ArduinoBoard`, `ArduinoUnoR4WiFi`) that entry-point classes use to select a
  compilation target. Builds `juno/target/juno-<version>.jar`, an executable jar whose main class
  is `io.github.jabrena.juno.Main`.
- [`juno-maven-plugin/`](juno-maven-plugin) — Maven goals for generating a sketch (`juno:compile`),
  compiling it with Arduino CLI (`juno:verify`), uploading it (`juno:upload`), and opening the
  serial monitor (`juno:monitor`). The experimental Cortex-M4 ASM backend is the default; select
  the Arduino C++ backend with `-Djuno.backend=cpp`.
- [`juno-examples/`](juno-examples) — example Java programs written against `juno`'s `api` and
  `annotations` packages, compiled by Maven like any other Java module
  (`juno-examples/target/classes`) so they are checked for compile errors on every build.

## Quick start

Requirements: JDK 25+ and Maven 3.9+.

```bash
./mvnw clean install
```

This builds all three modules: `juno/target/juno-0.1.0-SNAPSHOT.jar` (the compiler and hardware
API), `juno-maven-plugin/target/juno-maven-plugin-0.1.0-SNAPSHOT.jar` (the Maven integration), and
`juno-examples/target/classes` (the compiled example programs). To turn an example into a
`.ino` sketch and run it on real UNO R4 hardware with `arduino-cli`, see
[docs/ARDUINO.md](docs/ARDUINO.md).

Use the following commands for the complete `Blink` workflow:

```bash
# Default ASM backend
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.Blink

# C++ backend
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.Blink \
  -Djuno.backend=cpp

# Upload
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.Blink

# Monitor
./mvnw -f juno-examples/pom.xml juno:monitor
```

Upload and monitor auto-detect the port when exactly one matching board is connected. Otherwise,
select it with `-Djuno.port=<PORT>`. Select another example with
`-Djuno.main=<fully-qualified-class-name>`.

## Java API

Hardware operations are normal Java native declarations at compile time and compiler intrinsics
at link time:

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.DigitalOutput;

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

For Wi-Fi connections, compile-time credentials, HTTP/HTTPS REST calls, and bounded JSON response
extraction, see the [Internet access guide](docs/INTERNET.md).

### Target board

`@Board` (`io.github.jabrena.juno.annotations.Board`) on the entry-point class selects which UNO R4 variant
Juno compiles for: `ArduinoUnoR4WiFi` (currently the only supported target). A class with no
`@Board` annotation targets it by default.

## Supported Java subset

Juno deliberately fails at link time when reachable code uses something outside the current
subset. Diagnostics identify the method, bytecode offset, and unsupported opcode. See
[docs/FEATURES.md](docs/FEATURES.md) for the full, up-to-date inventory of what's supported and
what isn't.

Before generating or uploading a sketch, inspect conservative runtime-risk and resource estimates:

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar inspect \
  --main ArenaFeaturesPulse \
  --classpath juno-examples/target/classes:juno/target/classes \
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

The above, along with the small Java-facing hardware abstraction (`api/`) and the `@Board`
selection types (`annotations/`), live under
[`juno/src/main/java/io/github/jabrena/juno/`](juno/src/main/java/io/github/jabrena/juno).

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

Generates a Javadoc site for `juno` into `docs/javadocs/<version>/apidocs`,
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
