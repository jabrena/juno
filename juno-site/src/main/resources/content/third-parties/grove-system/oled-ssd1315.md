---
title: "OLED 0.96-inch (SSD1315)"
description: "Grove 0.96-inch OLED (SSD1315): a 128 × 64 display over I2C, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-oled-ssd1315.jpg" alt="Grove 0.96-inch SSD1315 OLED display" width="240">

## API

| Method | What it does |
|---|---|
| `GroveOledSsd1315(i2c, framebuffer)` | Creates the display over an I2C bus, with a 1,024-byte framebuffer the program owns. |
| `fillScreen(color)`, `drawPixel(x, y, color)`, `fillRect(x, y, width, height, color)` | Drawing on the 128 × 64 screen. |
| `setCursor(x, y)`, `setTextSize(size)`, `setTextColor(foreground, background)` | Positions and styles text. |
| `print(...)`, `println(String)` | Prints text at the cursor. |
| `width()`, `height()` | The screen size in pixels. |
| `flush()` | Sends the framebuffer to the display. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.display.GroveOledSsd1315;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.util.Delay;

public final class GroveOledHello {
    public static void main(String[] args) {
        I2c i2c = new GroveBaseShield().getI2c();
        i2c.begin();
        byte[] framebuffer = new byte[GroveOledSsd1315.FRAMEBUFFER_SIZE];
        GroveOledSsd1315 oled = new GroveOledSsd1315(i2c, framebuffer);
        oled.fillScreen(Display.BLACK);
        oled.setTextColor(Display.WHITE, Display.BLACK);
        oled.setTextSize(1);
        oled.setCursor(30, 28);
        oled.print("Hello World");
        oled.flush();

        while (true) {
            Delay.millis(1000);
        }
    }
}
```
|}
