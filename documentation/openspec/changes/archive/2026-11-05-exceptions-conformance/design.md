## Context and decision

Exceptions are lowered in the shared IR (`ControlFlowLowering`, `ThrowingMethods`) and every backend already
propagates a pending exception across calls, so the fixtures exercise the same source on all three backends without
new compiler work.

The fixtures avoid three things that differ between the JVM and a board:

- **JDK exception messages.** Messages such as `/ by zero` or the array-index text are not printed; each `catch`
  prints a fixed label, and only messages supplied by the fixture's own exception classes are printed.
- **`java.math` and other large library code.** They do not fit the AVR runner's arena and are outside this category.
- **Long runtime strings.** Each printed line stays well inside the 31-character runtime `String` limit.

The four programs split the area by concern: exception kinds and causes (§§11.1.1–11.1.2), throw and catch matching
(§§14.18, 14.20.1), `finally` and unwinding (§§11.3, 14.20.2), and try-with-resources (§§14.20.3–14.20.3.2). Each area
cites its JLS sections and references its program; an area is `REQUIRED` on all three backends.

The compile-time checking rules of chapter 11 (§§11.1.3, 11.2–11.2.3) are recorded as `Out of scope:` because javac
enforces them before Juno sees the bytecode, and asynchronous exceptions are not raised by the board runtime. The
`controlflow` scope keeps its existing `Other category: exceptions/` pointers for §§14.18 and 14.20.

All three backends are `REQUIRED`; a mismatch must remain visible as a conformance failure.
