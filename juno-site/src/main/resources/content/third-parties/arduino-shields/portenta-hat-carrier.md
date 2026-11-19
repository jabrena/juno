---
title: "Portenta Hat Carrier"
description: "Arduino Portenta Hat Carrier: a carrier for the Portenta H7 with a Raspberry Pi header for HATs, a microSD slot and the MHS-3.5 touch display."
layout: page
---

{|
Arduino's carrier for the Portenta H7 (ASX00049). It adds a 40-pin Raspberry Pi header for HATs and a microSD slot of its own. `PortentaHatCarrier` names the header pins and the slot. The MHS-3.5 touch display, a 480 × 320 TFT with an ILI9486 controller and a resistive XPT2046 touch panel, plugs onto that header as a HAT, and `Mhs35TftDisplay` drives it. The [Portenta H7](../../../boards/arduino-portenta-h7#portenta-hat-carrier) page has the pin map and the examples.

<img src="../../../images/shields/portenta-hat-carrier.png" alt="Arduino Portenta Hat Carrier" width="280">

## API

| Method | What it does |
|---|---|
| `getSdCard()` | The carrier's own microSD slot, on the H7's SDMMC2 bus. Its `begin()` mounts the card. |
| `getLcdChipSelect()`, `getLcdDataCommand()`, `getLcdReset()` | The header pins for a HAT display: chip select (D7), data/command (PI7) and reset (D5). |
| `getTouchChipSelect()` | The header pin for the touch controller's chip select (D4). |
| `Mhs35TftDisplay(carrier)` | Creates the MHS-3.5 display on the carrier's header, with the SPI bus from `MCU.getSpi()`. |
| `setRotation(value)`, `fillScreen(color)`, `fillRect(...)`, `fillCircle(...)` | Drawing, as on the TFT Touch Shield. `LANDSCAPE` is 480 × 320. |
| `setCursor(x, y)`, `setTextColor(foreground, background)`, `print(...)`, `println(...)` | Text on the display. |
| `readTouch()`, `touchX()`, `touchY()` | Touch input on the resistive panel. |

## Example

```java
import io.github.jabrena.juno.hardware.device.carrier.PortentaHatCarrier;
import io.github.jabrena.juno.hardware.device.tft.Mhs35TftDisplay;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.util.Delay;

public final class Mhs35Hello {
    public static void main(String[] args) {
        Mhs35TftDisplay tft = new Mhs35TftDisplay(new PortentaHatCarrier());
        tft.setRotation(Mhs35TftDisplay.LANDSCAPE);
        tft.fillScreen(Display.BLACK);
        tft.setCursor(10, 10);
        tft.setTextColor(Display.WHITE, Display.BLACK);
        tft.print("Hello, Juno");

        while (true) {
            if (tft.readTouch()) {
                tft.fillCircle(tft.touchX(), tft.touchY(), 3, Display.WHITE);
            }
            Delay.millis(20);
        }
    }
}
```

[`BouncingTrianglesTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/tft/BouncingTrianglesTFT.java) is the typical first demo for the display.
|}
