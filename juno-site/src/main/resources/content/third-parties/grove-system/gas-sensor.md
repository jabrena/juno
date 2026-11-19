---
title: "Gas sensor"
description: "Gas sensor: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-gas-mq2.jpg" alt="Grove Gas Sensor (MQ2)" width="240">

## API

| Method | What it does |
|---|---|
| `GroveAnalogGas(gpio)` | Creates the driver on the analog socket. |
| `rawValue(analogPin)` | The raw gas reading from the ADC. |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveAnalogGas;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveGasExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveAnalogGas gas = new GroveAnalogGas();
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("gas ADC=");
            Serial.println(gas.rawValue(shield.getA3()));
            Delay.millis(500);
        }
    }
}
```
|}
