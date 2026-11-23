---
title: "Zero-allocation Java patterns for Arduino with Juno"
description: "Design Juno programs that allocate at startup, reuse buffers and keep the main loop predictable on tiny boards."
date: 2026-11-23
layout: post
link: /blog/2026-11-26-juno-zero-allocation/
tags: [memory, embedded, performance]
blogVisible: true
---

On a desktop JVM, a short-lived object is usually an implementation detail. On a microcontroller, every allocation
competes for a fixed Java arena, and collection cannot create more RAM. Juno programs are easiest to reason about when
they allocate their working set once and reuse it.

## Allocate during startup

Construct drivers, buffers, requests and application state before entering the long-running loop:

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

final class AnalogLoop {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        var gpio = MCU.getGpio();
        int input = gpio.analogPin("A0");

        while (true) {
            int value = gpio.analogRead(input);
            Serial.print("value=");
            Serial.println(value);
            Delay.millis(1000);
        }
    }
}
```

This path uses primitive values and existing runtime state, so it demonstrates zero steady-state Java-arena allocation
after startup. The exact native driver may still allocate its own buffers, which are outside the Java arena.

## Prefer fixed-size data

Use arrays with documented capacities, records for small immutable values and primitive fields for counters and flags.
Avoid growing collections in a loop. If a queue is required, choose a fixed capacity and define what happens when it is
full: reject, overwrite the oldest sample or apply backpressure.

A record created once at startup can be a good immutable configuration value. A record constructed for every sample is
still an allocation, so use primitive fields or an existing array for changing readings.

Strings deserve special care. Literals can live in flash, while constructed strings consume arena space. A reusable
`StringBuilder` is useful, but converting it to a `String` still creates a value that must fit in the arena.

## Reuse I/O state safely

Reusable state must have a clear owner. A response buffer should belong to one HTTP worker; a sensor buffer should not be
written by two tasks without coordination; and a display buffer should be updated by one producer before the display
task consumes it.

Reuse reduces allocation, but it does not remove races. Virtual threads make it easy to overlap work, so ownership and
publication must be explicit.

## Keep allocation out of the hot path

Watch for less obvious allocations:

- string concatenation and formatting inside loops;
- temporary arrays passed to a driver;
- growing collections;
- new request and response objects for every network call;
- captured state created for every task;
- exceptions used as ordinary control flow.

The compiler's arena estimate is a starting point. Confirm it against the generated report, stack requirements and the
native RAM consumed by the board core, Wi-Fi, TLS and other peripherals.

## A practical review checklist

Before uploading, ask:

1. Which objects are created before the main loop?
2. Which buffers have a fixed owner and capacity?
3. What happens when a queue or response is larger than expected?
4. Can a timeout or cancellation leave a resource in use?
5. Does the peak number of live tasks fit both the Java arena and native heap?

Zero allocation is not a demand to avoid every object. It is a design discipline: make the allocations that matter
visible, bounded and reusable. Compare `MCU.getBoardMemory().arenaUsedBytes()` with
`arenaCapacityBytes()` after many loop iterations; this measures the Java arena, not native heap usage or every native
allocation. The compiler's startup report and `JUNO-RISK-001` findings complement that runtime check.
