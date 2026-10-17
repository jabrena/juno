---
title: "Memory Model"
description: "Where Juno programs keep their data, how the arena and its garbage collector work, and what concurrent tasks can observe compared with the JVM."
layout: page
---

A Juno program is compiled ahead of time to Thumb-2 assembly, so there is no JVM, no heap that grows, and no
operating-system scheduler underneath it. This page explains the two halves of the resulting memory model: **where
data lives** and **what concurrent tasks can observe**. The [Known Limitations](/limitations) page lists the places
where the behavior differs from the JVM; this page explains why. The [JLS 17 Conformance](/jls-17-conformance) page
maps every section of the Java Memory Model to a focused source example and its board coverage.

## Where data lives

| Region | What goes there | Lifetime |
|---|---|---|
| **Arena** (fixed 8 KiB) | Objects, records, arrays, capturing closures, `UUID`, `BigInteger` / `BigDecimal` values, atomic cells, task and scope handles | Garbage-collected |
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

The public API is virtual threads (`Thread.startVirtualThread`, `join` and `isAlive`) and `StructuredTaskScope`;
`new Thread` and the other `java.lang.Thread` members, apart from `sleep` and `yield`, are rejected at compile time.
There is exactly one running task at any moment, and each gets the processor in **time slices** of 1 ms. The two
kinds of task differ in how a slice ends:

- **Virtual threads, and `main`, are preemptive.** The scheduler takes the processor away when the slice is over,
  whatever the thread is doing (see below for each board).
- **`StructuredTaskScope` subtasks are cooperative.** A subtask is never interrupted part-way through a statement: once
  its slice is over it gives the processor up at its next loop back-edge, or earlier at one of the blocking points
  below. A subtask that runs a long stretch without a loop (a deep recursion, say) keeps the processor until it
  reaches one.

Every task also gives the processor up early when it blocks:

- `Delay`, `Thread.sleep` and `Thread.yield`,
- a scope's or a virtual thread's `join`,
- entering a monitor that another task holds,
- a Powered Up call waiting for the hub: scanning for it, or waiting for its reply.

How a preemptive thread's slice ends depends on the board:

- **UNO R4 WiFi and UNO Q** (Arm Cortex-M): a periodic tick (`SysTick` on the R4, a Zephyr timer on the Q) interrupts
  the task. If it was running compiled Java, it is switched out right there, between any two instructions, inside a
  loop or a recursion or straight-line code alike; every register, the flags and the FPU state are saved and restored
  around the switch.
- **UNO WiFi Rev2** (AVR): an 8-bit core stores a reference or an `int` a byte at a time, so a thread cannot be stopped
  at an arbitrary instruction. It is preempted at **safepoints** instead: every loop back-edge and every method entry.
  A spinning loop and a loop-free recursion are both preempted once their slice is over.

The runtime itself is never preempted part-way: an allocation, a collection, a throw, a monitor or atomic operation,
and every hardware call run to completion (a hardware call that waits for a device lets the others run while it
waits, see below), and a tick that lands in one only marks the switch as due. That is why the
arena, the collector and the pending-exception slot need no locks.

### Visibility and ordering

With one task running at a time and no caches or store buffers to reorder anything, a task always sees the latest
write of every other task, and operations happen in program order. In practice the model is **sequentially
consistent**. Java's own memory model is weaker than that, so the rule for portable code is simple: a program that
is correct under the Java Memory Model is correct on Juno.

The converse does not hold. A data race can be invisible on a board and still be a bug on the JVM, where tasks run in
parallel on separate cores. Write the code as though it ran on the JVM:

- A virtual thread can be switched between any two instructions of compiled Java, and a subtask at any loop
  back-edge, so `count++` on a shared field is a lost update waiting to happen. Do not rely on a subtask being
  cooperative for atomicity: on the JVM it runs in parallel. Use `AtomicInteger`, `AtomicLong` or `AtomicBoolean`, or
  `synchronized`/`ReentrantLock`.
- Use `volatile` or an atomic for a flag one task sets and another polls. The JVM needs it, and on the board it keeps
  the compiler from caching the field in a local.
