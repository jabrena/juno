---
title: "Grove Base Shield and DHT11"
description: "Reading the Grove Temperature and Humidity Sensor with the passive Grove Base Shield."
layout: page
---

The [Grove Base Shield](https://wiki.seeedstudio.com/Base_Shield_V2/) is a passive adapter for
the UNO headers. Its A0-A3, D2-D8, UART and I2C sockets are ordinary UNO pins; Juno exposes their
map through [`GroveBaseShield`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/api/thirdparty/grove/GroveBaseShield.java).

## Grove DHT11

[`GroveDht11`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/api/thirdparty/grove/GroveDht11.java)
is a library-free, allocation-free driver for the Grove Temperature and Humidity Sensor (DHT11,
the blue module marked `GND`, `VCC`, `SIG`, `NC`). Plug the module into the shield's D4 socket:
the shield supplies `GND` and `VCC`, and the driver reads the `SIG` wire on `D4`.

```java
import io.github.jabrena.juno.api.thirdparty.grove.GroveBaseShield;
import io.github.jabrena.juno.api.thirdparty.grove.GroveDht11;

int[] reading = new int[GroveDht11.READING_LENGTH];
if (GroveDht11.read(GroveBaseShield.D4, reading, reading.length)) {
    int humidity = reading[GroveDht11.HUMIDITY_PERCENT];
    int temperature = reading[GroveDht11.TEMPERATURE_CELSIUS];
}
```

The result buffer belongs to the caller and is reused across readings. DHT11 values are whole
percent and whole degrees Celsius; a failed response or checksum returns `false` without changing
the previous result. Wait at least one second between successful reads. The complete serial example
is [`GroveDht11Monitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/thirdparty/grove/GroveDht11Monitor.java).

This driver is implemented in Java over `Gpio`, `Clock.micros()` and `Delay`; it adds no Arduino
library or compiler intrinsic and therefore works on the UNO R3, UNO WiFi Rev2, UNO R4 WiFi and
UNO Q targets.
