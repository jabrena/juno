---
title: "LCD Keypad Shield"
description: "LCD Keypad Shield: a 16 × 2 character LCD with five buttons read through one analog pin, driven by LcdKeypadShield."
layout: page
---

{|
A 16 × 2 character LCD on D4 to D9, its backlight on D10, and five buttons read through one analog pin. `LcdKeypadShield` needs no library.

<img src="../../../images/shields/shield-lcd.png" alt="LCD Keypad Shield" width="280">

## API

| Method | What it does |
|---|---|
| `print(String)`, `print(int)`, `print(int value, int width)` | Prints text or a number at the cursor. `print(7, 2)` shows `07`. |
| `setCursor(column, row)` | Moves the cursor. Column 0 to 15, row 0 to 1. |
| `clear()`, `home()` | Clears the display, or returns the cursor to the start. |
| `backlight(on)` | Turns the backlight on or off. |
| `readButton()` | Returns the pressed button: `NONE`, `RIGHT`, `UP`, `DOWN`, `LEFT` or `SELECT`. |

## Example

```java
import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class LcdKeypadExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        LcdKeypadShield lcd = new LcdKeypadShield();
        lcd.print("Press a button");
        int lastButton = -1;

        while (true) {
            int button = lcd.readButton();
            if (button != lastButton) {
                lcd.setCursor(0, 1);
                if (button == LcdKeypadShield.RIGHT) {
                    lcd.print("RIGHT ");
                } else if (button == LcdKeypadShield.UP) {
                    lcd.print("UP    ");
                } else if (button == LcdKeypadShield.DOWN) {
                    lcd.print("DOWN  ");
                } else if (button == LcdKeypadShield.LEFT) {
                    lcd.print("LEFT  ");
                } else if (button == LcdKeypadShield.SELECT) {
                    lcd.print("SELECT");
                } else {
                    lcd.print("NONE  ");
                }
                lastButton = button;
            }
            Delay.millis(50);
        }
    }
}
```

[`LcdKeypadDemo`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/lcd/LcdKeypadDemo.java) also reports the button code over Serial.
|}
