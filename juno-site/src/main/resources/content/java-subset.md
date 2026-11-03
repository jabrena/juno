---
title: "Java subset"
description: "The Java language Juno compiles for the board, grouped by topic, with what is not supported and where to find the full list."
layout: page
---

Juno compiles a practical subset of Java for microcontrollers. Your program is still ordinary Java, compiled with
`javac`. Juno then checks everything the program reaches and links only that code into the board's firmware. If the
program uses something outside the subset, Juno stops at link time with a diagnostic that names the method and the
operation, so nothing fails silently on the board.

This page summarises the subset by topic. The [Feature Inventory](../features) lists every supported item in full, and

## Data types

### Primitives data types

Juno keeps Java's data types and their sizes. The table shows how each one is stored on the board:

| Java type | Size | On the board | Supported boards |
|---|---|---|---|
| `boolean` | 1 byte in arrays | `int8_t` in arrays, held in a register otherwise | All boards |
| `byte` | 8 bits | `int8_t` | All boards |
| `char` | 16 bits, unsigned | `uint16_t` | All boards |
| `short` | 16 bits | `int16_t` | All boards |
| `int` | 32 bits | `int32_t`, with Java's wrap-around arithmetic | All boards |
| `long` | 64 bits | `int64_t`, held as two 32-bit halves while it is computed | All boards |
| `float` | 32 bits | IEEE 754 single precision, with Java's NaN and conversion rules | All boards |
| `double` | 64 bits | IEEE 754 double precision, with Java's rules | All boards |

Primitive values are kept in registers or in the program's static storage.

Some types behave differently from the JVM on purpose: an arena object lives as long as it is reachable, not until the
JVM's own heap collects it, and very large values do not fit the arena.

### Complex data types

- **String** is either a literal or a runtime value of up to 31 UTF-8 bytes; a longer runtime string stops the program.
  Supported: `String.valueOf(int)`, `length()`, `charAt(int)` and concatenation with `+`.
- **BigInteger** and **BigDecimal** give arbitrary precision, with results that match the JDK. Their size is limited by
  memory, not by a fixed width. Print them with `Serial.println(value.toString())`.
- **UUID** is an immutable 128-bit value. `UUID.randomUUID()` works on every board; the classic UNO R3 and M5Stack
  Cardputer use a weak, non-cryptographic source there.
- **LocalDateTime** comes from `LocalDateTime.now()`, synchronised over SNTP on boards with Wi-Fi. It returns
  `LocalDateTime.MIN` when the clock is unavailable.

## Arrays

- Arrays of `boolean`, `byte`, `char`, `short`, `int`, `long`, `float` and `double`, created with `new T[N]` and a
  compile-time size. They live in the board's fixed arena at their natural width.
- Bounds-checked `arr[i]`, `arr[i] = v` and `arr.length`, for arrays assigned once in the method that creates them.
  Juno proves many bounds checks at compile time, so they cost nothing at run time.
- Read-only tables in flash: a `static final` primitive array with a constant initializer is stored in flash rather than
  RAM, so a large lookup table does not use up the arena.

```java
static final short[] SINE = {0, 804, 1608, 2410 /* ... */};

static int sine(int index) {
    return SINE[index & 3];   // the index is masked, so the bounds check is proved at compile time
}
```

## Program entry points

A program starts in its `main` method. This complete program prints a message over USB serial once a second:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public class HelloWorld {

    public static void main(String[] args) {   // args is empty on the board
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);                     // give the serial monitor time to connect

        while (true) {
            Serial.println("Hello, World!");
            Delay.millis(1000);
        }
    }
}
```

## Object-oriented programming

Reference types point into the board's arena. Objects are allocated in the arena with `new`, and their storage is
reclaimed by the garbage collector when nothing refers to them. Records are arena objects, and enums are ordinal
integers. The subsections below explain each case.

### Objects and interfaces

`new` allocates an object in the arena. Once nothing refers to an object, the garbage collector reclaims its storage, so
the arena is reused rather than exhausted by objects that have been dropped.

- Classes with constructors, fields, inherited methods, overriding and virtual dispatch over the subclasses the program
  reaches.
- Interfaces implemented by final classes, including inherited and default methods.
- Objects live in the arena. A conservative garbage collector reclaims objects no longer reachable, so long-running
  programs do not run out of memory just because they allocate over time. Objects that are alive at the same moment must
  still fit in the arena.

### Enums

Enums are plain ordinals, with `ordinal()`, `values()`, equality and `switch`. An enum can give each constant one
compile-time integer value through its constructor, and Juno emits an immutable lookup for it.

### Records

Records, with canonical and compact constructors and custom accessors. A record is an arena object like any other class.

### Sealed classes

A sealed class or interface lists, with `permits`, the only classes allowed to extend or implement it. The compiler
checks that list, so every subclass is known before the program runs. Sealed types are often paired with `switch`,
where the compiler can see that every case is covered:

```java
public sealed interface Shape permits Circle, Square {}

public record Circle(int radius) implements Shape {}

