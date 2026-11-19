---
title: "PIR Motion Sensor"
description: "PIR Motion Sensor: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-pir-motion.jpg" alt="Grove PIR Motion Sensor" width="240">

## API

| Method | What it does |
|---|---|
| `GrovePirMotion(gpio, signalPin)` | Creates the driver on a digital pin. |
| `begin(signalPin)` | Prepares the pin for reading. |
| `motionDetected(signalPin)`, `isMotionDetected()` | Whether motion is detected, from the given pin or the last one used. |
| `sampleSize()`, `fetchSample(sample, offset)` | Copies the motion state into an array the program owns. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GrovePirMotion;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GrovePirExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GrovePirMotion pir = new GrovePirMotion();
        pir.begin(shield.getD2());
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("motion=");
            Serial.println(pir.motionDetected(shield.getD2()));
            Delay.millis(100);
        }
    }
}
```
|}
