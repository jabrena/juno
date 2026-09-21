# Clock

[`Clock`](../juno/src/main/java/io/github/jabrena/juno/api/Clock.java)
(`io.github.jabrena.juno.api`) is a compiler intrinsic that reads the board's monotonic uptime
clock, backed directly by the Arduino core's `millis()`/`micros()`.

```java
import io.github.jabrena.juno.api.Clock;

int uptimeMillis = Clock.millis();
int uptimeMicros = Clock.micros();
```

Both readings start at (approximately) zero at boot and increase monotonically while the board
runs, with no relation to wall-clock time — there is no real-time clock or NTP support. Both
methods return an `int`; the underlying counters are unsigned 32-bit values on the Arduino core,
so `millis()` wraps back through zero after about 49.7 days of continuous uptime, and `micros()`
after about 71.6 minutes. A program that must run unattended across a wrap should compare elapsed
time with subtraction (`Clock.millis() - start`), which stays correct across exactly one wrap
because of unsigned/twos-complement overflow arithmetic, rather than comparing timestamps directly
with `<`/`>`.

## `Clock` vs. `Delay`

[`Delay`](../juno/src/main/java/io/github/jabrena/juno/api/Delay.java) (`millis`/`micros`) blocks
the program for a fixed duration and does nothing else meanwhile. `Clock` instead reports elapsed
time, so a program built around it can do other work — polling a button, updating a display,
handling network I/O — while still acting on a schedule. Reach for `Clock` whenever a loop needs
to do more than one timed thing at once; reach for `Delay` when blocking is genuinely fine, as in
the classic single-LED blink.

## Example: non-blocking periodic action

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.DigitalOutput;
import io.github.jabrena.juno.api.io.Gpio;

@Board(ArduinoUnoR4WiFi.class)
public final class ClockBlink {
    private static final int LED_PIN = 13;
    private static final int BUTTON_PIN = 2;
    private static final int BLINK_INTERVAL_MILLIS = 500;

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(LED_PIN);
        Gpio.pinMode(BUTTON_PIN, Gpio.INPUT_PULLUP);

        int lastToggle = Clock.millis();
        while (true) {
            // Blink on a schedule...
            if (Clock.millis() - lastToggle >= BLINK_INTERVAL_MILLIS) {
                led.toggle();
                lastToggle = Clock.millis();
            }
            // ...while still reacting immediately to other input every iteration.
            if (!Gpio.digitalRead(BUTTON_PIN)) {
                led.high();
            }
        }
    }
}
```

Unlike a `Delay.millis(500)` blink, this loop never stops watching the button, because `Clock`
only measures time — it never pauses execution.

The repository's
[Pomodoro timer](../juno-examples/src/main/java/io/github/jabrena/juno/api/lcd/pomodoro/RunPomodoro.java)
uses the same pattern for real: `Clock.millis()` is its sole time source for a countdown displayed
on an [`LcdKeypadShield`](LCD-KEYPAD-SHIELD.md), so the displayed time stays accurate regardless of
how long each tick's own display/print work takes.

## Measuring how long something took

`Clock.micros()` is precise enough to profile a routine's execution time directly on the board,
which is useful when [`Serial`](SERIAL.md) is the only visibility into a running program:

```java
int start = Clock.micros();
someExpensiveOperation();
int elapsedMicros = Clock.micros() - start;
Serial.print("elapsed us: ");
Serial.println(elapsedMicros);
```

## Troubleshooting

- **Elapsed time looks wrong after a very long uptime:** this is the `millis()`/`micros()` 32-bit
  wraparound described above. Always compute elapsed time as `Clock.millis() - start` (subtraction),
  never `Clock.millis() > deadline` (direct comparison), so a single wrap doesn't produce a
  spuriously huge or negative elapsed value.
- **A `Clock`-scheduled action seems to run late:** `Clock.millis()`/`Clock.micros()` only report
  time — they don't preempt anything. A loop iteration that blocks on `Delay` or a slow HID/I/O
  call still delays the next `Clock` check by that same amount.
