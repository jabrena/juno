---
title: "Temperature and Humidity Sensor (DHT11)"
description: "Temperature and Humidity Sensor (DHT11): how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-dht11.jpg" alt="Grove Temperature and Humidity Sensor (DHT11)" width="240">

## API

| Method | What it does |
|---|---|
| `GroveDht11(gpio, clock)` | Creates the driver once, at startup. |
| `read(signalPin, reading, readingLength, frame, frameLength)` | Reads one frame and fills `reading` with whole-number temperature and humidity. Returns whether the read succeeded. |
| `getTemperatureCelsius()` | The last temperature, in degrees Celsius. |
| `READING_LENGTH`, `TEMPERATURE_CELSIUS`, `HUMIDITY_PERCENT` | The size of `reading` and the index of each value in it. |
| `sampleSize()`, `fetchSample(sample, offset)` | Copies the last sample into an array the program owns. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht11;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveDht11Example {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveDht11 dht = new GroveDht11();
        int[] reading = new int[GroveDht11.READING_LENGTH];
        int[] frame = new int[GroveDht11.FRAME_LENGTH];
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            if (dht.read(shield.getD3(), reading, reading.length, frame, frame.length)) {
                Serial.print("temperature C=");
                Serial.print(reading[GroveDht11.TEMPERATURE_CELSIUS]);
                Serial.print(" humidity %= ");
                Serial.println(reading[GroveDht11.HUMIDITY_PERCENT]);
            } else {
                Serial.println("DHT11 read failed");
            }
            Delay.millis(2000);
        }
    }
}
```
|}
