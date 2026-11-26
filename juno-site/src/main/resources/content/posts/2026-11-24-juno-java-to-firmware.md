---
title: "From Java source to Arduino firmware: how Juno compiles a program"
description: "Follow a Juno program from javac through class-file analysis, generated assembly, native shims and the Arduino toolchain."
date: 2026-11-23
layout: post
link: /blog/2026-11-24-juno-java-to-firmware/
tags: [toolchain]
blogVisible: true
---

Juno does not put a JVM on an Arduino. It compiles a closed Java program ahead of time into board-specific machine
code and a small native support layer. The result is an ordinary Arduino firmware image, built by the same board
toolchain used for C++ sketches.

The pipeline is:

```text
Java source -> javac class files -> Juno compiler -> assembly + Shim.cpp -> Arduino CLI -> firmware
```

To generate the intermediate files without invoking the board compiler:

```bash
./mvnw -f juno-examples/pom.xml compile juno:compile \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

## 1. javac produces the input

Juno starts with normal Java compilation. `javac` checks the source, resolves overloads and emits `.class` files. The
source can use the Juno API, but it does not need to know whether the target is AVR, ARM, Xtensa or RP2040.

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

public final class Blink {
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
        while (true) {
            led.high();
            Delay.millis(500);
            led.low();
            Delay.millis(500);
        }
    }
}
```

The compiler then sees the program's reachable classes and methods as a closed world. It can resolve calls, field
layouts and supported intrinsics without carrying class-loading metadata onto the board.

## 2. Juno decodes and lowers bytecode

Juno reads the class files and translates supported bytecodes into an intermediate representation. Method calls,
branches, primitive operations, arrays and selected object operations are lowered to the target backend. Hardware APIs
are represented by intrinsics: a Java method such as `Led.builtin().toggle()` becomes a call to the board support code
selected by the compiler.

Unsupported bytecode or an unsupported API is a build error. That is important on an embedded target: silently falling
back to a JVM feature would produce firmware that cannot run on the board.

## 3. The backend emits machine code

The backend selects the instruction set for the board and emits deterministic assembly. AVR boards use AVR instructions,
the ARM boards use Thumb-2 (including the RP2350's Cortex-M33 in the Pico 2 W), and the supported ESP32 boards use
Xtensa. Juno's backend is board neutral; board-specific behavior belongs in the runtime profile and generated shim.

The generated assembly contains compiled Java methods and calls into a small runtime for allocation, scheduling,
exceptions, synchronization and selected library operations. Juno's compilation report makes arena estimates, generated
stack demand and risk findings visible. The Arduino toolchain later supplies the linked flash and RAM image sizes.

## 4. Shim.cpp connects Java to Arduino libraries

Some operations are most naturally implemented by the Arduino ecosystem. Juno generates `Shim.cpp` with the required
headers and helper functions. Includes and helpers are emitted only when the program uses the corresponding capability,
while core serial and LED support may be unconditional for the selected runtime.

The shim is not a second application. It is the narrow native boundary between generated Java code and the board core:
GPIO, serial, Wi-Fi, HTTP, displays and other services live there when a Java intrinsic needs them.

## 5. Arduino CLI produces the final image

The Maven plugin invokes the selected Arduino board core to compile the generated assembly and C++ shim together with
the Arduino runtime:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r4-wifi
```

The generated files are the useful boundary when diagnosing a failure. Inspect the generated `.S`, `Shim.cpp` and
Arduino sketch/report in the example's target output: a `javac` error is a Java-source problem, a Juno error is a
supported-subset or lowering problem, and an Arduino CLI error is a native include, core, linker or board-profile
problem.

`juno:verify` is a build check. It compiles the generated sketch with the real board toolchain but does not upload it.
Uploading is a separate action and should be followed by the Juno serial monitor when behavior is being verified.

## What this architecture buys you

There is no interpreter, JIT warm-up or runtime class loader. The firmware starts directly in native code, and the
compiler can reject unsupported features before a board is involved. The trade-off is that the supported Java subset is
deliberately explicit, and the compiler must know enough about the whole application to produce a complete image.

That is the central Juno idea: write a familiar Java program, then make the cost and the hardware boundary visible at
build time.
