---
title: "Memory Model"
description: "How a Juno program runs on the microcontroller: its flash and RAM, startup, the stack, the fixed arena and its garbage collector."
layout: page
---

A Juno program is compiled ahead of time into machine code for the board's microcontroller. The board runs that code
directly: there is no JVM, no operating system and no heap that grows. Everything the program needs is laid out in the
chip's two memories when the firmware is built, and only a few regions are used at run time. This page explains that
layout and how the program uses it.

## Where the program lives

The microcontroller has two kinds of memory:

- **Flash** keeps the program's code and its constants. It is written once, when the firmware is uploaded, and it
  costs no RAM.
- **RAM** holds everything the program changes while it runs.

| Region | Memory | What goes there | Lifetime |
|---|---|---|---|
| Code | Flash | The compiled methods of the program and the runtime shim | The whole program |
| Constant data (`.rodata`) | Flash | String literals and `static final` tables with an all-constant initializer | The whole program |
| Static data (`.bss`) | RAM | Mutable `static` fields, one symbol per field | The whole program |
| Arena (fixed 8 KiB) | RAM | Objects, records, arrays, capturing closures, `UUID`, `BigInteger` and `BigDecimal` values, atomic cells | Until the garbage collector reclaims it |
| Stack | RAM | Local variables, operand slots and call frames of the running method | One call |
| String pool | RAM | Results of `String.valueOf`, `StringBuilder.toString()` and similar, in rotating 32-byte slots | Until the slot is reused |
| Task stacks | RAM | One small stack for each extra task (2 KiB on the UNO R4 WiFi, 4 KiB on the UNO Q) | One task |

There is no general heap. Everything the program allocates at run time comes from the arena, whose size is fixed when
the program is built, and no allocation can make it larger.

## Startup

When the board starts, the runtime sets up the stack and the static data, and then runs the static initializers
(`<clinit>`) of the classes the program uses. The initializers run before `main`, and they can only use the supported subset. After that the runtime calls `main`, and the program keeps
running until it returns or the board is reset.

## The stack

Each method call takes a frame on the stack, which holds the method's local variables and its working values. A frame is
released when the method returns. The stack has a fixed size, so a recursion that goes too deep runs out of it. The
compiler reports recursion cycles it can see as `JUNO-RISK-003`.

## The arena

The arena is one fixed block of RAM that holds everything allocated with `new` and the other values listed above. Each
block in the arena carries a small header with the block's size and two flags, one marking it as in use and one marking
it as free. Allocation takes a free block that is large enough. When no block fits, the garbage collector runs first.

### Garbage collector

The arena is reclaimed by a conservative mark-and-sweep garbage collector. It runs automatically, only when an allocation
would otherwise exceed the arena, so the program never has to call it.

A collection has three steps:

1. **Find the roots.** The collector scans the places a running program can keep a reference: the stack of every live
   task, and the reference-valued static fields.
2. **Mark.** Every word it finds is treated as a possible pointer. A word marks a block only if it points exactly at the
   start of an allocated block, and the blocks it marks are scanned in turn, so everything reachable from a root is
   marked.
3. **Sweep.** Unmarked blocks go back to the free list, and the allocation that triggered the collection continues.

Because the collector has no type information, it cannot tell a pointer from an integer that happens to look like one.
That has two consequences:

- **Live objects are never freed.** An object still reachable from a root keeps its address for as long as it lives.
- **Garbage can survive.** An integer that matches the address of a dropped block keeps that block alive until the
  integer changes. This is rare, and it only delays reclaiming memory.

The collector does not move objects, so an object's address never changes and identity comparisons behave as in Java.

Practical effects for your programs:

- **Dropped objects are reclaimed.** A program that creates temporary objects inside a loop does not run out of memory,
  as long as each object is unreachable before the next one needs the space.
- **Objects that are alive at the same time must fit.** If the live objects at one moment need more than the arena,
  no collection can help, and the program stops.
- **Keep allocation out of hot loops.** Allocating in a loop triggers collections often, and the compiler reports it as
  `JUNO-RISK-001`. Create the objects once at startup and reuse them.
- **Timing is not controlled.** A collection happens when an allocation needs space, so a program cannot choose when it
  runs. Allocate what you need at startup, so the arena is not under pressure during time-sensitive code.

When the arena is full even after a collection, the program stops with a diagnostic on the serial port. There is no
`OutOfMemoryError` to catch.

## Arrays and constants

Arrays are fixed-size and allocated from the arena. A `static final` array whose initializer is all constants is placed
in flash instead, which keeps large lookup tables out of RAM. A table that cannot be placed in flash falls back to the
arena, and the build reports why (`JUNO-RISK-012`).

This table estimates how many arrays fit in the arena on each board. An array costs its element size times its length,
plus the 4-byte header of its block, so the number that fit is the arena size divided by that cost. The arena is shared
with the program's objects and strings, and the static data and stacks take their own share of the RAM, so these are
upper limits.

