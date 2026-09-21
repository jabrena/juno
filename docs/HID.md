# HID (USB mouse control)

[`Mouse`](../juno/src/main/java/io/github/jabrena/juno/api/io/hid/Mouse.java)
(`io.github.jabrena.juno.api.io.hid`) lets a Juno program act as a USB HID mouse, moving the
pointer of whatever computer the board's USB port is plugged into. It is a compiler intrinsic
backed by the Arduino `Mouse` library.

## Requirements

- A board with native USB support. The UNO R4 WiFi qualifies; boards without native USB (e.g. a
  UNO R3 with an ATmega16U2 as a fixed USB-serial bridge) cannot act as a HID device.
- The Arduino `Mouse` library installed:

  ```bash
  arduino-cli lib install Mouse
  ```

- The board connected over USB to the computer whose pointer should move — that is the same port
  used for flashing, so uploading a new sketch while `Mouse` is actively moving the pointer can
  make the board harder to interact with (see the warning below).

## API

```java
import io.github.jabrena.juno.api.io.hid.Mouse;

Mouse.begin();
Mouse.move(10, -5);   // relative move: (x, y) pixels from the current pointer position
```

`move(x, y)` narrows each argument to a `signed char` (`-128..127`, low 8 bits, sign-extended) to
match the underlying `Mouse_::move(signed char, signed char, signed char)` call. Keep both
arguments within that range — a larger value wraps around instead of clamping.

## Example: moving the pointer in a square

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.hid.Mouse;

@Board(ArduinoUnoR4WiFi.class)
public final class MouseSquare {
    private static final int STEP = 50;
    private static final int STEPS_PER_SIDE = 5;
    private static final int MOVE_DELAY = 1000;

    public static void main(String[] args) {
        Mouse.begin();

        while (true) {
            moveSide(STEP, 0);   // right
            moveSide(0, STEP);   // down
            moveSide(-STEP, 0);  // left
            moveSide(0, -STEP);  // up
        }
    }

    private static void moveSide(int dx, int dy) {
        int i = 0;
        while (i < STEPS_PER_SIDE) {
            Delay.millis(MOVE_DELAY);
            Mouse.move(dx, dy);
            i = i + 1;
        }
    }
}
```

The repository ships a fuller version of this idea as
[`RatonLoco`](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/hid/RatonLoco.java),
which also blinks the built-in LED and draws a
[mouse-face icon](../juno-examples/src/main/java/io/github/jabrena/juno/api/io/hid/MouseIcon.java)
on the LED matrix first. Build, flash, and run it with `juno-maven-plugin` (see
[docs/JUNO-MAVEN-PLUGIN.md](JUNO-MAVEN-PLUGIN.md)):

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.io.hid.RatonLoco
```

## Warning

Once uploaded, a program that calls `Mouse.begin()`/`Mouse.move(...)` in a loop takes control of
the pointer on the connected computer and keeps moving it indefinitely — including possibly
interfering with clicking into another terminal to re-flash the board. Before uploading such a
sketch, know how to recover: disconnect the USB cable, or use a physical reset/bootloader
sequence, to stop the pointer and get back to a state where the board can be reprogrammed.

## Troubleshooting

- **Compile/verify fails referencing `Mouse`:** install the library with
  `arduino-cli lib install Mouse`.
- **Nothing moves after flashing:** confirm the target board has native USB (the UNO R4 WiFi
  does); a board without it cannot present as a HID device regardless of what the sketch does.
- **The pointer jumps by the wrong amount:** check that `x`/`y` stay within `-128..127` per call —
  a larger step silently wraps instead of clamping.
