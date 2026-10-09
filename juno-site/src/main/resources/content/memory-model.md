---
title: "Memory Model"
description: "Where Juno programs keep their data, how the arena and its garbage collector work, and what concurrent tasks can observe compared with the JVM."
layout: page
---

A Juno program is compiled ahead of time to Thumb-2 assembly, so there is no JVM, no heap that grows, and no
operating-system scheduler underneath it. This page explains the two halves of the resulting memory model: **where
data lives** and **what concurrent tasks can observe**. The [Known Limitations](/limitations) page lists the places
where the behavior differs from the JVM; this page explains why.

## Where data lives

| Region | What goes there | Lifetime |
|---|---|---|
| **Arena** (fixed 8 KiB) | Objects, records, arrays, capturing closures, `BigInteger` / `BigDecimal` values, atomic cells, task and scope handles | Garbage-collected |
| **Static data** (`.bss`) | Mutable `static` fields, one symbol per field | The whole program |
| **Flash** (`.rodata`) | String literals and `static final` tables with an all-constant initializer | The whole program, costs no RAM |
| **Stack** | Local variables, operand slots and call frames | One call |
| **String pool** | Results of `String.valueOf`, `StringBuilder.toString()` and similar, in rotating 32-byte slots | Until the slot is reused |
| **Task stacks** | One small stack for each extra task (2 KiB on the UNO R4 WiFi, 4 KiB on the UNO Q) | One task |

There is no general heap. Everything that is allocated at run time comes from the arena, whose size is fixed at
compile time, and no allocation can make it larger.

### The arena and its garbage collector

Allocation takes a block from the arena. Every block carries a 4-byte header holding a mark bit, a free bit and the
block's size. When an allocation does not fit, a **conservative mark-and-sweep collector** runs:

1. It treats every word on the stack, in the static data and inside each marked object as a *possible* pointer.
2. A word marks a block only if it points exactly at the start of an allocated block.
3. Unmarked blocks go back to the free list.

The collector has no type information and does not move objects, because it cannot tell a pointer from an integer that
happens to look like one. Two consequences follow:

- **Object identity is stable.** An object's address never changes, so identity and `==` behave as in Java.
- **Garbage can occasionally survive.** An integer that looks like a block's address keeps that block alive. A live
  object is never freed.

If the arena is full even after a collection, the program stops with a diagnostic on the serial port. There is no
`OutOfMemoryError` to catch. The compiler reports risks it can see at build time (allocation inside loops, an
estimated startup footprint above the arena size, arrays without a provable bound) as `JUNO-RISK-*` findings. Those
findings are estimates that ignore garbage collection, so treat them as warnings, not as proof.

### Arrays and constants

Arrays are fixed-size and allocated from the arena. A `static final` array whose initializer is all constants is
placed in flash instead, which keeps large lookup tables out of RAM. A table that cannot be placed in flash falls back
to the arena, and the build reports why (`JUNO-RISK-012`).

## What tasks can observe

Juno's concurrency is **cooperative**. The public API is `StructuredTaskScope`; `new Thread` and the other
`java.lang.Thread` members, apart from `sleep` and `yield`, are rejected at compile time. There is exactly one running
task at any moment, and it keeps the processor until it reaches a **switch point**:

- a loop back-edge in compiled Java (a task switch happens at most once per millisecond),
- `Delay`, `Thread.sleep` and `Thread.yield`,
- a scope's `join`,
- entering a monitor that another task holds,
- a Powered Up call waiting for the hub: scanning for it, or waiting for its reply.

The runtime never switches a task in the middle of an allocation, a collection or a throw. That is why the arena, the
collector and the pending-exception slot need no locks.

### Visibility and ordering

With one task running at a time and no caches or store buffers to reorder anything, a task always sees the latest
write of every other task, and operations happen in program order. In practice the model is **sequentially
consistent**. Java's own memory model is weaker than that, so the rule for portable code is simple: a program that
is correct under the Java Memory Model is correct on Juno.

The converse does not hold. A data race can be invisible on a board and still be a bug on the JVM, where tasks run in
parallel on separate cores. Write the code as though it ran on the JVM:

- A task can still be switched between any two loop iterations, so `count++` on a shared field is still a lost update
  across a loop back-edge. Use `AtomicInteger`, `AtomicLong` or `AtomicBoolean`, or `synchronized`.
- Use `volatile` or an atomic for a flag one task sets and another polls, even though both behave the same on the
  board. The JVM needs them.
- Share results through `join`. Reading a subtask's result after the scope has joined is safe on both.

`AtomicReference` is not supported.

### Native calls are atomic, except the Powered Up waits

A call into the hardware runtime (Wi-Fi, SD card, the TFT shield) runs to completion before any other task gets a
turn, however long it takes. This is the main place where behavior differs from the JVM, where a blocking call stalls
only the thread that made it. Keep long hardware calls out of programs that depend on other tasks making progress.

`PoweredUpHubRemote` is the exception. While it scans for a hub or waits for the hub's reply, it lets the other tasks
run, so a forked task can keep drawing a progress line during a `connect`. Two limits remain:

- The second or so that ArduinoBLE spends connecting to a hub it has found, and discovering its services, cannot be
  interrupted.
- Only one task at a time is inside a `PoweredUpHubRemote` call. A second task that calls one meanwhile waits its
  turn, like a `synchronized` method, so the hub never sees two commands interleaved.

See the `PoweredUpHubScanWhileBlinking` example.

## How it compares with the JVM

| | JVM platform threads | JVM virtual threads | Juno |
|---|---|---|---|
| Scheduling | The operating system, preemptive | Cooperative at blocking calls, on preemptive carrier threads | Cooperative only |
| Parallel execution | Yes | Yes | No, one task at a time |
| Where a switch can happen | Anywhere | Blocking operations | Loop back-edges, `sleep`, `yield`, `join`, contended monitors, Powered Up waits |
| Memory ordering | Java Memory Model | Java Memory Model | Sequentially consistent |
| A long blocking call | Stalls only its thread | Pins its carrier thread | Stalls every task (Powered Up waits excepted) |

Juno behaves like virtual threads running on a single carrier, with an extra switch point at each loop back-edge. That
is why `StructuredTaskScope`, which uses virtual threads on the JVM, is the supported way to run work concurrently.

## RAM budget

The arena is only one share of the board's RAM. The statics, the task stacks, the core's USB and networking runtime,
and optional libraries such as `ArduinoBLE` and `WiFiS3` take the rest. On the UNO R4 WiFi, with 32 KB in total,
combining Bluetooth, Wi-Fi and several task stacks can run out of room at link time. When it does, the linker reports
that `.heap` does not fit in `RAM`. Reduce the number of forks, or drop an optional shield or library, and build again.
