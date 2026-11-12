---
title: "Memory Model"
description: "How a Juno program uses the microcontroller's RAM: internal SRAM, the arena, stacks, the runtime reserve, PSRAM, and the limits each board enforces."
layout: page
---

A Juno program is compiled ahead of time into machine code for the board's microcontroller. The board runs that code
directly: there is no JVM, no operating system and no heap that grows. Everything the program needs is laid out in the
chip's memories when the firmware is built, and only a few regions are used at run time. This page explains that
layout, what each board allows, and how to size a program.

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
| Arena | RAM | Objects, records, arrays, capturing closures, `UUID`, `BigInteger` and `BigDecimal` values, atomic cells. Its size depends on the board, see the [board profiles](#board-memory-profiles) | Until the garbage collector reclaims it |
| Stack | RAM | Local variables, operand slots and call frames of the running method | One call |
| String pool | RAM | Results of `String.valueOf`, `StringBuilder.toString()` and similar, in rotating 32-byte slots | Until the slot is reused |
| Carrier and task stacks | RAM | The stack each carrier runs on, and one stack for each extra thread where the board needs one. See [concurrency by board](#concurrency-by-board) | The program |

There is no general heap. Everything the program allocates at run time comes from the arena, whose size is fixed when
the program is built, and no allocation can make it larger.

## Startup

When the board starts, the runtime sets up the stack and the static data, and then runs the static initializers
(`<clinit>`) of the classes the program uses. The initializers run before `main`, and they can only use the supported
subset. After that the runtime calls `main`, and the program keeps running until it returns or the board is reset.

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
upper limits. Every board uses its default arena unless `juno.Xmx` says otherwise.

| Board | Arena (default) | `int[100]` arrays that fit (404 bytes each) | `byte[1000]` arrays that fit (1004 bytes each) |
|---|---|---|---|
| UNO R3 | 1 KB | 2 | 1 |
| UNO WiFi Rev2 | 1 KB (up to 3 KB with `juno.Xmx=3k`: 7 and 3) | 2 | 1 |
| UNO R4 WiFi | 8 KB | 20 | 8 |
| UNO Q | 8 KB | 20 | 8 |
| Nano ESP32 | 8 KB | 20 | 8 |
| AI Thinker ESP32-CAM | 8 KB | 20 | 8 |
| M5Stack Cardputer | 8 KB | 20 | 8 |
| Raspberry Pi Pico 2 W | 8 KB | 20 | 8 |

Flash holds the program's code as well as its constant tables, so the room left for `static final` tables is smaller than
the board's flash. The board pages describe each board's flash, and the linker report shows what a build uses.

## Board memory profiles

Each board has a memory profile in the compiler. It gives the RAM the build may use, the arena and stack sizes it starts
with, and the largest sizes it accepts. The build checks every explicit `juno.Xmx` and `juno.Xss` against these maximums.

| Board | Usable RAM | Arena: default / max | Stack per carrier: default / max | Carriers | What decides whether a larger arena fits | PSRAM |
|---|---|---|---|---|---|---|
| UNO R3 | 2 KB | 1024 / 1024 bytes | 512 / 512 bytes | 1 | The linker | None |
| UNO WiFi Rev2 | 6 KB | 1024 / 3072 bytes | 512 / 1024 bytes | 1 | The linker | None |
| UNO R4 WiFi | 32 KB | 8192 / 25736 bytes | 2048 / 8192 bytes | 1 | The linker | None |
| UNO Q | 256 KB | 8192 / 226928 bytes | 4096 / 16384 bytes | 1 | The loader pool: the sketch's code shares it | None |
| Nano ESP32 | 320 KB | 8192 / 296840 bytes | 8192 / 32768 bytes | 2 | The run-time heap | 8 MB, not used by the arena |
| AI Thinker ESP32-CAM | 320 KB | 8192 / 305216 bytes | 8192 / 32768 bytes | 2 | The run-time heap | Used by the camera frame buffers; no size recorded |
| M5Stack Cardputer | 320 KB | 8192 / 273408 bytes | 8192 / 32768 bytes | 2 | The run-time heap | No size recorded |
| Raspberry Pi Pico 2 W | 512 KB | 8192 / 436176 bytes | 4096 / 16384 bytes | 2 | The run-time heap | None |

- **Usable RAM** is the limit the Arduino toolchain reports for a sketch's static data and arena. It is smaller than the
  chip's total RAM: the core, its radio and networking libraries, and the runtime keep the rest. The UNO Q, for example,
  has 786 KB of RAM in total but offers 256 KB to a sketch.
- **What decides whether a larger arena fits** explains how a too-large arena fails:
  - **The linker** places every section, so a successful link is the final word.
  - **The run-time heap** supplies the memory for Wi-Fi, TLS, Bluetooth, the camera and the extra thread stacks. A
    larger arena can link and then fail at boot or when a network connection starts.
  - **The loader pool** is filled when the sketch loads on the UNO Q, and the sketch's code and its arena share it. The
    usable arena shrinks as the program grows.
- **The maximums are provisional.** Each maximum is the largest arena the linker accepts for a minimal program on that
  board, not the largest a real program can use. On the boards whose limit is not the linker, the real limit is lower,
  and it depends on the program's own memory use. The measured limits will replace these values. Until then, treat the
  maximums as a guard against sizes that cannot work, not as a safe budget.
- **PSRAM is never used for the arena, objects or stacks.** It is external RAM, slower than the internal SRAM, and the
  build does not size anything from it.

## Build-time checks

The compiler looks at the program's memory use before it is built, and reports what it finds as `JUNO-RISK-*`:

- `JUNO-RISK-001`: allocation inside a loop.
- `JUNO-RISK-002`: a startup allocation estimate larger than the arena.
- `JUNO-RISK-003`: a recursion cycle with unbounded depth.
- `JUNO-RISK-011`: a thread body that may use more stack than its board's carrier stack.
- `JUNO-RISK-012`: a constant table kept in the arena instead of flash.
- `JUNO-RISK-014`: on the UNO Q, the arena and static data pass 90% of the loader pool.

When the startup estimate is larger than the arena, the compiler also suggests a larger `juno.Xmx`, capped at the board's
maximum. If the program cannot fit even at that maximum, the suggestion says so.

Every build prints a "Runtime configuration" block before these findings: the board, the arena and whether it is the
board default or a `juno.Xmx` override, the carrier stack, the carrier count, and the PSRAM the board carries.

These are estimates, so treat them as warnings rather than proof. The [Maven Plugin](../juno-maven-plugin#runtime-risk-inspection)
page explains how they are reported.

## Juno flags

Four `juno.*` properties set the memory and carriers a program is built with. Each one changes how much RAM the program
can use, never what the program means. The [Maven Plugin](../juno-maven-plugin#juno-flags) page gives their syntax.

| Flag | Sets | Accepted values | Memory effect |
|---|---|---|---|
| `juno.Xmx` | The arena | A size of at least 512 bytes, a multiple of 8, at most the board's maximum arena | A larger arena holds more objects, arrays and strings at once, and takes that much more RAM from the static data and the stacks. |
| `juno.Xss` | The stack of each carrier (on AVR, of each extra task) | A size of at least 512 bytes, a multiple of 8, at most the board's maximum stack | Each carrier reserves this much RAM. On the ESP32 boards and the Pico 2 W, each thread slot takes it from the run-time heap. |
| `juno.carriers` | How many carriers run threads | `1` or `2`, at most the board's carriers | `1` runs every thread on one carrier. Use it only in test environments that cannot run both cores. |
| `juno.gcLog` | Garbage-collector output | `true` or `false` | Prints one `Serial` line per collection, with the arena's use before and after. |

A size is written as the JVM writes `-Xmx`: bytes, or a number followed by `k`, `m` or `g`. An explicit value is an override
even when it equals the board default, and the build's configuration block says which one was used. A value above the
board's maximum fails the build with a message that names the board, the requested size, the maximum, the board's
internal RAM and the default. For example, `-Djuno.Xmx=1g` on the UNO R4 WiFi fails with a message that starts:

> The UNO R4 WiFi has too little RAM for a 1073741824-byte arena; juno.Xmx can be at most 25736 bytes there (32768 bytes internal SRAM, ... default 8192).

## Sizing a program

1. **Read the configuration block** at the start of the build, and confirm the arena is the size you expect.
2. **Read the arena figure** in the risk report: `Arena: estimated / capacity`. If the startup estimate exceeds the
   capacity, the compiler's suggestion gives a size to try.
3. **Check the live set at run time.** Build with `-Djuno.gcLog=true`, watch the collections on the serial port, and
   raise `juno.Xmx` in steps while the arena stays nearly full after each collection. The program can also read
   `ArduinoUno.getBoardMemory()` (or `ArduinoNano.getBoardMemory()` on the Nano ESP32): `arenaUsedBytes()` and `arenaCapacityBytes()` give the same figures from inside the
   program.
4. **Confirm the build runs.** On the UNO R3 and UNO R4, a link that succeeds is a reliable sign the sizes fit. On the
   Nano ESP32, ESP32-CAM, Cardputer, Pico 2 W and UNO Q, the run-time heap or the loader pool decides, so upload the
   program and watch its output with `juno:monitor` before relying on the size.

## Concurrency by board

How many threads a program can run depends on the board's carriers and on the RAM each thread's stack takes. The table
gives the limit each board applies.

| Board | Limit on threads alive at once | Stack per extra thread (default / max) | Structured concurrency and locks |
|---|---|---|---|
| UNO R3 | Four slots, including `main` | 512 / 512 bytes | No: use `synchronized` and atomics |
| UNO WiFi Rev2 | Four slots, including `main` | 512 / 1024 bytes | Yes |
| UNO R4 WiFi | No slot count: the arena bounds the threads | 2048 / 8192 bytes | Yes |
| UNO Q | No slot count: the arena bounds the threads | 4096 / 16384 bytes | Yes |
| Nano ESP32 | 16 slots on two carriers (the default); four with `juno.carriers=1` | 8192 / 32768 bytes | Yes |
| AI Thinker ESP32-CAM | 16 slots on two carriers (the default); four with `juno.carriers=1` | 8192 / 32768 bytes | Yes |
| M5Stack Cardputer | 16 slots on two carriers (the default); four with `juno.carriers=1` | 8192 / 32768 bytes | Yes |
| Raspberry Pi Pico 2 W | 16 slots on two carriers (the default); no slot count with `juno.carriers=1` | 4096 / 16384 bytes | Yes |

- On the UNO R3 the three extra threads take 1.5 KB of its 2 KB, which leaves little for the arena and the statics. The
  R3 is the board where threads cost the most RAM.
- On the four-slot boards, starting a thread beyond the slots stops the program. No flag raises the slot count.
- On the two-carrier boards each slot's stack comes from the run-time heap only once a thread uses that slot, so a
  program that starts many threads can exhaust the heap at run time even when it links.
- Structured concurrency (`StructuredTaskScope`, `ScopedValue`) and `ReentrantLock` need the structured concurrency
  capability, which every board except the UNO R3 has.

## RAM budget

The arena is only one share of the board's RAM. The static data, the task stacks, the core's USB and networking runtime,
and optional libraries such as `ArduinoBLE` and `WiFiS3` take the rest. On the UNO R4 WiFi, with 32 KB in total,
combining Bluetooth, Wi-Fi and several task stacks can run out of room at link time. When it does, the linker reports
that `.heap` does not fit in `RAM`. Reduce the number of threads, or drop an optional shield or library, and build again.

On the boards whose limit is the run-time heap, the same budget is spent at run time instead, and the failure appears as a
boot loop or a stopped program rather than a link error. Watch the serial output after upload.
