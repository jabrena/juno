---
title: "Serial"
description: "Reading and writing over USB serial."
layout: page
---

[`Serial`](https://github.com/jabrena/juno/blob/main/juno/src/main/java/io/github/jabrena/juno/api/io/serial/Serial.java) is a compiler
intrinsic that wraps the Arduino core's USB serial port (`Serial.begin`/`print`/`println`). It is
the primary way a Juno program talks back to the development machine while it runs on real
hardware.

## Why it matters

Juno programs run on bare Cortex-M4 hardware with no attached debugger, no breakpoints, and no
console. Without `Serial`, the only observable behavior of a running sketch is whatever it does
with GPIO, the LED matrix, or an LCD — which is rarely enough to tell *why* something is wrong.
`Serial` is how you:

- confirm a program actually reached a given line (`Serial.println` markers between steps, the
  same way [`LcdKeypadDemo`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/lcd/LcdKeypadDemo.java)
  prints `1`, `2`, `3` after each setup call);
- watch live sensor/state values (button reads, `Wifi.status()`, HTTP result codes — see
  [docs/INTERNET.md](../internet)) instead of guessing from final behavior;
- see the conservative garbage collector's own diagnostics, when a program is compiled with
  `--gc-log` (one line per collection: arena bytes used before/after — see
  [docs/FEATURES.md](../features));
- tell a silent hang (arena exhaustion, an infinite loop, a bad button threshold) apart from a
  crash, since there is no stack trace to read afterward.

Because of this, add `Serial.begin(...)` and a few `Serial.println(...)` checkpoints to a new
program *before* debugging hardware behavior, not after. Flashing the board and reading its serial
output is the normal way to confirm a change actually behaves correctly on real hardware.

## API

```java
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

Serial.begin(BaudRate.BAUD_115200);   // preferred: a named, valid rate
Serial.begin(115200);                 // equivalent, for a custom/non-standard rate

Serial.print(42);                     // "42"
Serial.println(42);                   // "42\n"
Serial.print(true);                   // "true"
Serial.println(false);                // "false\n"
Serial.println(123456789012L);        // 64-bit integer
Serial.println(3.14f);                // single-precision floating point
Serial.println(3.141592653589793);    // double-precision floating point
Serial.print("Distance: ");           // compile-time string literal, no trailing newline
Serial.println("Ready");              // string, with a trailing newline
Serial.println(e.getMessage());       // runtime String (prints "null" for null)
```

| Method | Argument | Notes |
|---|---|---|
| `begin(BaudRate)` | a [`BaudRate`](https://github.com/jabrena/juno/blob/main/juno/src/main/java/io/github/jabrena/juno/api/io/serial/BaudRate.java) constant | preferred; keeps the rate and the monitor's config in sync |
| `begin(int)` | a data rate in bits per second | for a rate `BaudRate` doesn't name |
| `print(boolean)` / `println(boolean)` | any `boolean` expression | prints `true` or `false` |
| `print(int)` / `println(int)` | any `int` expression | prints in decimal |
| `print(long)` / `println(long)` | any `long` expression | preserves the full Java 64-bit value |
| `print(float)` / `println(float)` | any `float` expression | uses Arduino's floating-point formatting |
| `print(double)` / `println(double)` | any `double` expression | uses Arduino's floating-point formatting |
| `print(String)` / `println(String)` | any `String` expression | literals print straight from flash; a `null` prints `null` |

`BaudRate` provides `BAUD_9600`, `BAUD_19200`, `BAUD_38400`, `BAUD_57600`, and `BAUD_115200`.
`juno-examples` conventionally uses `BAUD_115200` everywhere, which is also `juno:monitor`'s
default.

### Printing runtime strings

`Serial.print(String)`/`println(String)` also print runtime `String` values — `String.valueOf(...)`,
a `StringBuilder` result, a JSON string, an exception's `getMessage()`, or a bounded concatenation.
Juno lowers javac's `StringConcatFactory.makeConcatWithConstants` call sites for strings and primitive
values; the resulting UTF-8 text must fit in the 31-byte runtime-string payload:

```java
int temperature = /* ... */;
Serial.println("Temperature C: " + temperature);
```

Concatenating an arbitrary object remains unsupported because it requires broader
`String.valueOf(Object)`/`toString()` behavior.

## A minimal example

[`SerialCounter`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/io/serial/SerialCounter.java)
opens the connection and prints an incrementing counter, one value per line, once a second:

```java
@Board(ArduinoUnoR4WiFi.class)
public final class SerialCounter {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        int counter = 0;
        while (true) {
            Serial.println(counter);
            counter = counter + 1;
            Delay.millis(1000);
        }
    }
}
```

## Watching the output

Build, upload, and open the serial monitor with `juno-maven-plugin` (see
[docs/JUNO-MAVEN-PLUGIN.md](../juno-maven-plugin)):

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.serial.SerialCounter

./mvnw -f juno-examples/pom.xml juno:monitor
```

Always use the `juno:monitor` goal rather than invoking `arduino-cli monitor` directly — it
resolves the same board port `juno:upload` used and defaults to the `115200` baud rate every
`juno-examples` program uses, so the two never mismatch.

## Troubleshooting

- **Monitor shows nothing or garbage:** the monitor's baud rate must match the value passed to
  `Serial.begin`. `juno:monitor`'s default (`115200`) matches `BaudRate.BAUD_115200`; override it
  with `-Djuno.baudRate=<rate>` if a program calls `Serial.begin` with a different value.
- **First lines are missing:** some boards reset when the serial connection opens, and a few
  milliseconds of early output can be lost before the monitor attaches. Add a short
  `Delay.millis(...)` before the first `Serial.println` if early output matters, or simply rely on
  a loop that keeps printing.
- **Nothing prints inside a loop at all:** confirm the loop is actually reached — add a
  `Serial.println` right at the top of `main` first, then move the checkpoint deeper until the
  silent section is found.
