---
title: "Infrared emitter and receiver"
description: "Infrared emitter and receiver: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-ir-emitter.jpg" alt="Grove Infrared Emitter" width="240">

## API

| Method | What it does |
|---|---|
| `GroveInfraredEmitter(signalPin)` | Creates the emitter on a digital pin. |
| `GroveInfraredReceiver(signalPin)` | Creates the receiver on a digital pin. |
| `signalPin()` | The pin the driver uses. |
| `driver()` | The infrared protocol the driver speaks (`InfraredDriver.Type`). |

## Example

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class GroveRcxExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield();
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(shield.getD4());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(shield.getD3());
        RcxLink link = new RcxLink(emitter, receiver);
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            int millivolts = rcx.getBattery().millivolts();
            Serial.print("RCX battery mV=");
            Serial.println(millivolts);
            Delay.millis(1000);
        }
    }
}
```
|}
