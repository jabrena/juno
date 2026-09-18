# Java types vs. Arduino types

Juno only accepts a small slice of Java's type system (see [FEATURES.md](FEATURES.md)). Its IR now
records an explicit `JunoType` for every `Value`; accepted bytecodes lower to `INT32`, `INT64`,
`FLOAT32`, and `FLOAT64` values. This note explains the current representations and where the Java
and Arduino type systems meet.

## Supported Java types

Method descriptor validation (in
[`juno/src/main/java/io/github/jabrena/juno/linker/Descriptor.java`](../juno/src/main/java/io/github/jabrena/juno/linker/Descriptor.java))
admits eight primitive JVM descriptor types as ordinary scalar method parameters/results:

| Java type | JVM descriptor |
|-----------|-----------------|
| `boolean` | `Z` |
| `byte`    | `B` |
| `char`    | `C` |
| `short`   | `S` |
| `int`     | `I` |
| `long`    | `J` |
| `float`   | `F` |
| `double`  | `D` |

Primitive arrays, simple enums, records, and final closed-world object types are also supported as
parameters/results under the restrictions in [FEATURES.md](FEATURES.md). `DigitalOutput` remains a
compiler-erased handle rather than an arena object.

## Typed IR scalar lanes

The JVM itself never gives `boolean`, `byte`, `char`, or `short` their own operand-stack
representation — bytecode always computes with them as a 32-bit `int` on the stack, only
narrowing on store (`i2b`, `i2s`) or when a `char` needs zero-extension.

Juno represents every symbolic IR register as `Value(id, JunoType)`. `JunoType` defines `INT32`,
`INT64`, `FLOAT32`, and `FLOAT64`, and the backend declares each value using its recorded type.

Integer-only methods retain an `int32_t` local-slot array. A method containing `long`, `float`, or
`double` values uses a typed `JunoSlot` union so the same JVM slot can safely hold different lanes at
different points in the method.
Array references remain 32-bit handles on Cortex-M4, enums are ordinals, and each half of Juno's split
`long` representation is 32 bits.

```cpp
int32_t locals[N] = {};
int32_t v0, v1, v2;

// In a method containing wide or floating-point values:
union JunoSlot { int32_t i32; int64_t i64; float f32; double f64; };
JunoSlot locals[N] = {};
float v3, v4;
```

So a Java `byte`, `char`, `short`, `boolean`, or `int` intentionally share the `INT32` runtime
representation. The narrowing bytecodes are lowered
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
code never sees or writes these casts; the affected hardware APIs retain their Java integer-like signatures.

## `DigitalOutput`: an object with no runtime representation

`DigitalOutput` is an object-shaped API but not a real object at runtime. The
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

Juno supports typed `INT64`, `FLOAT32`, and `FLOAT64` boundary values in addition to its normal
`INT32` values. JVM `long` stack/local values remain paired 32-bit halves and are packed when crossing
a call, field, or array boundary. Remaining exclusions include:

- `String` — so no `Serial.print(String)`; only the `int` overloads exist, and multi-character
  display (see [`LedMatrixText`](../juno/src/main/java/io/github/jabrena/juno/api/LedMatrixText.java))
  works character-by-character with hand-encoded font tables instead of string data
- polymorphic objects/inheritance and unbounded allocation. Final closed-world objects and records use
  one-slot handles into a fixed 8 KiB program-lifetime arena with no reclamation

See [FEATURES.md](FEATURES.md) for the full, authoritative list.
