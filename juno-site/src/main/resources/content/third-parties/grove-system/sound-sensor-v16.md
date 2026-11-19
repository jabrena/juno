---
title: "Sound Sensor v1.6"
description: "Sound Sensor v1.6: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-sound-v16.jpg" alt="Grove Sound Sensor v1.6" width="240">

## API

| Method | What it does |
|---|---|
| `GroveSoundV16(gpio)` | Creates the driver on the analog socket. |
| `rawValue(analogPin)` | The raw microphone level from the ADC. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveSoundV16;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveSoundExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveSoundV16 sound = new GroveSoundV16();
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("microphone ADC=");
            Serial.println(sound.rawValue(shield.getA2()));
            Delay.millis(100);
        }
    }
}
```
|}
