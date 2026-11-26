---
title: "Juno Internals: how the memory model works in Juno"
description: "An educational tour of how a Juno program uses flash and RAM: the arena, the stack, the runtime, and what primitives, classes, records and virtual threads really cost."
date: 2026-11-23
layout: post
link: /blog/2026-11-23-juno-memory-model-design/
tags: [memory, concurrency]
blogVisible: true
---

On a laptop, a Java developer rarely thinks about memory. The JVM starts with hundreds of megabytes, the heap grows when
it needs to, and the garbage collector cleans up in the background. An Arduino UNO R4 WiFi has **32 KB of RAM** in
total, and the UNO R3 has **2 KB**. That is less memory than one empty `ArrayList` uses on a desktop JVM once you count
its class metadata.

Juno still lets you write Java for these boards: classes, records, lambdas, exceptions, even virtual threads. To make
that work, it organizes memory differently from the JVM. This article explains that design step by step, so you can
predict what a line of Java costs before you upload it to the board.

> This is the educational companion to the [Memory model](../../features/memory-model) reference page. That page has the
> complete tables and flags; this one explains the *why*.

## 1. Two kinds of memory: flash and RAM

A microcontroller has two memories, and they behave very differently:

| | Flash | RAM (SRAM) |
|---|---|---|
| Size on the UNO R4 WiFi | 256 KB | 32 KB |
| Written | Once, when you upload | All the time, while the program runs |
| Survives a reset | Yes | No |
| Holds | Code and constants | Everything that changes |

The rule that follows is simple: **anything that never changes should live in flash where the board and linker allow
it**. That preserves scarce RAM, although flash has its own capacity limit. Juno follows that rule wherever it can.

## 2. How the program is stored

Juno is an *ahead-of-time* compiler. It reads your `.class` files at build time and turns them into machine code for the
board's processor (ARM Thumb-2, Xtensa or AVR). The board runs that code directly: there is no JVM on the board, no
bytecode interpreter, no JIT, and no class loading at run time.

```text
  your .java ──javac──▶ .class ──Juno──▶ assembly (.S) + shim (.cpp) ──arduino-cli──▶ firmware ──upload──▶ flash
```

Once uploaded, the firmware is split into sections:

```text
 FLASH                                   RAM
 ┌──────────────────────────────┐        ┌──────────────────────────────┐
 │ .text   compiled methods     │        │ .bss    mutable static fields│
 │         + Juno runtime       │        ├──────────────────────────────┤
 │         + Arduino core       │        │ arena   objects, arrays,     │
 ├──────────────────────────────┤        │         runtime strings      │
 │ .rodata string literals,     │        │         (fixed size)         │
 │         static final tables  │        ├──────────────────────────────┤
 └──────────────────────────────┘        │ core: USB, Wi-Fi, buffers    │
                                         ├──────────────────────────────┤
                                         │ stack   frames of the running│
                                         │         method ↓ grows down  │
                                         └──────────────────────────────┘
```

Two consequences are worth remembering:

- **A string literal costs no RAM.** `Serial.println("Hello")` reads `"Hello"` straight from flash.
- **A `static final` table with constant values costs no RAM.** A font, a sprite or a lookup table written as
  `static final int[] TABLE = {…}` is placed in flash. If the compiler cannot place it there, it tells you with
  `JUNO-RISK-012`.

There is no class metadata in RAM either. Because the compiler sees the whole program at once (a *closed world*), it
resolves method calls, field offsets and type checks at build time.

## 3. The startup sequence

When the board powers on:

1. The Arduino core initializes the hardware and the C runtime: it zeroes `.bss` and sets up the stack.
2. Juno runs the static initializers (`<clinit>`) of the classes your program uses.
3. Juno calls your `main` method.
4. The program runs until `main` returns or the board is reset. There is no `System.exit` to a shell; the board simply
   keeps running.

## 4. The heap that is not a heap: the arena

On the JVM, `new` takes memory from a heap that can grow up to `-Xmx`. On the board, Juno configures one fixed-size block
of RAM, called the **arena**, for Java allocations. Its size is selected and validated at build time, then reserved by
the generated firmware when it runs. Every `new` takes its memory from it.

| Board | Default arena | Maximum (`juno.Xmx`) |
|---|---|---|
| UNO R3 | 1 KB | 1 KB |
| UNO R4 WiFi | 8 KB | ~25 KB |
| Nano ESP32, ESP32-CAM, Cardputer | 64 KB | ~270–300 KB |
| Raspberry Pi Pico 2 W | 64 KB | ~425 KB |
| Portenta H7 | 64 KB | 896 KiB |

