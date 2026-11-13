---
title: "JLS 17 Conformance"
description: "Focused Java Memory Model examples, what each one proves, and how to run them on every Juno board that has a task scheduler."
layout: page
---

This page reviews [JLS Chapter 17: Threads and Locks](https://docs.oracle.com/javase/specs/jls/se27/html/jls-17.html)
against Juno. The examples live in
[`io.github.jabrena.juno.language.concurrency`](https://github.com/jabrena/juno/tree/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency).
Each board entry point is intentionally small, prints one observable result, allocates its state once at startup, and
does not allocate in its worker loops.

A successful run demonstrates one concrete execution. It cannot prove that every execution allowed by the Java
Memory Model is implemented correctly. In particular, a stress test that does not observe a permitted data-race
outcome does not make the racy program correct. The deterministic examples therefore assert required outcomes; the
one deliberately racy final-field example treats failure to observe the published reference as allowed.

## Locks and higher-level coordination objects

JLS 17 specifies the memory-ordering primitives (monitors, volatile access, thread start/join and interruption); it
does not make `Semaphore`, `CountDownLatch` or the other `java.util.concurrent` classes language primitives. Juno
nevertheless lowers a restricted, board-usable subset of those library classes on every board with the
`STRUCTURED_CONCURRENCY` capability (all scheduler boards except Classic UNO R3). The examples make that boundary
visible:

| Example | Object demonstrated | Target |
|---|---|---|
| [`Locks`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/Locks.java) | Concrete `ReentrantLock`, reentrancy, `tryLock`, and `finally`/`unlock` | Board-ready on all scheduler boards |
| [`Atomics`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/Atomics.java) | `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `compareAndSet` and `getAndSet` | Board-ready on all scheduler boards |
| [`StructuredRace`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/StructuredRace.java) | `StructuredTaskScope` joiners, subtasks, joining and cancellation | Compiler-supported on all scheduler boards; the example declares its current board targets |
| [`Semaphores`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/Semaphores.java) | Restricted `Semaphore`: permits, blocking/try acquire, release and fairness policy | Compiler-supported subset; this example remains a JVM-oriented executable reference |
| [`CountDownLatchExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/CountDownLatchExample.java) | Restricted `CountDownLatch`: count, count-down and blocking/timed await | Compiler-supported subset; this example remains a JVM-oriented executable reference |
| [`PhaserExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/PhaserExample.java) | Non-tiered `Phaser`: registration, arrival, phase advance and interruptible await | Compiler-supported subset; timed, tiered and terminating forms are rejected |
| [`CyclicBarrierExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/CyclicBarrierExample.java) | Reusable barrier, barrier action and interrupt-driven breakage | Compiler-supported subset; this example remains a JVM-oriented executable reference |
| [`ReadWriteLockExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/ReadWriteLockExample.java) | Separate read and write locks | Compiler-supported subset; fair `ReentrantReadWriteLock` is rejected |
| `Condition` | `ReentrantLock.newCondition()`, await, signal and signal-all | Compiler-supported subset; the write lock can also create a condition |
| [`ExchangerExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/ExchangerExample.java) | Two-party value exchange | JVM-only; no Juno lowering |
| [`BlockingQueueExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/BlockingQueueExample.java) | Blocking producer/consumer queue | JVM-only; no Juno lowering |

The same board capability also includes the JDK 27 preview `ScopedValue` API used by structured-task programs. It is
lexically scoped and is not a substitute for a shared mutable field or a synchronization primitive.

The board-safe [`BoundedBuffer`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/BoundedBuffer.java)
shows the replacement pattern for a queue: a concrete `ReentrantLock`, a non-blocking `offer`/`poll`, and
`Thread.yield()` while the caller retries. The compiler rejects unsupported classes and unsupported overloads instead
of silently changing their blocking or fairness semantics. The JUnit examples still run on OpenJDK, so each object has
a focused executable example and a deterministic assertion.

## Clause-to-example matrix

| JLS clause | Juno status | Focused evidence |
|---|---|---|
| 17.1 Synchronization | Monitor blocks are supported and reentrant. Normal and abrupt block exit emit monitor release. Synchronized methods are rejected. | [`MonitorHappensBefore`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/MonitorHappensBefore.java) prints `monitor value 42`. |
| 17.2 Wait sets and notification | Board objects have monitors and Juno implements a restricted wait set. Timed `Object.wait(long)` is not lowered. | The following rows describe the supported zero-argument board operations and their JVM rules. |
| 17.2.1 Wait | `Object.wait()` is lowered. It requires monitor ownership, releases the thread's monitor acquisitions, waits for notification or interruption, and reacquires the monitor before returning or throwing. | [`WaitNotification.await`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/WaitNotification.java) uses the required guarded `while` loop. |
| 17.2.2 Notification | `notify` and `notifyAll` are lowered. JVM `notify` chooses an arbitrary waiter; `notifyAll` wakes all waiters, which then reacquire the monitor one at a time. | [`WaitNotification.publish`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/WaitNotification.java) changes guarded state before `notifyAll`. |
| 17.2.3 Interruptions | `Thread.interrupt()` and `isInterrupted()` are lowered. An interrupt sets the status; a supported blocking operation such as wait, sleep, await or acquire throws `InterruptedException` and consumes the pending status. | [`Interruption`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/Interruption.java) demonstrates restoring the status when an exception cannot be propagated. |
| 17.2.4 Wait/notification/interruption races | Juno models the restricted wait/notification/interrupt interaction, but does not provide timed `Object.wait(long)`. On the JVM a notified-and-interrupted waiter may return normally with a pending interrupt or throw; it may not clear the interrupt and return normally, and notification cannot be lost. | Covered as a specification rule rather than a deterministic board test because both race outcomes are legal. |
| 17.3 Sleep and yield | Both are supported scheduling points. As required, neither releases a monitor nor creates a Java Memory Model synchronization edge. | [`SleepAndYield`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/SleepAndYield.java) prints `monitor retained true`. |
| 17.4.1 Shared variables | Fields and array elements are shared; locals and parameters remain thread-confined. | All examples keep shared state in fields or arrays and worker-local expected values in locals. |
| 17.4.2 Actions | Ordinary reads/writes, volatile reads/writes, monitor actions, thread start, interruption, thread termination detection, and serial output are represented. Thread divergence is specification machinery rather than a terminating board test. | The examples below exercise each supported action kind. |
| 17.4.3 Program order and sequential consistency | Single-carrier boards execute one task at a time. Dual-carrier ESP32 and Pico 2 W boards can run two Java carriers concurrently; runtime critical sections remain protected, but portable code must still be correctly synchronized. | [`LostUpdates`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/LostUpdates.java) contrasts a racy compound action with monitor, lock, and atomic updates. |
| 17.4.4 Synchronization order | Volatile, monitor, start, join, and interruption synchronization edges are supported. Default zero initialization occurs before Java code runs. | `MonitorHappensBefore`, [`VolatileHappensBefore`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/VolatileHappensBefore.java), and [`ThreadStartAndJoin`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/ThreadStartAndJoin.java). |
| 17.4.5 Happens-before | Monitor unlock/lock, volatile write/read, start, join, program order, and transitivity publish the expected values. A data race remains a bug even when a particular Juno scheduling configuration happens to hide it. | The three happens-before examples each print `42`; `LostUpdates` documents the racy counterexample. |
| 17.4.6–17.4.7 Executions and well-formedness | These sections define how a specification validates an execution; they are not separate runtime APIs. Juno's compiler/runtime must only produce well-formed executions. | Cross-checks run the examples on OpenJDK and on generated target code. |
| 17.4.8 Causality | Values cannot arise solely from circular reasoning between racy reads and conditional writes. | [`Causality`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/Causality.java) prints `out of thin air false`. |
| 17.4.9 Observable behavior and nontermination | Serial calls are external actions. Hangs are valid observable outcomes for deadlock or infinite execution, but an automated conformance example must terminate, so the suite does not deliberately hang a board. | [`DiningPhilosophers`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/DiningPhilosophers.java) avoids deadlock with a global lock order. |
| 17.5 Final fields | Constructor-assigned final fields provide safe immutable state even when the reference publication itself is racy. | The following rows separate ordinary freeze semantics from JVM-only reflective and `System`-stream exceptions. |
| 17.5.1 Final-field semantics | Constructor exit freezes each final field. The guarantee extends to the initialized state reached through a final reference. | [`FinalFieldPublication`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/FinalFieldPublication.java) checks a final primitive and an array reached through a final field, then prints `final value 42` when its deliberately racy reader observes the reference. Not observing the reference is allowed. |
| 17.5.2 Reads during construction | Reads in the constructing thread follow normal program order: a read after assignment sees the assigned value. Java's definite-assignment rules prevent an ordinary constructor from reading its own blank final before assignment. | The `Value` constructor assigns `answer` before the object can be published. |
| 17.5.3 Subsequent modification | Reflection/deserialization mutation of final fields is not in Juno's closed-world subset. Portable board programs never mutate a final after construction. | Marked unsupported rather than emulated with weaker semantics. |
| 17.5.4 Write-protected fields | The special JVM treatment of `System.in`, `System.out`, and `System.err` is not applicable: Juno serial I/O uses `Serial`, and `System.setIn/setOut/setErr` are not lowered. | No board test; these JVM globals are outside the board API. |
| 17.6 Word tearing | Distinct fields and array elements are updated independently, including adjacent bytes on AVR. | [`WordTearing`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/WordTearing.java) prints `word tears 0`. |
| 17.7 `double`, `long`, and reference atomicity | References and volatile 64-bit values are atomic. Juno also keeps non-volatile 64-bit fields whole while its scheduler is active, which is stronger than the minimum JLS guarantee. | [`ReferenceAtomicity`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/ReferenceAtomicity.java) prints `invalid references 0`; [`VolatileLongAtomicity`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/concurrency/VolatileLongAtomicity.java) prints `torn longs 0`. |

## Board matrix

| Target | Compile and link | Runtime execution |
|---|---|---|
| UNO R4 WiFi | Supported; one carrier | Physical board, or Cortex-M4 QEMU for the language/runtime layer |
| UNO Q | Supported; one carrier | Physical board; Cortex-M4 QEMU uses a stand-in for the Zephyr scheduling port |
| UNO WiFi Rev2 | Supported; one carrier | Physical board, or simavr for the AVR language/runtime layer |
| Nano ESP32 | Supported; two carriers by default | Physical board; Xtensa toolchain coverage, no QEMU/simavr stand-in |
| AI Thinker ESP32-CAM | Supported; two carriers by default | Physical board; Xtensa toolchain coverage, no QEMU/simavr stand-in |
| M5Stack Cardputer | Supported; two carriers by default | Physical board; Xtensa toolchain coverage, no QEMU/simavr stand-in |
| Raspberry Pi Pico 2 W | Supported; two carriers by default | Physical board; RP2350 toolchain coverage, no QEMU/simavr stand-in |
| Classic UNO R3 | Not applicable | The board has only 2 KiB SRAM and intentionally exposes no task scheduler; Juno rejects monitor/lock concurrency for it instead of silently weakening the semantics |

Each board-ready example declares its own target set, while the compiler capability covers every scheduler board listed
above. The Arduino CLI integration profile builds and links the generated sketches with each target's real Arduino
core. QEMU and simavr compare generated-code output with OpenJDK where a stand-in exists; real hardware remains the
final check for each scheduler port.
