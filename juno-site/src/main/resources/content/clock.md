---
title: "Clock & Delay"
description: "Timing: Delay and Clock."
layout: page
---

[`Clock`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/time/Clock.java)
(`io.github.jabrena.juno.hardware.time`) reads the board's monotonic uptime clock. `MCU.getClock()` reads
the real timer: its calls compile to the Arduino core's `millis()`/`micros()` with no extra call in
between. `port` reads any
[`Clock`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/time/Clock.java)
instead, which is how a unit test controls time (see [Testing](#testing-code-that-reads-the-clock)).
Create a program's clocks once, at startup, as you would any other object.

```java
import io.github.jabrena.juno.hardware.time.Clock;

private static final Clock CLOCK = MCU.getClock();

int uptimeMillis = CLOCK.millis();
int uptimeMicros = CLOCK.micros();
```

Both readings start at (approximately) zero at boot and increase monotonically while the board
runs, with no relation to wall-clock time — there is no real-time clock or NTP support. Both
methods return an `int`; the underlying counters are unsigned 32-bit values on the Arduino core,
so `millis()` wraps back through zero after about 49.7 days of continuous uptime, and `micros()`
after about 71.6 minutes. A program that must run unattended across a wrap should compare elapsed
time with subtraction (`CLOCK.millis() - start`), which stays correct across exactly one wrap
because of unsigned/twos-complement overflow arithmetic, rather than comparing timestamps directly
with `<`/`>`.

## `Clock` vs. `Delay`

[`Delay`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/util/Delay.java) (`millis`/`micros`) blocks
the program for a fixed duration and does nothing else meanwhile. `Clock` instead reports elapsed
time, so a program built around it can do other work — polling a button, updating a display,
handling network I/O — while still acting on a schedule. Reach for `Clock` whenever a loop needs
to do more than one timed thing at once; reach for `Delay` when blocking is genuinely fine, as in
the classic single-LED blink.

## Example: non-blocking periodic action

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.gpio.Gpio;

@Board(ArduinoUnoR4WiFi.class)
public final class ClockBlink {
    private static final int LED_PIN = 13;
    private static final int BUTTON_PIN = 2;
    private static final int BLINK_INTERVAL_MILLIS = 500;

    public static void main(String[] args) {
        Clock clock = MCU.getClock();
        Gpio gpio = MCU.getGpio();
        DigitalOutput led = DigitalOutput.of(LED_PIN);
        gpio.pinMode(BUTTON_PIN, Gpio.getInputPullup());

        int lastToggle = clock.millis();
        while (true) {
            // Blink on a schedule...
            if (clock.millis() - lastToggle >= BLINK_INTERVAL_MILLIS) {
                led.toggle();
                lastToggle = clock.millis();
            }
            // ...while still reacting immediately to other input every iteration.
            if (!gpio.digitalRead(BUTTON_PIN)) {
                led.high();
            }
        }
    }
}
```

Unlike a `Delay.millis(500)` blink, this loop never stops watching the button, because `Clock`
only measures time — it never pauses execution.

The repository's
[Pomodoro timer](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/thirdparty/lcd/pomodoro/RunPomodoro.java)
uses the same pattern for real: `millis()` is its sole time source for a countdown displayed
on an [`LcdKeypadShield`](../lcd-keypad-shield), so the displayed time stays accurate regardless of
how long each tick's own display/print work takes.

## Measuring how long something took

`micros()` is precise enough to profile a routine's execution time directly on the board,
which is useful when [`Serial`](../serial) is the only visibility into a running program:

```java
int start = CLOCK.micros();
someExpensiveOperation();
int elapsedMicros = CLOCK.micros() - start;
Serial.print("elapsed us: ");
Serial.println(elapsedMicros);
```

## Testing code that reads the clock

`Clock`'s readings come from its `Clock`, an interface, so a unit test on the desktop JVM hands
the code under test a `Clock` over a Mockito mock and decides what time it is. The board's own
port, `NativeClock`, has no Java body and cannot run or be mocked off the board.

```java
Clock port = mock(Clock.class);
when(port.millis()).thenReturn(1_000, 1_499, 1_500);
Debouncer debouncer = new Debouncer(port);   // the class under test takes its Clock

assertThat(debouncer.settled()).isFalse();   // 1,000 ms: just pressed
assertThat(debouncer.settled()).isFalse();   // 1,499 ms
assertThat(debouncer.settled()).isTrue();    // 1,500 ms: held for 500 ms
```

[`Gpio`](../apis) works the same way, over a `Gpio`; `PortNoiseTest` in `juno-api` mocks both.

## Troubleshooting

- **Elapsed time looks wrong after a very long uptime:** this is the `millis()`/`micros()` 32-bit
  wraparound described above. Always compute elapsed time as `clock.millis() - start` (subtraction),
  never `clock.millis() > deadline` (direct comparison), so a single wrap doesn't produce a
  spuriously huge or negative elapsed value.
- **A `Clock`-scheduled action seems to run late:** `millis()`/`micros()` only report
  time — they don't preempt anything. A loop iteration that blocks on `Delay` or a slow HID/I/O
  call still delays the next `Clock` check by that same amount.
