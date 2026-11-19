---
title: "Temperature Sensor v1.2"
description: "Temperature Sensor v1.2: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-temperature-v12.jpg" alt="Grove Temperature Sensor v1.2" width="240">

## API

| Method | What it does |
|---|---|
| `GroveTemperatureV12(gpio)` | Creates the driver on the analog socket. |
| `celsius(analogPin)` | Reads the thermistor and returns degrees Celsius. |
| `rawValue(analogPin)` | The raw ADC reading. |
| `celsiusFromRaw(adcValue)` | Converts a raw ADC reading to degrees Celsius. |
| `getTemperatureCelsius()`, `sampleSize()`, `fetchSample(sample, offset)` | The last temperature, and the sample copy used by the program. |

## Example

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveTemperatureV12;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveTemperatureExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveTemperatureV12 temperature = new GroveTemperatureV12();
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("temperature C=");
            Serial.println(temperature.celsius(shield.getA0()));
            Delay.millis(1000);
        }
    }
}
```
|}
