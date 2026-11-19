---
title: "Hardware"
description: "Hardware: Gpio drives the pins, Delay pauses a program, and Clock reads the board's timer."
layout: page
---

Gpio drives and reads the board's pins. Delay pauses a program for a fixed time. Clock reads the board's timer for measuring intervals.

| API | What it provides | Complete example |
|---|---|---|
| [Gpio](gpio) | Driving and reading the board's pins, with `DigitalOutput` and the Blink example. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| [Delay](delay) | Pausing a program for a fixed time with `Delay.millis`, `Delay.micros` and `Delay.seconds`. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| [Clock](clock) | The board's monotonic millisecond and microsecond timer, for measuring intervals. | [`RandomNumbers`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/RandomNumbers.java) |
