---
title: "TFT Touch Shield"
description: "ELEGOO 2.8-inch TFT touch shield: a 240 × 320 color display with resistive touch and a microSD socket, driven by TftTouchShield."
layout: page
---

{|
The ELEGOO 2.8" shield: a 240 × 320 ILI9341 color display on an 8-bit parallel bus, a resistive touch panel and a microSD socket. `TftTouchShield` draws, prints text and reads touches.

<img src="../../../images/shields/shield-tft.png" alt="ELEGOO 2.8-inch TFT Touch Shield" width="280">

## API

| Method | What it does |
|---|---|
| `fillScreen(color)` | Fills the whole screen with a color. |
| `fillRect(x, y, w, h, color)`, `drawRect(...)` | Fills, or outlines, a rectangle from its top-left corner. |
| `fillCircle(cx, cy, r, color)`, `drawCircle(...)` | Fills, or outlines, a circle. |
| `drawPixel(x, y, color)`, `drawHorizontalLine(...)`, `drawVerticalLine(...)` | Single pixels and straight lines. |
| `setRotation(value)` | Selects `PORTRAIT`, `LANDSCAPE`, or their flipped forms. |
| `setCursor(x, y)`, `setTextSize(size)`, `setTextColor(foreground, background)` | Positions and styles text. |
| `print(...)`, `println(String)` | Prints text, a character or a decimal number at the cursor. |
| `readTouch()` | Samples the panel and returns whether it is pressed. |
| `touchX()`, `touchY()`, `touchPressure()` | Last touch position for the current rotation, and its pressure. |
| `calibrateTouch(rawLeft, rawRight, rawTop, rawBottom)` | Replaces the touch calibration. |
| `color(red, green, blue)` | Packs 8-bit channels into an RGB565 color. |

## Example

```java
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class TftTouchExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield tft = new TftTouchShield();
        tft.fillScreen(TftTouchShield.BLUE);
        tft.setCursor(20, 20);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLUE);
        tft.setTextSize(2);
        tft.println("Touch the screen");

        while (true) {
            if (tft.readTouch()) {
                tft.fillCircle(tft.touchX(), tft.touchY(), 4, TftTouchShield.RED);
            }
            Delay.millis(30);
        }
    }
}
```

The full paint program with a color palette is [`TouchPaintTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/tft/TouchPaintTFT.java).
|}
