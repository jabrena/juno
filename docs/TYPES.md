# Java types vs. Arduino types

Juno only accepts a small slice of Java's type system (see [FEATURES.md](FEATURES.md)),
and maps all of it onto a single C++ representation. This note explains why, and where the two
type systems still show through at the API boundary.

## Supported Java types

`Descriptor.isIntegerLike` (in
[`juno/src/main/java/io/github/jabrena/juno/linker/Descriptor.java`](../juno/src/main/java/io/github/jabrena/juno/linker/Descriptor.java))
admits exactly five JVM descriptor types as method parameters/results:

| Java type | JVM descriptor |
|-----------|-----------------|
| `boolean` | `Z` |
| `byte`    | `B` |
| `char`    | `C` |
| `short`   | `S` |
| `int`     | `I` |

`long`, `float`, `double`, arrays, and every reference type except the compiler-erased
`DigitalOutput` handle (see below) are rejected at link time.

## One stack slot, one C++ type

The JVM itself never gives `boolean`, `byte`, `char`, or `short` their own operand-stack
representation — bytecode always computes with them as a 32-bit `int` on the stack, only
narrowing on store (`i2b`, `i2s`) or when a `char` needs zero-extension. Juno's backend
(`ArduinoCppBackend`) mirrors this directly: every local variable and every stack slot in
generated C++ is declared `int32_t`, regardless of which of the five Java types produced it.

```cpp
int32_t locals[N] = {};
int32_t stack[M] = {};
```

So a Java `byte`, `char`, `short`, `boolean`, or `int` are indistinguishable once they reach
Juno's backend — there is exactly one runtime representation. The narrowing bytecodes are lowered
to explicit casts that reproduce Java's sign/zero-extension semantics:

- `i2b` (opcode 145) → `juno_i2b`, sign-extends the low 8 bits
- `i2c` (opcode 146) → a plain `static_cast<uint16_t>`, zero-extending like Java's `char`
- `i2s` (opcode 147) → `juno_i2s`, sign-extends the low 16 bits

These only run where the *Java source* narrows explicitly (a cast, or storing into a narrower
local); arithmetic itself is always full-width `int32_t`, exactly as the JVM specifies.

## Where Arduino's own types show up

Generated sketches still call the real Arduino/C++ API, which has its own, unrelated type
vocabulary (`unsigned long`, `uint32_t`, `bool`, `int`). Juno's intrinsic lowering
(`ArduinoCppBackend#intrinsicExpression`) is the single place that bridges the two:

| Juno API (Java)                          | Generated C++ call                                    | Arduino/C++ type notes |
|-------------------------------------------|--------------------------------------------------------|-------------------------|
| `Gpio.digitalWrite(int pin, boolean high)` | `digitalWrite(call_arg0, call_arg1 ? HIGH : LOW)`      | Java `boolean` (0/1 as `int32_t`) becomes Arduino's `HIGH`/`LOW` constants, not C++ `bool` |
| `Gpio.digitalRead(int pin)` → `boolean`    | `static_cast<int32_t>(digitalRead(call_arg0) == HIGH)` | Arduino's `digitalRead` returns `int`; Juno re-encodes it as a Java `boolean` (0/1) |
| `Gpio.analogRead(int pin)` → `int`         | `static_cast<int32_t>(analogRead(call_arg0))`          | Arduino's ADC reading (`int`, 0-1023 on UNO R4) fits `int32_t` unchanged |
| `Delay.millis(int ms)`                     | `delay(static_cast<unsigned long>(call_arg0))`         | Arduino's `delay` takes `unsigned long`; Java has no unsigned types, so this is a reinterpreting cast |
| `Clock.millis()` → `int`                   | `static_cast<int32_t>(millis())`                       | Arduino's `millis()` returns `unsigned long`, which wraps at ~49.7 days; reinterpreted as a signed `int32_t`, the same bit pattern reads as negative after ~24.8 days. Delta calculations like `millis() - start` still work (two's-complement wraparound), but the raw value itself is not always positive |
| `Serial.begin(int baudRate)`               | `Serial.begin(static_cast<unsigned long>(call_arg0))`  | same unsigned/signed mismatch as `Delay.millis` |
| `Serial.println(int value)`                | `Serial.println(call_arg0)`                            | prints a signed decimal `int32_t`; there is no `String` overload because Juno has no `String` |
| `LedMatrix.loadFrame(int, int, int)`       | `juno_led_matrix_load_frame(...)` → `const uint32_t frame[3]` | each Java `int` is reinterpreted bit-for-bit as `uint32_t` (a packed pixel bitmask, not a numeric value) |
| `Mouse.move(int x, int y)`                 | `Mouse.move(static_cast<signed char>(call_arg0), static_cast<signed char>(call_arg1))` | Arduino's `Mouse.move` takes `signed char` (-128..127); each Java `int` is narrowed to its low 8 bits, sign-extended — out-of-range values wrap instead of clamping |

The recurring pattern: Java's type system has no `unsigned` and no distinct `bool` at the ABI
level, so every Arduino API that expects `unsigned long`, `uint32_t`, or a `HIGH`/`LOW`/`bool`
convention gets an explicit `static_cast`/ternary at the exact point Juno lowers the call. Application
code never sees or writes these casts — it only ever deals in `boolean`, `byte`, `char`, `short`,
and `int`.

## `DigitalOutput`: an object with no runtime representation

`DigitalOutput` (the one non-primitive type Juno accepts) is not a real object at runtime. The
"constructor" `DigitalOutput.of(int pin)` lowers to `juno_digital_output_of`, which just calls
`pinMode(pin, OUTPUT)` and returns the pin number:

```cpp
static int32_t juno_digital_output_of(int32_t pin) {
  pinMode(pin, OUTPUT);
  return pin;
}
```

Every instance method (`high`, `low`, `toggle`, `isHigh`) receives that same `int32_t` pin number
as `call_receiver` and calls `digitalWrite`/`digitalRead` directly — there is no heap allocation,
no vtable, and no field storage. `DigitalOutput led = DigitalOutput.of(13)` and the raw pin number
`13` compile to identical code; the Java type exists purely to make call sites read like object
method calls (`led.high()` instead of `Gpio.digitalWrite(13, true)`).

## What this rules out

Because everything collapses to `int32_t`, Juno cannot (yet) represent:

- Java's `long`, `float`, or `double` — there is no 64-bit or floating-point lane in the backend
- `String` — so no `Serial.print(String)`; only the `int` overloads exist, and multi-character
  display (see [`LedMatrixText`](../juno/src/main/java/io/github/jabrena/juno/api/LedMatrixText.java))
  works character-by-character with hand-encoded font tables instead of string data
- arrays, so a `uint32_t[3]` LED matrix frame is passed as three separate `int` parameters
  instead of one array parameter
- real objects/fields — `DigitalOutput` is the only reference type, and it is erased entirely by
  link time

See [FEATURES.md](FEATURES.md) for the full, authoritative list.