public record Square(int side) implements Shape {}
```

Juno links the program as a closed world, so it already sees every subclass the program reaches. A sealed hierarchy
should therefore behave like an ordinary class hierarchy on the board. Juno has no example or test that uses sealed
types yet, so this page does not list them as supported.

### Static methods

Static methods take and return primitive values, and their calls are resolved when the program is linked. Methods the
program never calls are left out of the firmware, so unused code does not take up space on the board.

## Control flow

Programs use Java's control flow and numeric rules unchanged. The subsections below explain each part.

### Local variables, arithmetic and loops

Local variables, constants, arithmetic, bitwise operations, shifts, comparisons, conditionals and loops all work as
they do in Java. Integer arithmetic wraps around at 32 bits, and division overflow behaves as on the JVM: the result of
`Integer.MIN_VALUE / -1` is `Integer.MIN_VALUE`.

```java
int total = 0;
for (int i = 1; i <= 10; i++) {
    if ((i & 1) == 0) {
        total += i * i;
    }
}
```

### Long, double and float

`long` and `double` values take two slots in the generated code, and their arithmetic is 64-bit. `float` follows Java's
rules for NaN and for conversions: converting a `float` or `double` to an integer saturates at the integer's limits, and
converts NaN to zero.

```java
long population = 3000000000L;      // too big for an int, but fine as a long
double ratio = population / 3.0;    // double arithmetic
```

### Constants

`private static final` primitive constants (`boolean`, `byte`, `char`, `short`, `int`, `long`, `float` and `double`) are
inlined by `javac` into the code that uses them. They cost nothing at run time, and the value is fixed when the program
is compiled.

```java
private static final int BLINK_MILLIS = 500;
```

## Exceptions

`throw`, `try`, `catch` (including multi-catch), `finally` and try-with-resources are supported, including an exception
that leaves a method and is caught by a caller. Common exception classes such as `IllegalArgumentException`,
`ArithmeticException` and `IOException` are available, and an uncaught exception prints a message on `Serial` and stops
the program.

## Lambdas and method references

Lambdas and method references compiled by `javac` work, including capturing lambdas and `Runnable`:

```java
Runnable blink = () -> led.high();
blink.run();
```

## Concurrency

### Virtual threads

- `Thread.startVirtualThread(Runnable)`, `join()`, `isAlive()`, `Thread.sleep` and `Thread.yield()`.
- `interrupt()` and `isInterrupted()` on a virtual thread. An interrupt ends a `sleep`, `wait`, `await` or `acquire` that
  the thread is blocked in with `InterruptedException`, as on the JDK.
- At most four threads run at once, including `main`.

### Structured concurrency

- `StructuredTaskScope` (JDK 27 preview) and `ScopedValue`.

### Synchronisation

- `synchronized` blocks, `volatile` fields, `ReentrantLock` (`lock`, `tryLock` and `unlock`), and the atomic classes
  `AtomicInteger`, `AtomicBoolean` and `AtomicLong`.
- `Object.wait()`, `notify()` and `notifyAll()`, only while the monitor is held (inside `synchronized`). `wait(long)` is
  not supported.
- `CountDownLatch(int)` with `countDown()`, `getCount()` and `await()`, and `Semaphore(int)` with `acquire()`,
  `release()`, `tryAcquire()` and `availablePermits()`. Timed waits and fairness are not supported.

## Concurrency behaviour

Virtual threads, structured subtasks and `main` share one preemptive scheduler, and the runtime switches between them in
1 ms time slices. A subtask is scheduled exactly like a virtual thread; the structured scope only adds lifecycle
management. Only one task runs at any moment.

- **Every task is preemptive.** When a slice is over, the scheduler takes the processor away from the task. On the UNO R4
  WiFi and the UNO Q a periodic tick can switch a task between any two instructions. On the UNO WiFi Rev2 a task is
  switched at loop back-edges and method entries instead, so a loop with no calls is still switched.
- **Tasks give up the processor when they block.** That happens on `Delay.millis`, `Thread.sleep`, `Thread.yield`,
  `join`, entering a monitor that another task holds, and while a hardware call waits for its device: an HTTP or HTTPS
  request, `HttpServer.accept`, the retries of `SdCard.begin`, and a Powered Up scan or reply. Other tasks run during
  those waits. Each device is used by one task at a time, so a second task that needs it waits its turn.
- **The runtime itself is never interrupted part-way.** Allocation, the garbage collector, throws, monitors, atomics and
  hardware calls each run to completion, so the arena and the pending-exception slot need no locks.

Because tasks switch at arbitrary points, write shared code as if it ran on the JVM, where tasks run in parallel:

- Protect a shared counter with `AtomicInteger`, `AtomicLong`, `AtomicBoolean`, `synchronized` or `ReentrantLock`. A
  plain `count++` on a shared field can lose updates.
- Use `volatile` or an atomic for a flag that one task sets and another polls.
- Share a subtask's result by reading it after `join`, which is safe on both the board and the JVM.
- A `long` or `double` field is always read and written whole on the Arm boards, so no task sees half of a 64-bit store.

## JDK Library classes

Juno implements a bounded set of JDK classes on top of the board's own runtime:

- `java.math.BigInteger`, `BigDecimal` and `MathContext`, with results that match the JDK.
- `java.lang.Math`, for the common functions.
- `java.util.UUID`, with `UUID.randomUUID()` on every board. The classic UNO R3 and M5Stack Cardputer use a weak,
  non-cryptographic source, so use it there only for best-effort identities.
- `java.util.Properties`, read from a file on the SD card, and read-only `java.io.RandomAccessFile` and
  `InputStream`.
- `java.time.LocalDateTime.now()`, with the time synchronised over SNTP on boards with Wi-Fi.
- `java.lang.StringBuilder`, with `new StringBuilder(int capacity)`, `append(char)`, `append(String)` and `toString()`.
  Give it a small capacity: `toString()` returns a runtime String, which holds at most 31 bytes, so a longer result stops the
  program.
