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

The Cortex-M4 assembly backend gives every JVM local slot a full, fixed 8-byte region in the
method's stack frame, regardless of its actual type — so a `long`/`double` local can never overlap
the next slot's storage, at the cost of wasting 4 bytes per plain `int` local (see
`CortexM4AsmBackend`'s class doc for the exact frame-layout algorithm). Array references remain
32-bit handles on Cortex-M4, enums are ordinals (with associated integer values stored in
compiler-generated lookup tables), and each half of Juno's split `long` representation is 32 bits.

So a Java `byte`, `char`, `short`, `boolean`, or `int` intentionally share the `INT32` runtime
representation. The narrowing bytecodes are lowered
to explicit casts that reproduce Java's sign/zero-extension semantics:

- `i2b` (opcode 145) → `juno_i2b`, sign-extends the low 8 bits
- `i2c` (opcode 146) → a plain `static_cast<uint16_t>`, zero-extending like Java's `char`
- `i2s` (opcode 147) → `juno_i2s`, sign-extends the low 16 bits

These only run where the *Java source* narrows explicitly (a cast, or storing into a narrower
local); arithmetic itself is always full-width `int32_t`, exactly as the JVM specifies.

## Where Arduino's own types show up

The generated assembly itself is untyped machine code — every intrinsic call is a plain `bl`
branch with arguments already sitting in registers. Arduino's own type vocabulary
(`unsigned long`, `uint32_t`, `bool`) only reappears one layer down, inside the generated
`extern "C"` runtime shim that the assembly calls into and that itself calls the real Arduino API.
Juno's intrinsic lowering (`CortexM4AsmBackend#emitIntrinsicCall`, plus the shim-side helpers it
calls) is the single place that bridges the two:

| Juno API (Java)                          | Generated assembly call         | Shim / Arduino type notes |
|-------------------------------------------|----------------------------------|-------------------------|
| `Gpio.digitalWrite(int pin, boolean high)` | `bl digitalWrite`                | Java `boolean` (0/1) is loaded straight into the register Arduino's `digitalWrite` reads as `HIGH`/`LOW`; no C++ `bool` conversion involved |
| `Delay.millis(int ms)`                     | `bl delay` (calls Arduino's `delay` directly, no shim) | Arduino's `delay` takes `unsigned long`; the raw `int32_t` register value is reinterpreted as `unsigned long` at the call boundary itself, with no explicit cast anywhere in generated code |
| `Serial.begin(BaudRate baudRate)`           | resolves the enum to an `int`, then `bl juno_serial_begin` | shim casts to `unsigned long`; type-safe convenience overload for the five common rates in `BaudRate` |
| `Serial.println(int value)`                | `bl juno_serial_println`         | prints a signed decimal `int32_t`; `Serial.println(String)` instead compiles to `bl juno_serial_println_str` for a compile-time string literal (see [FEATURES.md](FEATURES.md) for the current runtime-`String` support) |
| `LedMatrix.loadFrame(int, int, int)`       | `bl juno_led_matrix_load_frame`  | the shim reinterprets each Java `int` bit-for-bit as `uint32_t` (a packed pixel bitmask, not a numeric value) |
| `Mouse.move(int x, int y)`                 | `bl juno_mouse_move`             | the shim narrows each Java `int` to Arduino's `signed char` (-128..127), sign-extended — out-of-range values wrap instead of clamping |

The recurring pattern: Java's type system has no `unsigned` and no distinct `bool` at the ABI
level, so every Arduino API that expects `unsigned long`, `uint32_t`, or a `HIGH`/`LOW`/`bool`
convention gets an explicit cast/reinterpretation inside the shim, one call removed from the
generated assembly. Application code never sees or writes these casts; the affected hardware APIs
retain their Java integer-like signatures.

## `DigitalOutput`: an object with no runtime representation

`DigitalOutput` is an object-shaped API but not a real object at runtime. The
"constructor" `DigitalOutput.of(int pin)` inlines directly into the caller's own assembly — load
the pin number, load `OUTPUT` (1), `bl pinMode`, then keep the pin number itself as the "handle":

```asm
    ldr  r0, <pin>
    movs r1, #1        @ OUTPUT
    bl   pinMode
    ldr  r0, <pin>     @ the "handle" is just the pin number again
```

Every instance method (`high`, `low`) receives that same pin number as its receiver and calls
`digitalWrite` directly (`r1 = 1` or `0`) — there is no heap allocation, no vtable, and no field
storage. `DigitalOutput led = DigitalOutput.of(13)` and the raw pin number `13` compile to
identical code; the Java type exists purely to make call sites read like object method calls
(`led.high()` instead of `Gpio.digitalWrite(13, true)`). `Gpio.analogRead` lowers to the core's real
`analogRead` the same way `digitalWrite` does. `DigitalOutput.toggle()`/`isHigh()` and
`Gpio.digitalRead`/`analogWrite` still exist in the Java API but the Cortex-M4 assembly backend
doesn't lower them yet (`CompileException: ... does not support this yet: intrinsic ...` at compile
time).

## What this rules out

Juno supports typed `INT64`, `FLOAT32`, and `FLOAT64` boundary values in addition to its normal
`INT32` values. JVM `long` stack/local values remain paired 32-bit halves and are packed when crossing
a call, field, or array boundary. Remaining exclusions include:

- general `String` construction/concatenation and most `String`/`StringBuilder` methods — a bounded
  set of runtime-`String` operations is supported instead (string literals, `String.valueOf(int)`/
  `(double)`, `length()`, `charAt(int)`, and a fixed-capacity `StringBuilder`); see
  [FEATURES.md](FEATURES.md) for the exact list. Multi-character LED matrix display (see
  [`LedCanvas`](../juno/src/main/java/io/github/jabrena/juno/api/led/LedCanvas.java)) still works
  character-by-character with hand-encoded font tables rather than string data
- polymorphic objects/inheritance and unbounded allocation. Final closed-world objects and records use
  one-slot handles into a fixed 8 KiB program-lifetime arena with no reclamation

See [FEATURES.md](FEATURES.md) for the full, authoritative list.
