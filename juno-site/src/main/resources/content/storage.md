---
title: "SD Card Storage"
description: "Loading configuration and credentials from an SD card at boot."
layout: page
---

Juno exposes read-only `SdFat32`/`File32` operations through `SdCard` as the standard
`java.io.InputStream`, so application code can construct a real `java.util.Properties` object and
call `Properties.load(InputStream)`. Juno recognizes those
standard-library bytecode calls and lowers them to a bounded embedded implementation; it does not
link the desktop JDK's `Hashtable` implementation into the sketch. `SdFat` is used instead of
Arduino's legacy `SD` library because `application.properties` is a long filename; the legacy
library is limited to DOS 8.3 names.

## Card contents

Create `application.properties` in the SD card root:

```properties
wifi.ssid=YOUR_WIFI_SSID
wifi.password=YOUR_WIFI_PASSWORD
```

The embedded parser accepts blank lines, `#`/`!` comments, and `key=value` or `key:value` entries.
It does not yet implement Java's escape, Unicode-escape, or continuation syntax. A document can
contain at most 16 entries; keys are limited to 31 bytes, values to 63, and physical lines to 95.
Exceeding a bound or encountering a card read failure invokes Juno's runtime panic handler.

## AZDelivery Data Logger Module Data Recorder Shield

`WifiStatusSD` is configured for the AZDelivery Data Logger Module Data Recorder Shield. Seat the
shield directly on the UNO R4 WiFi; its SD interface uses the classic UNO SPI pin mapping:

| SD module | UNO R4 WiFi |
|---|---|
| CS | D10 |
| MOSI | D11 |
| MISO | D12 |
| SCK | D13 |
| VCC | module-appropriate supply |
| GND | GND |

`SdCard.begin()` uses D10 by default, matching this shield and the standard Arduino shield layout.
The shield also contains an RTC, but this example does not initialize or use it. The card must be
formatted as FAT16 or FAT32; exFAT is not supported by the shield's documented SD setup.

If a different reader or shield routes CS elsewhere, call `SdCard.begin(chipSelectPin)` explicitly.

The ELEGOO 2.8" TFT touch screen shield's microSD socket also uses D10, and shares no pins with its
display: see [`WifiStatusTFT`](../tft-touch-shield#examples) for the same flow shown on the TFT.

## Build

Install the Arduino `SdFat` library once, then compile the example through the real toolchain:

```bash
./mvnw -f juno-examples/pom.xml juno:install-deps
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.net.WifiStatusSD
```

The essential Java flow is:

```java
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

if (SdCard.begin()) {
    InputStream file = SdCard.open("application.properties");
    Properties properties = new Properties();
    properties.load(file);
    file.close();
    Wifi.begin(
        properties.getProperty("wifi.ssid"),
        properties.getProperty("wifi.password"));
}
```

Because the standard `Properties.load(InputStream)` declaration throws `IOException`, the enclosing
method must declare `throws IOException` (or catch it). Juno's embedded implementation never actually
throws from `load`, so such a handler is accepted but never runs. As specified by the Java API, `load` reads from the stream's current position
and leaves it open, so close the `InputStream` explicitly.

## Random access with `RandomAccessFile`

For files read out of order, such as a game's data file with a directory of offsets, Juno supports the standard
`java.io.RandomAccessFile` in read-only mode. The same code runs unchanged on a desktop JVM against a local file:

```java
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

byte[] header = new byte[12];
if (SdCard.begin()) {
    try (RandomAccessFile file = new RandomAccessFile("DATA.BIN", "r")) {
        file.readFully(header);                // the first 12 bytes
        file.seek(file.length() - 16);         // jump anywhere in the file
        int read = file.read(header, 0, 4);    // up to 4 bytes, or -1 at the end
    } catch (FileNotFoundException missing) {
        // no such file on the card
    } catch (EOFException truncated) {
        // readFully ran out of file
    } catch (IOException failed) {
        // any other card error
    }
}
```

Supported: the `(String, "r")` constructor, `read()`, `read(byte[])`, `read(byte[], int, int)`, `readFully(byte[])`,
`readFully(byte[], int, int)`, `seek(long)`, `getFilePointer()`, `length()`, `skipBytes(int)` and `close()`.
Failures throw the JDK's own exceptions, as above, and a `RandomAccessFile` can be passed to helper methods.

Limits:

- The path is a compile-time string literal and the mode must be `"r"`; any other mode is a compile error.
- `read(byte[])` and `readFully(byte[])` take their length from the array, so they need a local array created
  with a constant size in the same method. Pass `(buffer, offset, length)` for any other array; its bounds are
  then not checked against the array's real size.
- `seek` past the end of the file throws `IOException` (the JDK allows it).
- `RandomAccessFile` and `SdCard.open()` share the card's four open-file slots.

Every byte array, like any other `new` object, lives in Juno's garbage-collected arena, 8 KB by default. A program
that keeps large tables in RAM, such as data loaded from a file at startup, can raise it with the Maven plugin's
JVM-style `-Djuno.Xmx` (for example `-Djuno.Xmx=32k`), and check the room left at run time with
`Memory.arenaCapacityBytes() - Memory.arenaUsedBytes()`. `-Djuno.Xss` sets each extra task's stack the same way.
