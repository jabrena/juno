---
title: "Known Limitations"
description: "Known compiler bugs and sharp edges in what Juno compiles today, with workarounds."
layout: page
---

The [Feature Inventory](/features) lists what Juno supports. This page lists where reality is narrower than that
inventory or where the generated code behaves differently from the JVM, so you can design around it. Anything
outside the supported subset fails at link time with a diagnostic; a *known bug* would be the exception,
because it compiles without one.

## Known bugs

None open. Bugs the QEMU suite has found are fixed and kept as regression programs; open ones are listed in
`QemuRunIT.KNOWN_GAPS`. Fixed so far: `long` and `double` parameters, results and fields lost their high word on
the Thumb-2 path (for example `factorial(20)` printed `2192834560`, the low 32 bits, with no diagnostic), and
`long[]`, `double[]` and `float[]` arrays were rejected by the backend although the inventory listed them.

## Compile-time limits that are easy to hit

These fail with a `CompileException`; they are listed because they come up when writing ordinary Java.

- **Compound assignment on an array element** (`values[i] += x`, `values[i]++`). javac compiles it with `dup2`, which
  is outside the accepted opcode subset; write `values[i] = values[i] + x`.
- **`length` of an array parameter.** `arraylength` works only on a local array created once in the same method
  with a constant size. A method that receives an array must also receive its length, and an enhanced `for`
  over a parameter (which javac compiles to `arraylength`) is rejected. Use an indexed loop with a count.
- **Default and inherited interface methods.** A reachable implementation must declare each interface method
  it is called through directly.
- **JDK functional interfaces** such as `IntBinaryOperator` in signatures. Declare your own interface.
- **`Object` in signatures**, which rules out `equals(Object)` on records and classes when it is reachable.
- **Concatenating arbitrary objects.** Juno lowers `StringConcatFactory` for strings and primitive
  values, but not the general `String.valueOf(Object)`/`toString()` path.

## Exceptions

See [Feature Inventory](/features) for the supported subset. Beyond it:

- An integer division by zero raises a catchable `ArithmeticException`, from a callee too, but only when some
  `catch` or `finally` in the program can receive it; otherwise it still panics (and costs no guard).
- `addSuppressed` is accepted and dropped, so a `close()` failure while another exception unwinds is lost.
- Exception causes and stack traces are not modelled.

## How these are found

`./mvnw -f juno-examples/pom.xml -Pqemu verify` builds each program in `juno-examples/src/test/qemu/programs`
with the real generated assembly, runs it in QEMU (Cortex-M4) and requires output identical to the JVM. Known
bugs are listed in `QemuRunIT.KNOWN_GAPS` and skipped, so the suite stays green while the bug stays visible.
The harness models no hardware, so it covers language and runtime behavior only.