| Board | Flash (total) | SRAM (total RAM) | Arena (default) | `int[100]` arrays that fit (404 bytes each) | `byte[1000]` arrays that fit (1004 bytes each) |
|---|---|---|---|---|---|
| UNO R3 | 32 kB (0.5 kB for the bootloader) | 2 kB | 1 KB | 2 | 1 |
| UNO WiFi Rev2 | 48 kB | 6 kB | 1 KB (up to 3 KB with `juno.Xmx=3k`: 7 and 3) | 2 | 1 |
| UNO R4 WiFi | 256 kB | 32 kB | 8 KB | 20 | 8 |
| UNO Q | 2 MB | 786 kB | 8 KB | 20 | 8 |
| Nano ESP32 | 16 MB (external) | 512 kB (plus 8 MB PSRAM) | 8 KB | 20 | 8 |
| M5Stack Cardputer | 8 MB | 512 kB | 8 KB | 20 | 8 |

Flash holds the program's code as well as its constant tables, so the room left for `static final` tables is smaller
than the total. The linker report shows what each build uses. On the UNO Q the sketch partition is also smaller than the
full flash, as the board pages describe.

## Build-time checks

The compiler looks at the program's memory use before it is built, and reports what it finds as `JUNO-RISK-*`:

- `JUNO-RISK-001`: allocation inside a loop.
- `JUNO-RISK-002`: a startup allocation estimate larger than the arena.
- `JUNO-RISK-003`: a recursion cycle with unbounded depth.
- `JUNO-RISK-012`: a constant table kept in the arena instead of flash.

These are estimates, so treat them as warnings rather than proof. The [Maven Plugin](../juno-maven-plugin#runtime-risk-inspection)
page explains how they are reported.

## Juno flags

Four `juno.*` properties set the memory that a program is built with. Each one changes how much RAM the program can use,
never what the program means. The [Maven Plugin](../juno-maven-plugin#juno-flags) page gives their syntax and limits.

| Flag | Sets | Memory effect |
|---|---|---|
| `juno.Xmx` | The arena | A larger arena holds more objects, arrays and strings at once, and takes that much more RAM from the static data and the stacks. |
| `juno.Xss` | The stack of each extra task | Each extra task reserves this much RAM, so the total grows with the number of tasks. |
| `juno.maxThreads` | The number of scheduler slots, including `main` | Each slot beyond the first has its own task stack, so fewer slots save RAM. |
| `juno.gcLog` | Garbage-collector output | Prints the arena's use before and after each collection, to help size `juno.Xmx`. |

Set `juno.Xmx` to the largest live set the program needs, plus some margin, and check the result with `juno.gcLog`. Lower
`juno.maxThreads` or `juno.Xss` when the RAM budget below is close to the limit.

## Concurrency by board

How many virtual threads a program can run depends on the board's scheduler slots and on the RAM each extra task's stack
takes. The default is four slots, including `main`, so a program can have three extra virtual threads running beside
`main`. The `juno.maxThreads` flag changes the number of slots; the Maven Plugin page explains the flags.

| Board | SRAM (total RAM) | Virtual threads | At once, including `main` | Extra threads beside `main` | Stack per extra thread | Structured concurrency and locks |
|---|---|---|---|---|---|---|
| UNO R3 | 2 kB | Yes | 4 | 3 | 512 bytes | No |
| UNO WiFi Rev2 | 6 kB | Yes | 4 | 3 | 512 bytes (up to 1 KB) | Yes |
| UNO R4 WiFi | 32 kB | Yes | 4 | 3 | 2048 bytes | Yes |
| UNO Q | 786 kB | Yes | 4 | 3 | 4096 bytes | Yes |
| Nano ESP32 | 512 kB (plus 8 MB PSRAM) | Yes | 4 | 3 | 8192 bytes | Yes |
| M5Stack Cardputer | 512 kB | Yes | 4 | 3 | 8192 bytes | No |

- The stacks of the three extra threads take their share of the SRAM. On the UNO R3 they take 1.5 kB of its 2 kB, which
  leaves little for the arena and the statics, so the R3 is the board where threads cost the most RAM.
- Starting a thread beyond the number of slots stops the program. Raise the slots with `-Djuno.maxThreads=<n>` if the
  program needs more threads at once; the RAM each extra slot takes comes from the same budget as the arena.
- Structured concurrency (`StructuredTaskScope`, `ScopedValue`) and `ReentrantLock` need the structured concurrency
  capability. On the UNO R3 and the M5Stack Cardputer, use `synchronized` and atomics instead.
- The stack sizes are the defaults. `-Djuno.Xss` changes them, within the limits the Maven Plugin page describes.

## RAM budget

The arena is only one share of the board's RAM. The static data, the task stacks, the core's USB and networking runtime,
and optional libraries such as `ArduinoBLE` and `WiFiS3` take the rest. On the UNO R4 WiFi, with 32 KB in total,
combining Bluetooth, Wi-Fi and several task stacks can run out of room at link time. When it does, the linker reports
that `.heap` does not fit in `RAM`. Reduce the number of forks, or drop an optional shield or library, and build again.
