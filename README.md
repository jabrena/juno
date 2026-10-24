# JUno: Java for Arduino

JUno `/ˈxu.no/` is an ahead-of-time compiler for running a practical subset of Java on Arduino boards & M5Stack Cardputer. It keeps `javac` as the Java frontend, performs
closed-world linking on the development machine, and emits Assembly.

[![CI Builds](https://github.com/jabrena/juno/actions/workflows/maven.yaml/badge.svg)](https://github.com/jabrena/juno/actions/workflows/maven.yaml)

## Arduino boards

<table>
  <tr>
    <td align="center" width="50%">
      <strong>Arduino UNO Q</strong><br>
      <img src="./juno-site/src/main/resources/public/images/boards/arduino-uno-q.png" alt="Arduino UNO Q">
    </td>
    <td align="center" width="50%">
      <strong>Arduino UNO R4 WiFi</strong><br>
      <img src="./juno-site/src/main/resources/public/images/boards/arduino-uno-r4-wifi.png" alt="Arduino UNO R4 WiFi">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Arduino UNO R3 WiFi Rev2</strong><br>
      <img src="./juno-site/src/main/resources/public/images/boards/arduino-uno-r3-wifi-rev2.png" alt="Arduino UNO WiFi Rev2">
    </td>
    <td align="center" width="50%">
      <strong>Arduino UNO R3</strong><br>
      <img src="./juno-site/src/main/resources/public/images/boards/arduino-uno-r3.png" alt="Arduino UNO R3">
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <strong>Arduino Nano ESP32</strong><br>
      <img src="./juno-site/src/main/resources/public/images/boards/arduino-nano-esp32.png" alt="Arduino Nano ESP32">
    </td>
    <td width="50%"></td>
  </tr>
</table>

## Arduino Shields

<table>
  <thead>
    <tr>
      <th>Shield</th>
      <th>Image</th>
      <th>Supported features</th>
    </tr>
  </thead>
  <tbody>
  <tr>
    <td><strong>LCD Keypad Shield</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-lcd.png" alt="LCD Keypad Shield" width="220"></td>
    <td>16x2 HD44780 character display, five-button keypad, and controllable backlight.</td>
  </tr>
  <tr>
    <td><strong>ELEGOO 2.8-inch TFT Touch Shield</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-tft.png" alt="ELEGOO 2.8-inch TFT Touch Shield" width="220"></td>
    <td>240x320 ILI9341 display, resistive touch input, and an onboard microSD socket.</td>
  </tr>
  <tr>
    <td><strong>Arduino 9 Axis Motion Shield</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-nine-axis.png" alt="Arduino 9 Axis Motion Shield" width="220"></td>
    <td>BNO055 heading, pitch, roll, gyroscope, and calibration data over I2C.</td>
  </tr>
  <tr>
    <td><strong>PCA9685 PWM &amp; Servo Shield</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-pwm-servo.png" alt="PCA9685 PWM and Servo Shield" width="220"></td>
    <td>Sixteen independently controlled 12-bit PWM/servo channels over I2C.</td>
  </tr>
  <tr>
    <td><strong>AZDelivery Data Logger Shield</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-data-logger.png" alt="AZDelivery Data Logger Shield" width="220"></td>
    <td>SPI SD-card access. The shield's real-time clock is not currently exposed.</td>
  </tr>
  <tr>
    <td><strong>Grove Base Shield</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-grove.png" alt="Grove Base Shield" width="220"></td>
    <td>Grove digital, analog, I2C, and UART connectors.</td>
  </tr>
  <tr>
    <td><strong>Arduino WiFi Shield</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-wifi.png" alt="Arduino WiFi Shield" width="220"></td>
    <td>Wi-Fi, UDP, TCP, plain HTTP and the SNTP clock on the UNO R3 (shield firmware 1.1.0). No access point or HTTPS.</td>
  </tr>
  <tr>
    <td><strong>Arduino Nano Connector Carrier</strong></td>
    <td align="center"><img src="./juno-site/src/main/resources/public/images/shields/shield-nano-connector-carrier.png" alt="Arduino Nano Connector Carrier" width="220"></td>
    <td>Passive carrier for the Arduino Nano ESP32: microSD card slot on SPI, four Grove sockets, and one Qwiic connector.</td>
  </tr>
  </tbody>
</table>

## M5Stack board

<table>
  <tr>
    <td align="center" width="50%">
      <strong>M5Stack Cardputer Adv</strong><br>
      <img src="./juno-site/src/main/resources/public/images/boards/m5stack-cardputer.png" alt="M5Stack Cardputer">
    </td>
    <td width="50%"></td>
  </tr>
</table>

## Quick start

Requirements: `Arduino CLI`, `JDK 27+`, `Maven 3.10+`.

On macOS, install Arduino CLI with [Homebrew](https://brew.sh/) and verify that it is on your `PATH`:

```bash
brew update
brew install arduino-cli
arduino-cli version
```

See Arduino's [installation guide](https://docs.arduino.cc/arduino-cli/installation/) for direct downloads and other options.

```bash
sdk env install
./mvnw clean install
```

### Blink

[`Blink`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) runs unmodified on every
supported board. Its built-in LED alternates on and off every 500 ms (one full cycle per second).

```java
package io.github.jabrena.juno.api.hardware.gpio;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

public final class Blink {

    public static void main(String[] args) {

        DigitalOutput led = DigitalOutput.of(ArduinoUno.getGpio().getBuiltinLed());

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
`Blink` has no board restriction, so pick a target with `-Djuno.board`:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-q
```

Select another supported board with `-Djuno.board`. The plugin auto-detects the port when exactly one matching board is
connected; otherwise, specify it with `-Djuno.port=<PORT>`.

After uploading [`M5CardputerBatteryLevel`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/battery/M5CardputerBatteryLevel.java),
open the serial monitor with:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=m5stack-cardputer
```

It prints the estimated battery level once per second:

```text
Battery level: 100%
Battery level: 100%
Battery level: 100%
```
## Maven plugin

The `juno` Maven plugin provides these goals. The commands below run them from the examples module;
add `-Djuno.main=<fully-qualified-class-name>` and `-Djuno.board=<id>` when the entry point has no single-board
`@Board` declaration.

<table>
  <thead>
    <tr>
      <th>Goal</th>
      <th>Purpose</th>
      <th>Command</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>juno:boards</code></td>
      <td>Lists the boards and serial ports detected by Arduino CLI without touching a board.</td>
      <td><code>./mvnw -f juno-examples/pom.xml juno:boards</code></td>
    </tr>
    <tr>
      <td><code>juno:install-deps</code></td>
      <td>Installs the Arduino core and optional libraries required by the examples.</td>
      <td><code>./mvnw -f juno-examples/pom.xml juno:install-deps</code></td>
    </tr>
    <tr>
      <td><code>juno:compile</code></td>
      <td>Generates the assembly, C++ runtime shim, and Arduino sketch wrapper.</td>
      <td><code>./mvnw -f juno-examples/pom.xml compile juno:compile -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink -Djuno.board=arduino-uno-r4-wifi</code></td>
    </tr>
    <tr>
      <td><code>juno:verify</code></td>
      <td>Generates the sketch and compiles it with Arduino CLI without using physical hardware.</td>
      <td><code>./mvnw -f juno-examples/pom.xml compile juno:verify -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink -Djuno.board=arduino-uno-r4-wifi</code></td>
    </tr>
    <tr>
      <td><code>juno:upload</code></td>
      <td>Generates, verifies, and uploads the sketch to the selected or detected board.</td>
      <td><code>./mvnw -f juno-examples/pom.xml compile juno:upload -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink -Djuno.board=arduino-uno-r4-wifi</code></td>
    </tr>
    <tr>
      <td><code>juno:monitor</code></td>
      <td>Opens the serial monitor for the selected or detected board.</td>
      <td><code>./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi</code></td>
    </tr>
  </tbody>
</table>

For example, verify the Grove Temperature and Humidity Sensor Pro monitor for a UNO WiFi Rev2
without uploading it:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.device.grove.sensors.GroveDht22Monitor \
  -Djuno.board=arduino-uno-r3-wifi-rev2
```

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
  [`HelloWorld`](juno-examples/src/main/java/io/github/jabrena/juno/language/HelloWorld.java) and
  [`Methods`](juno-examples/src/main/java/io/github/jabrena/juno/language/Methods.java).
- **Primitive types:** `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, and `double`.
  The example prints each type's size and range using `javac`-folded `BYTES`, `MIN_VALUE`, and
  `MAX_VALUE` constants. See
  [`PrimitiveTypes`](juno-examples/src/main/java/io/github/jabrena/juno/language/PrimitiveTypes.java).
- **Values and control flow:** constants, mutable static fields, local variables, arithmetic,
  bitwise and shift operations, comparisons, conditionals, `switch`, and loops. See
  [`Variables`](juno-examples/src/main/java/io/github/jabrena/juno/language/Variables.java),
  [`Operators`](juno-examples/src/main/java/io/github/jabrena/juno/language/Operators.java), and
  [`ControlFlow`](juno-examples/src/main/java/io/github/jabrena/juno/language/ControlFlow.java).
- **Arrays:** fixed-size primitive arrays (`boolean` through `double`, including fixed-size
  multidimensional arrays) allocated from the fixed 8 KiB arena, and `static final` read-only lookup
  tables with an all-constant initializer, kept in flash instead of RAM. See
  [`Arrays`](juno-examples/src/main/java/io/github/jabrena/juno/language/Arrays.java).
- **Strings:** string literals, `String.valueOf(int)`, `length()`, and `charAt(int)` for runtime
  string references, `equals`, plus `+` concatenation of strings and a `StringBuilder`. See
  [`Strings`](juno-examples/src/main/java/io/github/jabrena/juno/language/Strings.java).
- **BigInteger and BigDecimal:** arbitrary-precision `java.math.BigInteger` and `BigDecimal`, with
  `MathContext` and the `RoundingMode` constants. Arithmetic, `pow`, `sqrt`, division by scale,
  `RoundingMode` or `MathContext`, `setScale`, `round`, comparisons, conversions and `toString` /
  `toPlainString` follow the JDK's results, scales and rounding. Values are immutable blocks in the
  8 KiB arena and are garbage-collected, so precision is limited by memory, not by a fixed width. See
  [`ScopedValuesPrecision`](juno-examples/src/main/java/io/github/jabrena/juno/language/ScopedValuesPrecision.java).
- **LocalDateTime:** `now()`, `MIN`, the date and time getters, `equals` and ISO-8601 `toString`. `now()`
  gets UTC from an SNTP server once Wi-Fi is up (`JUNO_SNTP_HOST`, default `pool.ntp.org`;
  `JUNO_SNTP_PORT`, default `123`) and returns `LocalDateTime.MIN` while it is unavailable. Works on every
  Wi-Fi board, including the UNO R3 with the Arduino WiFi Shield. See
  [`SntpTime`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTime.java) and
  [`SntpTimeSD`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTimeSD.java)
  (server read from the SD card).
- **UUID:** `java.util.UUID` construction, parsing, formatting, comparison and 16-byte conversion through
  `UuidCodec`. `UUID.randomUUID()` uses the board's hardware random source on the UNO R4 WiFi, UNO WiFi Rev2
  and UNO Q; the UNO R3 has none, so it is rejected there.
- **Objects and interfaces:** simple enums, records, final closed-world classes, constructors, instance
  fields, and directly implemented interfaces with closed-world dispatch through an interface reference.
  Objects, records, and capturing closures share the fixed 8 KiB arena, which uses conservative garbage
  collection. See
  [`Objects`](juno-examples/src/main/java/io/github/jabrena/juno/language/Objects.java) and
  [`Interfaces`](juno-examples/src/main/java/io/github/jabrena/juno/language/Interfaces.java).
- **Lambdas and method references:** non-capturing and capturing lambdas, plus static, bound,
  unbound, and constructor references emitted through `LambdaMetafactory.metafactory`. See
  [`Lambdas`](juno-examples/src/main/java/io/github/jabrena/juno/language/Lambdas.java).
- **Math and random numbers:** the documented `java.lang.Math` subset and Arduino-backed bounded
  pseudorandom numbers. See
  [`MathFunctionsSinglePrecision`](juno-examples/src/main/java/io/github/jabrena/juno/language/MathFunctionsSinglePrecision.java),
  [`MathFunctionsDoublePrecision`](juno-examples/src/main/java/io/github/jabrena/juno/language/MathFunctionsDoublePrecision.java) and
  [`RandomNumbers`](juno-examples/src/main/java/io/github/jabrena/juno/language/RandomNumbers.java).
- **Exceptions:** `throw`, `try`/`catch` (including multi-catch and catching by a supertype),
  `finally`, nested handlers, propagation between methods, custom final exception classes, and
  try-with-resources. See
  [`Exceptions`](juno-examples/src/main/java/io/github/jabrena/juno/language/Exceptions.java).
- **Concurrency:** preemptive virtual threads (`Thread.startVirtualThread`, `join`, `isAlive`) and the
  supported JDK 27 preview `StructuredTaskScope` subset with cooperative subtasks (up to four threads including `main`, with
  `Thread.sleep` and `Thread.yield`), restricted monitors and `ReentrantLock`, and `ScopedValue` bindings
  inherited by forked subtasks. Platform threads (`new Thread`) and `Thread.ofVirtual()` builders are
  rejected at compile time; `AtomicInteger`, `AtomicBoolean` and `AtomicLong` are available. See
  [`Synchronization`](juno-examples/src/main/java/io/github/jabrena/juno/language/Synchronization.java),
  [`VirtualThreads`](juno-examples/src/main/java/io/github/jabrena/juno/language/VirtualThreads.java),
  [`StructuredConcurrency`](juno-examples/src/main/java/io/github/jabrena/juno/language/StructuredConcurrency.java) (subtasks owned by a scope that is joined and closed before the owner reads their results), and
  [`ScopedValuesPrecision`](juno-examples/src/main/java/io/github/jabrena/juno/language/ScopedValuesPrecision.java)
  (a scoped `MathContext` shared by three parallel subtasks).
- **Board services:** GPIO, clocks and delays, serial I/O, LED matrices, mouse input, Wi-Fi,
  HTTP/HTTPS, bounded JSON inspection, and read-only SPI SD-card files through Juno's intrinsic APIs.
  See [`Blink`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) (GPIO and delays),
  [`SerialCounter`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialCounter.java) (serial I/O),
  [`HttpServerStatus`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpServerStatus.java) (clocks),
  [`LedMatrixHeart`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/led/LedMatrixHeart.java) (LED matrix),
  [`WifiStatus`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatus.java) (Wi-Fi),
  [`WifiStatusSD`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatusSD.java) (Wi-Fi credentials loaded from an SD card),
  [`WifiStatusSDLcd`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatusSDLcd.java) (the same, with an LCD Keypad Shield),
  [`WifiProvisioning`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/WifiProvisioning.java) (access point and HTTP server to provide Wi-Fi credentials),
  [`HttpsMethods`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/HttpsMethods.java) (HTTP/HTTPS and JSON),
  [`Miner2`](juno-examples/src/main/java/io/github/jabrena/juno/usecases/miner/Miner2.java) (UDP between boards:
  factor numbers together and chain the verified answers as a leaderless, Bitcoin-style proof-of-work chain), and
  [`SdFileOperations`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java) (SD card).
- **Shields:** stack a shield on the board as designed, with no rewiring, and use it through Juno's APIs;
  a combination whose shields share pins, such as the LCD Keypad Shield on a UNO R3 with the Arduino WiFi
  Shield, is rejected at compile time. The [Grove Base Shield](https://jabrena.github.io/juno/grove) brings
  Grove connectors on the UNO pin map, as shown by
  [`GroveDht22Monitor`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/sensors/GroveDht22Monitor.java).
  The [LCD Keypad Shield](https://jabrena.github.io/juno/lcd-keypad-shield) (16x2 display, buttons,
  backlight) is shown in
  [`LcdKeypadDemo`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/lcd/LcdKeypadDemo.java).
  The [ELEGOO 2.8" TFT touch shield](https://jabrena.github.io/juno/tft-touch-shield) (display, touch,
  microSD) is shown in
  [`TouchPaintTFT`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/tft/TouchPaintTFT.java) and
  [`WifiStatusSDTFT`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatusSDTFT.java).
  The [AZDelivery Data Logger Shield](https://jabrena.github.io/juno/storage) provides the SD card for
  [`WifiStatusSD`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/WifiStatusSD.java).
  The Arduino WiFi Shield brings Wi-Fi, UDP, TCP, plain HTTP and the SNTP clock to the UNO R3, as shown by
  [`UdpEcho`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/UdpEcho.java) and
  [`PlainHttpWeather`](juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/PlainHttpWeather.java).

## Maven modules

This is a multi-module Maven build:

- [`juno-api/`](juno-api) — the programming model: the Java-facing hardware API the compiler
  recognizes as intrinsics (`api/`) and the `@Board` annotations used to select a compilation
  target (`annotations/`). This is the only artifact programs compile against.
- [`juno-compiler/`](juno-compiler) — the compiler (`classfile`, `bytecode`, `linker`, `backend`, …),
  which depends on `juno-api`. Builds the executable `juno-compiler/target/juno-compiler-<version>.jar`.
- [`juno-maven-plugin/`](juno-maven-plugin) — Maven goals for generating a sketch (`juno:compile`),
  compiling it with Arduino CLI (`juno:verify`), uploading it (`juno:upload`), and opening the
  serial monitor (`juno:monitor`).
- [`juno-examples/`](juno-examples) — example Java programs written against `juno-api`, built
  like any other Maven module so they're checked for compile errors on every build.
- [`juno-site/`](juno-site) — the [Roq](https://iamroq.dev)/Quarkus static site generator behind
  the [documentation site](https://jabrena.github.io/juno/). `docs/` is entirely generated from
  Markdown under `juno-site/src/main/resources/content/` — see
  [Documentation site](#documentation-site) below to regenerate it.
- [`juno-third-parties/`](juno-third-parties) — integrations maintained outside the core compiler,
  currently including the independent H8/300 firmware generator for the LEGO RCX, its S-record,
  and the generated Java representation consumed by `juno-examples`.

## Architecture

Juno is an ahead-of-time compiler, not a virtual machine. An Arduino board has no JVM, so nothing is
interpreted at run time: the Java program is compiled with `javac` as usual, and Juno then translates the
resulting JVM bytecode into native ARM assembly that the Arduino toolchain builds into the sketch's firmware.
The compiler is *closed-world*: it only sees the classes reachable from `main`, so it can resolve every call
at build time, drop everything unused, and reject anything outside the supported subset before generating a
single instruction. That is also why there is no dynamic class loading, reflection, or general heap.

### From Java to a running board

```text
 Hello.java ──javac──▶ Hello.class ──▶ Juno ──▶ Hello.S + HelloShim.cpp + HelloAsm.ino ──arduino-cli──▶ board
                                       │
                 classfile → bytecode → linker → lowering → optimize → backend
```

1. **Write and compile Java.** Programs use the Juno hardware API and optionally select a board with `@Board`.
   Maven runs `javac`; Juno consumes the resulting `.class` files.
2. **Link and validate.** Juno starts at `main`, keeps only reachable code, converts hardware calls into
   intrinsics, and rejects unsupported bytecode with a precise diagnostic.
3. **Lower and optimize.** Reachable methods become Juno IR, where compiler passes simplify the program and
   report risks such as excessive arena use, recursion, or unsafe array access.
4. **Generate the sketch.** The backend emits native assembly and a small C++ runtime shim. The Maven plugin
   packages them with a minimal `.ino` under `target/juno/<Main>Asm/`.
5. **Build and run.** Arduino CLI compiles the generated sketch for the selected board. Use `juno:verify` to
   compile it, `juno:upload` to flash it, and `juno:monitor` to read its serial output.

## References

- https://dev.java/
- https://docs.oracle.com/javase/specs/jls/se25/html/index.html
- https://docs.oracle.com/en/java/javase/27/docs/api/index.html
- https://github.com/openjdk/jcstress/tree/master/jcstress-samples
- https://docs.arduino.cc/hardware/uno-q/
- https://docs.arduino.cc/hardware/uno-r4-wifi/
- https://docs.arduino.cc/hardware/uno-wifi-rev2/
- https://docs.arduino.cc/hardware/uno-rev3/
- https://docs.arduino.cc/hardware/nano-esp32/
- https://lejos.sourceforge.io/
- https://lejos.sourceforge.io/ev3/docs/index.html
- https://lejos.sourceforge.io/nxt/nxj/api/index.html
- https://lejos.sourceforge.io/rcx/api/index.html
- https://pbrick.info/index.html-p=74.html
- https://tinyvm.sourceforge.net/
- https://sunspotdev.org/
- https://sunspotdev.org/docs/index.html
