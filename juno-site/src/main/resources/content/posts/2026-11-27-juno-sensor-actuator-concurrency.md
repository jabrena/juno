---
title: "Concurrency patterns for sensors and actuators in Juno"
description: "Build predictable sensor, control and display loops with Juno virtual threads, bounded queues and explicit ownership."
date: 2026-11-23
layout: post
link: /blog/2026-11-27-juno-sensor-actuator-concurrency/
tags: [concurrency, robotics, sensors]
blogVisible: true
---

Robotics firmware often has several clocks: sensors must be sampled, control decisions must be made, actuators must be
updated and telemetry may need to leave over Wi-Fi. Juno virtual threads can express those activities clearly, but the
board still needs bounded work and explicit ownership.

## Give each activity one owner

A good starting design assigns one task to each responsibility:

```text
sensor task -> bounded sample queue -> control task -> actuator state
                                      \-> telemetry task
```

The sensor task owns the hardware read. The control task owns the current decision. The actuator task or control task
owns writes to motors, LEDs or servos. This prevents two tasks from changing a peripheral at the same time.

## Bound the queue

A queue should have a capacity related to the physical system, not an unbounded capacity. If the controller only needs
the latest sample, a one-element “latest value” buffer is often better than retaining stale readings. If every event
matters, choose a fixed queue and define the full behavior.

```java
final class ControlLoop {
    private static final Object GUARD = new Object();
    private static int latestValue;
    private static long latestAtMillis;
    private static boolean sampleReady;

    static void publishSample(int value, long atMillis) {
        synchronized (GUARD) {
            latestValue = value;
            latestAtMillis = atMillis;
            sampleReady = true; // overwrite policy: keep only the newest sample
        }
    }

    static void controlStep() {
        int value;
        long atMillis;
        synchronized (GUARD) {
            if (!sampleReady) {
                return;
            }
            value = latestValue;
            atMillis = latestAtMillis;
            sampleReady = false;
        }
        // Application code: perform I/O after releasing the hand-off lock.
        if (isFresh(atMillis)) {
            Motor.setPower(clamp(value));
        } else {
            Motor.setPower(0); // application-specific stale-data policy
        }
    }
}
```

The producer and consumer update and clear the one-slot hand-off under the same short lock, so a newer publication cannot
be accidentally cleared by an older consumer. The consumer copies the values and performs actuator I/O after releasing
the lock. Telemetry should receive a separate snapshot; it must not hold this lock while doing network I/O.

## Separate periodic work from blocking work

Sensor sampling and actuator updates should remain short and periodic. Network telemetry can block, so put it behind a
bounded hand-off rather than allowing a slow endpoint to delay motor control. A virtual thread waiting for Wi-Fi still
occupies a scheduler slot and retains its captured state.

Use a timeout for remote work, cancel work that is no longer relevant and avoid retry storms. If telemetry is late, the
control loop should continue with its most recent valid state.

## Structured lifetime for a control cycle

`StructuredTaskScope` is useful when a cycle needs several related readings and should proceed only after they agree. It
is a JDK 27 preview API and is unavailable on UNO R3.
Fork the small fixed set of readings, join them, validate their results and then update the actuator. If one reading
fails, cancel the siblings and apply the application's explicit stale-data or fail-safe policy. Preserving the previous
motor command is not automatically safe.

Do not create a new unbounded group on every loop iteration. Reuse buffers, keep the batch small and make the next cycle
wait until the previous cycle has released its work. Virtual threads provide concurrency, not a hard control deadline;
blocking vendor calls can still affect timing.

## Safety rules

- Keep actuator writes in one owner task.
- Make queue capacity and overflow behavior explicit.
- Never let telemetry backpressure block the control path.
- Use timeouts and cooperative cancellation.
- Keep emergency-stop handling independent of optional network work.
- Measure peak task count, arena use and native RAM together.

Concurrency is valuable when it separates independent timing concerns. It is harmful when it hides an unbounded queue or
allows several tasks to believe they own the same motor, sensor or buffer.
