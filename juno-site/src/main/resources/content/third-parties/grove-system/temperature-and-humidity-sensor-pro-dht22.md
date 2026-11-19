---
title: "Temperature and Humidity Sensor Pro (DHT22)"
description: "Temperature and Humidity Sensor Pro (DHT22): how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-dht22.jpg" alt="Grove Temperature and Humidity Sensor Pro (DHT22)" width="240">

## API

| Method | What it does |
|---|---|
| `GroveDht22(gpio, clock)` | Creates the driver once, at startup. |
| `read(signalPin, reading, readingLength, frame, frameLength)` | Reads one frame and fills `reading` with temperature and humidity in tenths. Returns whether the read succeeded. |
| `getTemperatureCelsius()` | The last temperature, in degrees Celsius. |
| `READING_LENGTH`, `TEMPERATURE_TENTHS_CELSIUS`, `HUMIDITY_TENTHS_PERCENT` | The size of `reading` and the index of each value in it. |
| `sampleSize()`, `fetchSample(sample, offset)` | Copies the last sample into an array the program owns. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht22;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveDht22Example {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveDht22 dht = new GroveDht22();
        int[] reading = new int[GroveDht22.READING_LENGTH];
        int[] frame = new int[GroveDht22.FRAME_LENGTH];
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            if (dht.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
                Serial.print("temperature tenths C=");
                Serial.print(reading[GroveDht22.TEMPERATURE_TENTHS_CELSIUS]);
                Serial.print(" humidity tenths %= ");
                Serial.println(reading[GroveDht22.HUMIDITY_TENTHS_PERCENT]);
            } else {
                Serial.println("DHT22 read failed");
            }
            Delay.millis(2000);
        }
    }
}
```
|}
