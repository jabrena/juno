---
title: "Light Sensor v1.2"
description: "Light Sensor v1.2: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-light-v12.jpg" alt="Grove Light Sensor v1.2" width="240">

## API

| Method | What it does |
|---|---|
| `GroveLightV12(gpio, analogPin, adcMaximum)` | Creates the driver. `adcMaximum` is `ADC_10_BIT` (1023) or `ADC_12_BIT` (4095). |
| `getLightValue()` | The brightness as a percentage of the ADC full scale. |
| `getNormalizedLightValue()` | The ADC sample divided by full scale, from 0 in darkness to 1 at saturation. |
| `getLow()`, `getHigh()` | The normalized values in darkness (0) and at saturation (1). |
| `sampleSize()`, `fetchSample(sample, offset)` | Copies the normalized value into an array the program owns. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveLightV12;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.LightDetector;
import io.github.jabrena.juno.util.Delay;

public final class GroveLightExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        LightDetector light = new GroveLightV12(shield.getA1(),
                GroveLightV12.ADC_10_BIT);
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("relative light %= ");
            Serial.println(light.getLightValue());
            Delay.millis(500);
        }
    }
}
```
|}
