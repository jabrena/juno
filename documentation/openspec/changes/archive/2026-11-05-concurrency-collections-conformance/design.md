## Context and decision

Threads, monitors, and memory visibility are lowered in the shared runtime (`ThreadSupport`, `ConcurrencySupport`,
`AtomicSupport`) and each backend has a thread port (Thumb-2 and Xtensa share the preemptive scheduler; AVR preempts at
safepoints). The fixtures exercise the same source on all three backends, so this change adds tests and catalog entries
and no compiler work.

Collections have no runtime in Juno, so the `collections` scope tests what the subset does provide (arrays and user
classes) and records what it does not. The decision that matters most is the boundary between the two.

## Scope boundary

The catalog measures Java language semantics against the JLS, as the issue's specification baseline requires. A
JDK library class is in the catalog only when the JLS defines its behavior in terms of a language primitive:

| Surface | Decision | Reason |
|---|---|---|
| `synchronized` statement and method, `wait`/`notify`/`notifyAll` | Covered (§§14.19, 8.4.3.6, 17.1, 17.2) | Monitor semantics are JLS language rules. |
| `volatile` fields, `Thread.start`/`join` publication | Covered (§§8.3.1.4, 17.4, 17.5) | JLS 17.4 defines the happens-before edges. |
| `AtomicInteger`, `AtomicBoolean`, `AtomicLong` | Covered through §8.3.1.4 | The JDK documents their memory effects as those of a `volatile` access; the fixture checks that contract. |
| `Thread.sleep`, `Thread.yield` | Covered (§17.3) | Named by JLS 17.3. |
| Non-atomic `long`/`double` | Covered (§17.7) on Arm only | The subset page claims whole 64-bit access on the Arm boards and nowhere else. |
| `CountDownLatch`, `Semaphore`, `CyclicBarrier`, `Phaser`, `ReadWriteLock`, `Exchanger`, `BlockingQueue` | Out of scope (JDK library) | The JLS defines no behavior for them. Their legacy QEMU programs still run in `src/test/qemu`. |
| `StructuredTaskScope`, `ScopedValue` | Out of scope (JDK 27 preview) | Preview APIs; the JLS does not define them. |
| `java.util` collections (`List`, `Map`, `Set`, their iterators) | Out of scope (JDK library, unsupported) | Juno implements none of them; see the collections decision below. |

Sections of `controlflow` that the new scopes own keep their `Other category:` pointers (§14.19 → `concurrency/`), as
`exceptions/` does for §§14.18 and 14.20.

## Determinism rules

A fixture prints only results that do not depend on scheduling, so the OpenJDK transcript is the single expected output:

- At most **two worker threads** run beside `main`. The Nano ESP32 runs at most four threads including `main`, and the
  AVR thread stacks are sized for a small number of threads.
- A worker's loop has a fixed bound. Results are read after `join`, never while a worker runs.
- Counters that depend on how often a thread was preempted are never printed. The fixtures print final totals,
  booleans, and counts of checked values (for example "torn reads: 0"), not the number of reads.
- No JDK exception message is printed; each `catch` prints a fixed label, as in `exceptions/`.
- Each printed line stays under the 31-character runtime `String` limit.
- Each fixture's state is allocated once in its static initializer or in `main`, and fits the AVR arena (1 KiB).

## Backend status

Every area declares all three backends. The default is `REQUIRED`, so a mismatch stays a visible failure. A backend
moves to `UNSUPPORTED` only when the subset page documents the limit, and that page changes in the same commit:

- `LongFieldPublication` (§17.7) is a separate program with `@Board` restricted to the UNO R4 WiFi and the UNO Q. The
  runners select boards from `@Board`, so the AVR and Xtensa boards never run it. Its area is `UNSUPPORTED` on AVR and
  Xtensa, which matches the documented claim.
- Every other concurrency area is `REQUIRED` on all three backends.

## Collections decision

`collections/` covers the iteration form that Juno compiles: the enhanced `for` over an array (§14.14.2), applied to a
user-defined sequence whose storage is a caller-allocated primitive array. `FixedCapacityList` provides add, get, set,
remove-at-index, `indexOf`, and `clear` with fixed capacity, and it exposes its storage to an enhanced `for`. It prints
only values and sizes, and an out-of-range access is caught by its class.

Two gaps stay open and are recorded rather than hidden:

- **`Iterable` enhanced `for`** (§14.14.2). Javac compiles it to `Iterable.iterator()`, and Juno has no `Iterable`
  runtime, so such a program is expected to fail at link time (task 1.4 confirms the diagnostic). §14.14.2 is one
  section that covers both forms, so the catalog cannot mark the `Iterable` form as a separate skipped entry. The scope
  comment records it, and task 3.1 asks for the decision. Implementing it is compiler work and needs the user's approval
  first.
- **`java.util` collections.** Out of the JLS authority and unsupported; the spec does not claim them.

Reference-typed arrays are not used by any fixture, because the Java subset page documents primitive arrays only.

## Verification approach

Each fixture is first run under OpenJDK to fix its expected transcript. It is then run under QEMU, simavr, and Espressif
QEMU through the existing runners, twice each, to confirm the output does not change between runs.
