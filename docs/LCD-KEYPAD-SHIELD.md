# LCD Keypad Shield

[`LcdKeypadShield`](../juno/src/main/java/io/github/jabrena/juno/api/lcd/LcdKeypadShield.java)
drives the common "LCD Keypad Shield" — a 16x2 HD44780-compatible character LCD in 4-bit mode,
plus 5 buttons wired through a single resistor ladder into one analog pin — using its standard
pinout, as used by e.g. [dzindra/lcdkeypad](https://github.com/dzindra/lcdkeypad).

Unlike the [LED matrix](../juno/src/main/java/io/github/jabrena/juno/api/led/LedMatrix.java), this
shield needs no new compiler intrinsic: every operation is built entirely from
[`Gpio`](../juno/src/main/java/io/github/jabrena/juno/api/io/Gpio.java) pin operations and
[`Delay`](../juno/src/main/java/io/github/jabrena/juno/api/Delay.java), so it works today on any
board Juno's ASM backend targets.

## Wiring

The driver assumes the shield's standard pinout and needs no additional configuration:

| Function | Pin |
|---|---|
| LCD `RS` | digital 8 |
| LCD `Enable` | digital 9 |
| LCD data (`D4`-`D7`) | digital 4-7 |
| Backlight | digital 10 |
| Buttons (analog ladder) | `A0` |

## API

```java
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

LcdKeypadShield.begin();                 // pin setup + HD44780 4-bit init + backlight on
LcdKeypadShield.clear();                 // clear display, cursor to (0, 0)
LcdKeypadShield.home();                  // cursor to (0, 0), keep contents
LcdKeypadShield.setCursor(0, 1);         // column 0-15, row 0-1
LcdKeypadShield.print("Hello");          // print text at the cursor, no wrapping
LcdKeypadShield.print(42);               // print an int in decimal
LcdKeypadShield.backlight(false);        // turn the backlight off (or on)

int button = LcdKeypadShield.readButton(); // undebounced read of the currently pressed button
```

`readButton()` returns one of `LcdKeypadShield.NONE`, `RIGHT`, `UP`, `DOWN`, `LEFT`, or `SELECT`.
`COLUMNS` (16) and `ROWS` (2) describe the character grid. Reads are undebounced — a program that
needs a clean single "press" event should only act when the value changes from `NONE`, as the
example below does.

## Example: showing which button is pressed

```java
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

@Board(ArduinoUnoR4WiFi.class)
public final class LcdKeypadDemo {
    public static void main(String[] args) {
        LcdKeypadShield.begin();
        LcdKeypadShield.print("Press a button");

        int lastButton = -1;
        while (true) {
            int button = LcdKeypadShield.readButton();
            if (button != lastButton) {
                LcdKeypadShield.setCursor(0, 1);
                String label = "NONE  ";
                if (button == LcdKeypadShield.RIGHT) {
                    label = "RIGHT ";
                } else if (button == LcdKeypadShield.UP) {
                    label = "UP    ";
                } else if (button == LcdKeypadShield.DOWN) {
                    label = "DOWN  ";
                } else if (button == LcdKeypadShield.LEFT) {
                    label = "LEFT  ";
                } else if (button == LcdKeypadShield.SELECT) {
                    label = "SELECT";
                }
                LcdKeypadShield.print(label);
                lastButton = button;
            }
            Delay.millis(50);
        }
    }
}
```

This is
[`juno-examples/.../lcd/LcdKeypadDemo.java`](../juno-examples/src/main/java/io/github/jabrena/juno/api/lcd/LcdKeypadDemo.java).
Build, flash, and run it with `juno-maven-plugin` (see
[docs/JUNO-MAVEN-PLUGIN.md](JUNO-MAVEN-PLUGIN.md)):

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.lcd.LcdKeypadDemo
```

Note that each printed label is padded to a fixed width (`"NONE  "`, `"SELECT"`) so a shorter new
label fully overwrites a longer previous one on the fixed 16-column display — `print` does not
clear the rest of the row.

### A larger example: Pomodoro

[`juno-examples/.../lcd/pomodoro`](../juno-examples/src/main/java/io/github/jabrena/juno/api/lcd/pomodoro)
implements a small Pomodoro timer entirely on the shield: `ConfigurePomodoroView` lets the buttons
set a duration, `RunPomodoro` counts it down while showing elapsed time, and `TimeUpView` signals
completion — a good reference for a multi-screen, button-driven program built only from
`LcdKeypadShield` and `Delay`.

## Notes

- `backlight(true)` floats the backlight pin (`INPUT`) rather than driving it `HIGH`, because the
  shield's backlight LED has no current-limiting resistor on some board revisions and driving the
  pin `HIGH` can pull the shared 5V rail down hard enough to keep the LCD from initializing at all.
- The HD44780 init sequence and every command/data write include generous settle delays; this
  keeps the shield reliable under voltage sag from other onboard activity (for example WiFi radio
  bursts on the UNO R4 WiFi) at the cost of a few extra microseconds per operation — never
  noticeable for a display updated a few times a second.
- `print(String)` writes each character with `charAt(i)`, so a compile-time literal or any runtime
  `String` supporting `length()`/`charAt(int)` (see [docs/FEATURES.md](FEATURES.md)) both work,
  unlike [`Serial.print(String)`](SERIAL.md), which is literal-only.
