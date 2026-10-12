---
name: juno-zero-allocation
description: Use when writing or reviewing a Juno example program (juno-examples) or a juno-api entry (a new native method, handle type or helper) so it runs in the board's fixed arena with zero allocations, or with a minimum number of objects created once at startup. Covers what allocates on a Juno board, API shapes that never allocate (caller-owned buffers, primitive handles, status returns), the startup-allocation pattern for programs that need objects, and how to verify allocation with compiler metrics, JUNO-RISK findings and the GC log. Triggers include write a Juno example, add a juno-api method, avoid allocation, reduce RAM, arena out of memory, JUNO-RISK-001, JUNO-RISK-002.
---
# Zero-allocation Juno programs and API entries

Juno compiles Java ahead of time for boards with a few KB of RAM. Every run-time allocation comes from one
**fixed arena** (8 KiB by default on the UNO R4 WiFi and UNO Q, 1 KiB on the UNO WiFi Rev2), collected by a
conservative, non-moving mark-and-sweep GC. When the arena is full even after a collection, the board stops with
`[juno-gc] OOM: need …` on the serial port: there is no `OutOfMemoryError` to catch. `juno-api` and
`juno-examples` are therefore written in a deliberately different style from `juno-compiler` and
`juno-maven-plugin`: primitives, caller-owned arrays and static intrinsics instead of object graphs.

Read `juno-site/src/main/resources/content/memory-model.md` and `limitations.md` for the full model.

## Where data lives

| Costs arena (allocates at run time) | Free (no allocation) |
|---|---|
| `new` of a program class, record or built-in exception | Primitives and local variables (stack) |
| Every `new T[n]` array, including `values()`-style copies you write | `static` fields (`.bss`) and enum constants (ordinals) |
| Capturing lambdas (each `LambdaCreate` with captured values) | String literals (flash) |
| `BigInteger`/`BigDecimal`, atomics, `StringBuilder`, task/scope handles | `static final` arrays with an all-constant initializer, only read (flash `.rodata`) |
| A `static final` table the compiler cannot keep in flash (`JUNO-RISK-012`) | `System.getenv("LITERAL")` and `LedCanvas.drawText(…, "literal", …)`, folded at compile time |

String concatenation, `String.valueOf` and `StringBuilder.toString()` do not use the arena: they use the
**runtime-string pool**, rotating 32-byte slots. A runtime string longer than 31 characters overflows its slot, so
split long `Serial` lines into several prints.

The optimizer can remove some allocations (it inlines small methods and scalar-replaces objects that never leave
a method), but treat that as a bonus: write the program so it does not depend on it, and measure.

## Rules for juno-api entries

1. **Shape the class like the existing API**: a `final`, non-instantiable class of `static native` intrinsics, or
   static methods over primitives. A handle is a zero-cost wrapper around a primitive (as `DigitalOutput` wraps a
   pin number), never an object graph.
2. **Let the caller own every buffer.** Take a `byte[]`/`int[]` plus an offset or length and fill it; never return a
   new array or a `String` built at run time. `Udp` (`int[5]` endpoint, caller-owned payload) and the bounded
   `Json` reader are the reference designs.
3. **Return primitives**: a `boolean` for success, an `int` count or status code. Do not throw on the hot path.
4. **Take compile-time text where it is configuration**: a parameter the intrinsic needs as a literal is declared
   in `IntrinsicRegistry` (`requiresLiteralStringArgument`/`prefersLiteralStringArgument`), so it costs no runtime
   string.
5. **Wire the intrinsic**: a new `native` method needs entries in `IntrinsicRegistry` and `IntrinsicLowering`
   (`juno-compiler`), its shim code gated on use (`ShimFeature`), and a `JunoCompilerTest`/`GeneratedAsmToolchainTest`
   case. The backend never branches on a specific board.
6. **Document the cost** in the Javadoc: "allocation-free", or exactly what it allocates and when.

## Rules for example programs

1. **Allocate once, before the main loop.** Buffers, frames, entity objects and lookup tables are created at
   startup and kept in fields or locals of `main`; the `while (true)` loop only mutates them.
2. **Nothing in the loop allocates**: no `new`, no array creation, no capturing lambda, no `StringBuilder`, no
   boxing (`Integer`, collections, `var` over wrappers), no string concatenation unless it is short and the pool
   rotation is understood.
3. **Constant data goes to flash**: write tables as `static final` arrays with literal initializers (fonts,
   sprites, maps, notes) and only read them.
4. **Size arrays with compile-time constants**, assign each array local once, and pass a length alongside every
   array parameter: that keeps bounds checks and `arraylength` resolvable (see `limitations.md`).
5. **Model state with enums and `int`s**, not small objects: an enum is its ordinal and `switch` on it is a table.
6. **Print in pieces**: `Serial.print("x=")` then `Serial.println(x)` rather than `"x=" + x` in a loop.
7. **When objects make the design clearer** (a game's entities, a parser's state), use a fixed pool created at
   startup and reuse its members (reset fields instead of creating new instances). Prefer `final` classes without
   subclasses so calls dispatch statically.

```java
// Bad: allocates every frame and builds a runtime string each time
while (true) {
    boolean[][] frame = new boolean[8][12];
    Serial.println("score=" + score + " lives=" + lives);
}

// Good: one frame for the whole run, printed in pieces
boolean[][] frame = new boolean[8][12];
while (true) {
    LedCanvas.clear(frame);
    Serial.print("score=");
    Serial.println(score);
}
```

## Verify, do not assume

1. **Compiler findings**: `juno:compile`/`juno:verify` print `JUNO-RISK-*` findings. `JUNO-RISK-001` (allocation
   that can repeat in a loop) and `JUNO-RISK-002` (startup arena estimate above capacity) are the ones this skill
   targets; `JUNO-RISK-012` explains a table that stayed in RAM.
2. **Metrics**: `java -jar juno.jar compile --main <class> --metrics` reports `Direct allocation calls`; an
   allocation-free loop body adds none.
3. **At run time**: build with `-Djuno.gcLog=true` and watch `./mvnw -f juno-examples/pom.xml juno:monitor`: each
   collection prints one line, so an allocation-free loop prints none after startup. `Memory.arenaUsedBytes()`
   reports usage from inside the program.
4. **Only if the data genuinely needs it**, raise the arena with `-Djuno.Xmx=<size>` (or `<arenaSize>` in the
   example's pom); it is a capacity, not a fix for a loop that allocates.
5. Run `./mvnw test`, then `juno:verify` for the example, and flash it to the board before calling it done.
