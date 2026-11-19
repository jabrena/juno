---
title: "Clock"
description: "Clock: the board's monotonic millisecond and microsecond timer, for measuring intervals."
layout: page
---

`Clock` is the board's monotonic timer. `millis()` counts milliseconds since the board started and `micros()` counts
microseconds. Both wrap around, after about 24.8 days and 35.8 minutes, so measure an interval by subtraction, which
stays correct across one wrap. Get the clock once, at startup:

```java
private static final Clock CLOCK = MCU.getClock();

int start = CLOCK.millis();
// work
int elapsed = CLOCK.millis() - start;
```

[`RandomNumbers`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/RandomNumbers.java)
gets its clock the same way. For wall-clock time (UTC from an SNTP server), see [Time](../../internet/time). Unit tests
pass a `mock(Clock.class)` instead of the board's timer.
