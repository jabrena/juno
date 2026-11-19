---
title: "AZDelivery Data Logger Shield"
description: "AZDelivery Data Logger Shield: a microSD socket on the UNO's SPI pins, read through DataLoggerShield."
layout: page
---

{|
A microSD socket on the UNO's SPI pins with chip select D10, plus a DS1307 real-time clock that Juno does not drive. `DataLoggerShield.getSdCard()` gives the card, which programs use for files and for Wi-Fi credentials kept out of the source. Its D10 is also the LCD Keypad Shield's backlight.

<img src="../../../images/shields/shield-data-logger.png" alt="AZDelivery Data Logger Shield" width="280">

## API

| Method | What it does |
|---|---|
| `getSdCard()` | The microSD card in the socket. Its `begin()` mounts the card. |
| `getSdChipSelect()` | The socket's chip select, D10. |
| `SdCard.begin()` | Mounts the card and returns whether it is available. |
| `SdCard.appendLine(path, line)` | Appends a line to a file, and returns whether the write succeeded. |
| `SdCard.size(path)` | The file's size in bytes. |

## Example

```java
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

public final class DataLoggerExample {
    private static final String LOG = "juno-log.txt";

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        SdCard sd = new DataLoggerShield().getSdCard();
        if (!sd.begin()) {
            Serial.println("SD card unavailable");
            return;
        }
        if (sd.appendLine(LOG, "Juno data logger is working")) {
            Serial.print("bytes written=");
            Serial.println(sd.size(LOG));
        } else {
            Serial.println("write failed");
        }
    }
}
```

[`SdFileOperations`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java) also reads the file back and deletes it.
|}
