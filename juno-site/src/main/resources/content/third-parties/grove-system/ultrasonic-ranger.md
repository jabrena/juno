---
title: "Ultrasonic Ranger"
description: "Ultrasonic Ranger: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-ultrasonic-ranger.png" alt="Grove Ultrasonic Ranger" width="240">

## API

| Method | What it does |
|---|---|
| `GroveUltrasonicRanger(gpio, clock, signalPin)` | Creates the driver on a digital pin, with the board's clock. |
| `getRange()` | The distance to the nearest object, in metres. |
| `sampleSize()`, `fetchSample(sample, offset)` | Copies the distance into an array the program owns. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveUltrasonicExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(
                MCU.getGpio(), MCU.getClock(), shield.getD5());
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("distance metres=");
            Serial.println(ranger.getRange());
            Delay.millis(100);
        }
    }
}
```
|}
