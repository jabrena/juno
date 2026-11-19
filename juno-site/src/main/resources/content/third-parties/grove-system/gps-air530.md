---
title: "GPS Air530"
description: "GPS Air530: how Juno reads it, with its driver API and an example."
layout: page
---

{|
## API

| Method | What it does |
|---|---|
| `GroveGpsAir530(gpio, clock, receivePin)` | Creates the reader on a digital pin, with a software UART at 9,600 baud. |
| `begin()` | Starts the reader. |
| `readPosition(sentence, sentenceLength, position, positionLength, timeoutMillis)` | Waits up to the timeout for a GGA sentence and fills `position` with latitude and longitude in millionths of a degree, altitude in centimetres, satellite count and fix quality. |
| `readSentence(sentence, sentenceLength, timeoutMillis)` | Reads one raw NMEA sentence into the caller's buffer. |
| `parseGga(sentence, sentenceLength, position, positionLength)` | Parses a GGA sentence that is already in a buffer. |

## Example

```java
GroveBaseShield shield = new GroveBaseShield();
Gpio gpio = MCU.getGpio();
GroveGpsAir530 gps = new GroveGpsAir530(gpio, MCU.getClock(), shield.getD2());
byte[] sentence = new byte[GroveGpsAir530.SENTENCE_CAPACITY];
int[] position = new int[GroveGpsAir530.POSITION_LENGTH];
gps.begin();

while (true) {
    if (gps.readPosition(sentence, sentence.length, position, position.length, 5_000)) {
        Serial.print("latitude=");
        Serial.println(position[GroveGpsAir530.LATITUDE_MICRODEGREES]);
        Serial.print("longitude=");
        Serial.println(position[GroveGpsAir530.LONGITUDE_MICRODEGREES]);
    }
}
```
|}
