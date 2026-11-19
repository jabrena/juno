---
title: "Grove Base Shield"
description: "Seeed Studio's passive Grove base shield for the UNO: every socket is a UNO pin, named by GroveBaseShield."
layout: page
---

{|
Seeed Studio's passive adapter: every Grove socket is an ordinary UNO pin, and `GroveBaseShield` names them. Plug a Grove module in and pass the socket's getter to its driver. For the sensors, see [Grove](..).

<img src="../../../images/shields/shield-grove.png" alt="Grove Base Shield" width="280">

## Sockets

The [Grove Base Shield](https://wiki.seeedstudio.com/Base_Shield_V2/) is a passive adapter for the UNO headers, used
on the UNO R3 and UNO R4 WiFi. Its A0-A3, D2-D8, UART and I2C sockets are ordinary UNO pins;
[`GroveBaseShield`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/GroveBaseShield.java)
names them. Seat it on the board's UNO headers and plug the module into a socket; the diagram is a socket map, not the
shield's physical layout:

```text
+-------------------------------------------------------------+
|                      Grove Base Shield                      |
|                                                             |
|  Digital   [D2] [D3] [D4] [D5] [D6] [D7] [D8]   getD2()...  |
|  Analog    [A0] [A1] [A2] [A3]                  getA0()...  |
|  I2C       [I2C] [I2C] [I2C] [I2C]  SDA A4, SCL A5          |
|  UART      [UART]                   RX D0, TX D1            |
+------------------------------+------------------------------+
                               | seated on the UNO headers
+------------------------------v------------------------------+
|      UNO R3  /  UNO R4 WiFi                                 |
+-------------------------------------------------------------+

 Grove DHT22 ---- cable ----> [D4]
 Java:  dht.read(shield.getD4(), reading, reading.length, frame, frame.length)
```

## API

| Method | What it does |
|---|---|
| `getD2()` … `getD8()` | The UNO pin behind each digital socket. |
| `getA0()` … `getA3()` | The UNO pin behind each analog socket. |
| `getUartRx()`, `getUartTx()` | The UART pins behind the UART socket. |
| `getI2c()`, `getI2cSda()`, `getI2cScl()` | The I2C bus for I2C modules, and its pins. |

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
        GroveDht22 sensor = new GroveDht22();
        int[] reading = new int[GroveDht22.READING_LENGTH];
        int[] frame = new int[GroveDht22.FRAME_LENGTH];
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            if (sensor.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
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

This example reads a DHT22 in socket D4. The formatted version is [`GroveDht22Monitor`]({ex}api/hardware/device/grove/sensors/GroveDht22Monitor.java).
|}