These maxima are build-time ceilings, not promises that an application can use that much RAM. Static data, native
libraries, network buffers, carrier/task stacks and board-specific runtime services consume RAM outside the arena and
reduce the usable budget.

The arena never grows. If you need more, you set it at build time, as you would set `-Xmx` on the JVM:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=... \
  -Djuno.board=arduino-uno-r4-wifi \
  -Djuno.Xmx=12k
```

### How allocation works

The arena is a chain of blocks. Each block starts with a **4-byte header** that stores the block's size and two flags,
*in use* and *free*:

```text
 arena
 ┌────┬──────────┬────┬──────┬────┬────────────────┬──────────────────────┐
 │hdr │ Point    │hdr │ int[]│hdr │ (free)         │ not yet used …       │
 └────┴──────────┴────┴──────┴────┴────────────────┴──────────────────────┘
   4 B              4 B        4 B
```

`new` looks for a free contiguous block that is large enough. If there is none, the garbage collector runs first;
fragmentation can prevent a large allocation even when the total free space would appear sufficient.

### The garbage collector

Juno uses a **conservative mark-and-sweep** collector:

1. **Roots.** It scans active carrier stacks, saved continuations, scheduler metadata and reference-valued static
   fields.
2. **Mark.** Every 4-byte word it finds is treated as a *possible* pointer. If the word points at the start of an
   arena block, that block is alive, and it is scanned in turn.
3. **Sweep.** Every unmarked block goes back to the free list.

"Conservative" means the collector has no type information: an `int` that happens to equal an object's address keeps
that object alive. This is rare and only delays freeing memory; it never frees something you are still using. The
collector also never *moves* objects, so an object's address never changes.

What the JVM gives you and Juno does not:

- **No `OutOfMemoryError`.** If the objects that are alive at the same moment do not fit in the arena, the program stops
  with a message on the serial port. There is nothing to catch.
- **No background GC.** The collector runs only when an allocation needs space, inside that `new`. A `new` in a
  time-critical loop can pause the loop.

That is why Juno programs follow one rule: **allocate at startup, reuse in the loop.** The compiler warns you with
`JUNO-RISK-001` when it sees an allocation inside a loop.

## 5. The stack

Each method call pushes a **frame** on the stack: its local variables, the values it is computing, and the return
address. Primitive locals use stack slots or registers while the method is active. They do not become arena
allocations, and their stack space is released when the frame returns.

The carrier/task stack has a fixed size, set with `juno.Xss`. On a single-carrier board, virtual threads share the Juno
carrier stack while `main` retains its native Arduino stack. On a dual-carrier board, each active task slot gets a native
stack from the board's heap.

| Board | Carrier/task stack (default) |
|---|---|
| UNO R3 | 512 bytes |
| UNO R4 WiFi | 2 KB |
| Raspberry Pi Pico 2 W | 4 KB |
| ESP32 boards | 8 KB |
| Portenta H7 | 8 KB |

512 bytes is not much: a method with a dozen `int` locals and some temporary values takes roughly 64 bytes, so a few
nested calls fill it. Deep recursion is the classic way to overflow it, and the compiler reports recursion cycles it
can see as `JUNO-RISK-003`.

## 6. The runtime

The **runtime** is the C++ code that Juno adds to every program (the "shim"). It provides what the generated code cannot
do alone: the allocator and garbage collector, `String` operations, exception dispatch, the thread scheduler and the
calls into the Arduino libraries (Wi-Fi, SD card, displays…).

The runtime is modular. A program that never uses HTTP does not get the HTTP helpers, so it does not pay for them in
flash. The board's own libraries are a different matter: Wi-Fi, TLS and Bluetooth stacks reserve their own RAM, outside
the arena. On the UNO R4 WiFi, combining Bluetooth, Wi-Fi and several threads can run out of RAM at link time even when
your Java code is small.

## 7. What things cost

Now the practical part. All the numbers below describe Juno's compiler layout rules, not the complete firmware
footprint. They are useful for budgeting Java allocations, but the Arduino core, native libraries, buffers, stacks,
alignment and linker report determine whether the complete program fits. A *word* is 4 bytes on every board Juno
supports.

### Primitive types

Where a primitive lives decides what it costs:

| Where | `boolean`, `byte`, `char`, `short`, `int`, `float` | `long`, `double` |
|---|---|---|
| Local variable | One stack slot (or a register), released at return | Two slots |
| Field of an object | **One word (4 bytes)** | Two words (8 bytes) |
| Element of an array | `boolean`/`byte`: 1 byte, `char`/`short`: 2, `int`/`float`: 4 | 8 bytes |
| `static final` compile-time constant | 0 bytes: `javac` inlines it | 0 bytes |

Two surprises for JVM developers:

- **A `byte` or `boolean` field is not smaller than an `int` field.** Juno gives every field a full word. If you need
  many small values, use a `byte[]` or pack bits into an `int`.
- **`long` and `double` cost twice as much** everywhere, and on the 8-bit UNO R3 they are also slow, because every
  operation becomes several instructions.

### Arrays

An array costs a 4-byte block header, a 4-byte length word, and its elements rounded up to 4 bytes:

| Array | Bytes in the arena |
|---|---|
| `new byte[10]` | 4 + 4 + 12 = **20** |
| `new int[100]` | 4 + 4 + 400 = **408** |
| `new long[100]` | 4 + 4 + 800 = **808** |

The UNO R4 WiFi's default 8 KB arena holds about twenty `int[100]` arrays and nothing else; the 64 KB arena of the
ESP32, Pico 2 W and Portenta boards holds about 160.

### Classes

An object costs the 4-byte block header, its fields, and sometimes a type header:

| Part | Bytes | When |
|---|---|---|
| Block header | 4 | Always |
| Type id | 4 | Only when the class takes part in polymorphic dispatch (an overridden method or interface is called on it) |
| Exception header | 8 | Exceptions: their class id and their message |
| Fields | 4 each (8 for `long`/`double`) | Inherited fields come first |

There is no per-object lock word and no identity hash in the header. Compare with HotSpot, where an object starts with a
12- or 16-byte header before its first field.

```java
final class Sensor {          // no subclasses, no interface calls → no type id
    int pin;                  // 4
    boolean enabled;          // 4 (a full word)
    long lastReadMillis;      // 8
}                             // 4 (block) + 16 (fields) = 20 bytes in the arena
```

### Records

A record is a final class with final fields, so it follows the same rules:

```java
record Point(int x, int y) {} // 4 (block) + 4 + 4 = 12 bytes
```

An 8 KB arena could hold about 680 `Point` objects if nothing else used it, and a 64 KB arena about 5,400.

Records have one advantage: they are small, final-field objects, and that is exactly what Juno's optimizer looks for.
When Juno can prove that an object never *escapes* the method that creates it (it is not stored, passed to a call,
returned or captured), it may apply **scalar replacement**: the fields become local variables and the allocation
disappears. This is an optimization to confirm in the compiler output, not a guarantee for every record.

```java
for (int i = 0; i < 100; i++) {
    Point p = new Point(i, i * 2);   // never escapes...
    total += p.x() + p.y();          // ...so no arena allocation at all
}
```

The code still reads like normal Java, but the loop does not allocate and the collector never runs. (Objects with a
`long` field are not replaced today.)

### Lambdas

| Lambda | Cost |
|---|---|
| Captures nothing: `() -> led.toggle()` on a static | 0 bytes: it becomes a plain function in flash |
| Captures values: `() -> count + offset` | One arena object: 4 (block) + 4 + 4 per captured value |

### Strings

| String | Where | Cost |
|---|---|---|
| Literal `"Hello"` | Flash | 0 bytes of RAM |
| Built at run time: `"T=" + t`, `String.valueOf(x)`, `sb.toString()` | Arena | 4-byte block header + stored runtime bytes + terminator, rounded by the allocator |

Runtime strings are stored as compact bytes, not as UTF-16 `char` arrays. Their cost is one byte per stored runtime byte,
plus the terminator and allocator overhead, rounded by the allocator. Even so, a concatenation inside the main loop
allocates every time, so prefer a `StringBuilder` created once at startup, or print the parts one by one.

## 8. Virtual threads and structured concurrency

Juno runs Java threads, including virtual threads and `StructuredTaskScope`, without operating-system threads behind
each one. The design depends on the board.

### Carriers

A **carrier** is a native execution context with its own stack. A virtual thread *mounts* on a carrier to run, and
*unmounts* when it blocks (`sleep`, waiting on a lock, waiting for a subtask…).

- **Single-carrier boards** (UNO R4 WiFi, and any board built with `juno.carriers=1`): virtual threads take turns on one
  Juno carrier stack; `main` retains its native Arduino stack. When a virtual thread parks, the runtime **copies its live
  frames into a continuation chunk in the arena**, and the next thread runs on the carrier. A parked thread therefore
  costs only the stack it actually used, not a full reserved stack.
- **Dual-carrier boards** (Nano ESP32, ESP32-CAM, Cardputer, Pico 2 W): threads can run concurrently on both cores, up
  to 16 slots, and each used slot's native stack is taken from the board's heap.
- **UNO R3**: four fixed slots including `main`, 512 bytes of stack each. Three extra threads take 1.5 KB of its 2 KB.
- **Portenta H7**: `main` runs on the Cortex-M7. With its default two-carrier runtime, new thread slots are placed on the least-loaded M7
  or M4 carrier and remain pinned there, so eligible workloads can execute concurrently on both cores. Juno builds a
  second M4 carrier image beside the M7 sketch; it supplies M4-side Mbed services while Java code and the shared
  runtime remain in the M7 program image's flash. The cores coordinate through hardware semaphores and request rings.
  M4 threads can execute Java and supported local runtime operations, while calls requiring M7-owned Arduino/Mbed
  libraries are forwarded to an M7 worker. M4 `double` arithmetic uses software routines because the M4 has no
  double-precision FPU.

### What a thread costs

| Item | Runtime payload bytes |
|---|---|
| Thread handle (state, result, failure) | 28 |
| `StructuredTaskScope` and its task table | 40 |
| A parked thread's continuation (single carrier) | The frames it had on the stack when it parked |
| A thread slot's native stack (dual carrier) | `juno.Xss` from the board heap, not the arena |

The thread and scope figures are payload estimates. Add the arena's 4-byte block header, then account for captured
runnables, task tables, continuations, alignment and any native task resources.

A concrete consequence: on the UNO R4 WiFi, a thread that parks deep inside an HTTP call parks with a large stack
picture, and that copy lives in the arena until the thread resumes. Programs that do network I/O on virtual threads may
need a larger carrier stack (`-Djuno.Xss`) and a carefully balanced arena.

### Structured concurrency is a memory feature too

`StructuredTaskScope` is more than a nicer API. Because every subtask must finish before the scope closes, the
subtasks' handles and continuations have a clear end of life. When the scope closes and the program retains no
references to its tasks, captures or results, those arena objects become eligible for the next collection. With
unstructured threads, a forgotten thread keeps its handle and its stack alive for the rest of the program.

```java
try (var scope = StructuredTaskScope.open()) {        // 40 bytes
    var temperature = scope.fork(() -> sensor.read()); // 28 bytes + continuation while parked
    var humidity    = scope.fork(() -> hygro.read());  // 28 bytes + continuation while parked
    scope.join();
    show(temperature.get(), humidity.get());
}                                                       // eligible for the next collection when unreachable
```

## 9. Checking your program before it reaches the board

The compiler estimates the memory a program will need and prints it on every build:

- A **Runtime configuration** block: the board, the arena, the stack, the carriers.
- An **arena estimate**, `Arena: estimated / capacity`, and the risk findings: `JUNO-RISK-001` (allocation in a loop),
  `JUNO-RISK-002` (startup allocations larger than the arena), `JUNO-RISK-003` (recursion), `JUNO-RISK-011` (a thread
  that may overflow its stack), `JUNO-RISK-012` (a table that could not go to flash).

At run time, `-Djuno.gcLog=true` prints one serial line per collection, with the arena use before and after, and
`MCU.getBoardMemory()` lets the program read the same figures itself.

## Summary

| JVM habit | Juno on a microcontroller |
|---|---|
| The heap grows up to `-Xmx` | The arena is fixed at build time (`juno.Xmx`) |
| Allocate freely, the GC handles it | Allocate at startup, reuse in the loop |
| `OutOfMemoryError` can be caught | The program stops with a serial message |
| Classes are loaded at run time | Everything is resolved at build time; code and constants live in flash |
| Object headers of 12–16 bytes | 4 bytes, plus 4 only for polymorphic classes |
| `byte` fields are compact | Every field is a word; use arrays for compact data |
| One OS thread per platform thread | Virtual threads on one or two carriers; parked frames saved in the arena |

Memory on a microcontroller is small but predictable. When you know where each value lives, you can size a Juno program
for its selected board and arena, and keep it running for months.
