---
title: "Nano Connector Carrier"
description: "Arduino Nano Connector Carrier: a passive carrier with a microSD slot, Grove sockets and a Qwiic connector for the Nano ESP32, named by NanoConnectorCarrier."
layout: page
---

{|
Arduino's passive carrier for the Nano ESP32: a microSD slot, four Grove sockets and a Qwiic connector, all named by `NanoConnectorCarrier`. The carrier is a pin adapter, so a program can use a Grove socket directly. The [Nano ESP32](../../../boards/arduino-nano-esp32#nano-connector-carrier) page has the pin map.

<img src="../../../images/shields/shield-nano-connector-carrier.png" alt="Arduino Nano Connector Carrier" width="280">

## Sockets

The Arduino Nano Connector Carrier holds the Nano ESP32 and adds four Grove sockets (two analog, UART and I2C) plus a
Qwiic connector; `NanoConnectorCarrier` names them. Set its input selector switch to 3.3 V for the Nano ESP32. See the
[Nano ESP32](../../../boards/arduino-nano-esp32#nano-connector-carrier) page for its full pin map.

The carrier seen from its connector side, USB cable at the bottom; `*` marks each Grove socket's yellow signal wire:

```text
+---------------------------------------------+
|  Grove I2C                      Grove UART  |
|  [ GND  ]                       [ RX * ]    |
|  [ VCC  ]                       [ TX   ]    |
|  [ SDA  ] A4                    [ VCC  ]    |
|  [ SCL *] A5                    [ GND  ]    |
|                 +-------------+             |
|  Qwiic          |   microSD   |             |
|  [ I2C  ]       |   slot      |             |
|  A4/A5, 3.3 V   |   CS D4     |             |
|                 +-------------+             |
|  Grove A0/A1                    Grove A2/A3 |
|  [ GND  ]                       [ A2 * ]    |
|  [ VCC  ]                       [ A3   ]    |
|  [ A1   ]                       [ VCC  ]    |
|  [ A0 * ]                       [ GND  ]    |
+-------------------[ USB ]-------------------+

 Grove light sensor ---- cable ----> [Grove A0/A1]
 Java:  light.rawValue(carrier.getGroveA0())
```

Keep digital modules out of the carrier's I2C socket: its bus pull-ups hold a digital output between low and high.

## API

| Method | What it does |
|---|---|
| `getSdCard()` | The microSD card in the slot, selected on D4 (not the UNO shields' D10). |
| `getSdChipSelectJumperD2()`, `getSdChipSelectJumperD3()` | The alternative chip select, if the solder jumper is moved. |
| `getGroveA0()`, `getGroveA2()`, `getGroveUart()` | The signal pin of each Grove socket. |
| `getA0()` … `getA3()` | The analog pins behind the Grove sockets. |
| `getI2c()`, `getI2cSda()`, `getI2cScl()` | The I2C bus shared by the Grove I2C and Qwiic connectors. |
| `getUartRx()`, `getUartTx()` | The UART pins. |
| `getSpiMosi()`, `getSpiMiso()`, `getSpiSck()` | The SPI pins. |

## Example

```java
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class NanoCarrierGroveLed {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        DigitalOutput led = DigitalOutput.of(carrier.getGroveA0());

        while (true) {
            led.high();
            Serial.println("on");
            Delay.millis(500);
            led.low();
            Serial.println("off");
            Delay.millis(500);
        }
    }
}
```

This example blinks a Grove LED in the A0/A1 socket. Set the carrier's voltage selector to 3.3 V for the Nano ESP32.
|}