- `long` and `double` fields are always read and written whole on the Arm boards (the JLS only promises that for
  `volatile` ones), so another task never sees half of a 64-bit store.
- Share results through `join`. Reading a subtask's result after the scope has joined is safe on both.

`AtomicReference` is not supported.

### Hardware calls let the other tasks run while they wait

A call into the hardware runtime is never interrupted part-way, but when it has to **wait** for a device it gives the
processor to the other tasks meanwhile, as a blocking call on the JVM stalls only the thread that made it:

- an HTTP or HTTPS request, while it waits for the response (up to its 5 s deadline);
- `HttpServer.accept`, while it waits for the request;
- `SdCard.begin`, between its retries;
- `PoweredUpHubRemote`, while it scans for a hub or waits for the hub's reply.

So a forked task can keep animating a display or drawing a progress line during a request. Each device is used by
one task at a time: a second task that calls into the network (or the SD card, or the hub) while another is waiting
there waits its turn, like a `synchronized` method, while tasks that do not touch that device keep running. If a
`StructuredTaskScope` cancels a subtask in the middle of such a call, the runtime closes the connection the subtask
left open and frees the device, so the next request works.

On the **UNO Q** the network is Linux's, reached through Arduino_RouterBridge, and every network call waits inside
that library for Linux to answer. Such a call runs on an **I/O worker**, a Zephyr thread of the runtime's own, while
the calling task is parked: `Wifi.begin` (starting the bridge), the whole of an HTTPS request including `connect` and
the TLS handshake, and `HttpServer.accept` and `respond` all leave the other tasks running. The text a request still
needs is copied before it is handed over, and a task cancelled meanwhile keeps its arrays alive until the worker is
done with them.

Some waits still hold every task, because they happen inside a vendor library with no worker to run them:

- on the UNO R4 WiFi and the UNO WiFi Rev2, `Wifi.begin`, opening a connection (`connect`) and the TLS handshake;
- on the UNO Q, the UDP calls (`Udp`), each a short round trip to Linux;
- on every board, the email clients (`Smtp`, `Pop3Client`), which keep using their arguments after each reply from the
  server;
- the second or so that ArduinoBLE spends connecting to a hub it has found and discovering its services.

Keep those out of the moments when other tasks must make progress.

See the `PoweredUpHubScanWhileBlinking` example.

## How it compares with the JVM

| | JVM platform threads | JVM virtual threads | Juno |
|---|---|---|---|
| Scheduling | The operating system, preemptive | Cooperative at blocking calls, on preemptive carrier threads | 1 ms time slices: virtual threads preemptive, `StructuredTaskScope` subtasks cooperative |
| Parallel execution | Yes | Yes | No, one task at a time |
| Where a switch can happen | Anywhere | Blocking operations | Virtual threads: anywhere in compiled Java (AVR: back-edges and method entries). Subtasks: loop back-edges. Both: `sleep`, `yield`, `join`, contended monitors, and a hardware call's wait (HTTP, server, SD, Powered Up); never part-way through runtime code |
| Memory ordering | Java Memory Model | Java Memory Model | Sequentially consistent |
| A long blocking call | Stalls only its thread | Pins its carrier thread | Request, server, SD and Powered Up waits stall only their task, and on the UNO Q every bridge call but UDP; elsewhere vendor waits (`Wifi.begin`, `connect`, TLS) stall every task |

Juno behaves like platform threads time-sliced on a single core, with the runtime's own calls atomic. That is why `StructuredTaskScope`, which uses virtual threads on the JVM, is the supported way to run work concurrently.

## RAM budget

The arena is only one share of the board's RAM. The statics, the task stacks, the core's USB and networking runtime,
and optional libraries such as `ArduinoBLE` and `WiFiS3` take the rest. On the UNO R4 WiFi, with 32 KB in total,
combining Bluetooth, Wi-Fi and several task stacks can run out of room at link time. When it does, the linker reports
that `.heap` does not fit in `RAM`. Reduce the number of forks, or drop an optional shield or library, and build again.
