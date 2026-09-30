# Juno: Java for Arduino

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

[`Blink`](juno-examples/src/main/java/io/github/jabrena/juno/api/Blink.java) runs on either board
unmodified: expect the UNO R4 WiFi's built-in LED, or the UNO Q's red status LED, to alternate on
and off every 500 ms (one full cycle per second).

```java
package io.github.jabrena.juno.api;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.io.Gpio;

@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
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

`juno:upload` generates the sketch, verifies it with `arduino-cli`, and flashes it in one step.
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

Juno deliberately fails at link time when reachable code uses something outside the current
subset. Diagnostics identify the method, bytecode offset, and unsupported opcode. See
the [Feature Inventory](https://jabrena.github.io/juno/features) for the full, up-to-date
inventory of what's supported and what isn't.
[`UnsupportedFeature`](juno-examples/src/main/java/io/github/jabrena/juno/UnsupportedFeature.java)
compiles fine with plain `javac` — string concatenation is ordinary Java — but fails `juno:compile`
because `+` on a `String` lowers to `invokedynamic`, an opcode Juno doesn't accept:

```bash
./mvnw -f juno-examples/pom.xml compile juno:compile -Djuno.main=io.github.jabrena.juno.UnsupportedFeature

./mvnw -f juno-examples/pom.xml compile juno:compile -Djuno.main=io.github.jabrena.juno.MathFunctions

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
- https://github.com/mobizt/ESP_SSLClient
