---
title: "Delay"
description: "Pausing a Juno program for a fixed time with Delay.millis, Delay.micros and Delay.seconds."
layout: page
---

[`Delay`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/util/Delay.java)
(`io.github.jabrena.juno.util`) pauses the program for a fixed amount of time. It has three static methods:

| Method | Pauses for |
|---|---|
| `Delay.millis(n)` | `n` milliseconds |
| `Delay.micros(n)` | `n` microseconds |
| `Delay.seconds(n)` | `n` seconds |

A value of zero or less returns at once, so a delay can be computed without checking it first.

## Blinking an LED

The classic example pauses for half a second in each state:

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

public class Blink {

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());

        while (true) {
            led.high();
            Delay.millis(500);
            led.low();
            Delay.millis(500);
        }
    }
}
```

## Short pauses with micros

`Delay.micros` is for pauses shorter than a millisecond, such as the timing of a single signal edge:

```java
led.high();
Delay.micros(10);   // 10 microseconds
led.low();
```

## Longer pauses with seconds

`Delay.seconds` is a convenience over `Delay.millis`, for pauses measured in whole seconds:

```java
Delay.seconds(2);   // the same as Delay.millis(2000)
```
