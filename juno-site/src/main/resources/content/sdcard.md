---
title: "SD Card"
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

`SdCard` is an interface: take the card from the shield or carrier that holds its socket, once, at
startup, and pass it to the classes that need it. A unit test passes `mock(SdCard.class)` instead.

| Socket | Card | Chip select |
|---|---|---|
| AZDelivery Data Logger Shield | `new DataLoggerShield().getSdCard()` | D10 |
| ELEGOO 2.8" TFT touch shield | `new TftTouchShield().getSdCard()` | D10 |
| LCD Keypad Shield (stacked on a data logger) | `new LcdKeypadShield().getSdCard()` | D10 |
| Arduino WiFi Shield (UNO R3) | `new ArduinoWifiShield().getSdCard()` | D4 |
| Nano Connector Carrier | `new NanoConnectorCarrier().getSdCard()` | D4 |

The card's `begin()` selects it on its socket's pin; `begin(chipSelectPin)` takes any other pin.

| Method | Argument | Notes |
|---|---|---|
| `begin()` | none | initializes the card on its socket's chip-select pin; returns whether it was detected |
| `begin(int)` | a chip-select pin | initializes the card on any other pin |
| `exists(String)` | a path | returns whether the path exists on the card |
| `createDirectory(String)` | a path | creates the path and its missing parent directories |
| `size(String)` | a path | the file's size in bytes; reports `Integer.MAX_VALUE` for files larger than that |
| `open(String)` | a path | opens the file for reading, or returns `null` when it cannot be opened |
| `open(String, String)` | a directory and a file stem | opens `directory/stem.txt` for reading, or returns `null` |
| `list(String, int, byte[], int)` | a directory, an index, a name buffer and its capacity | copies the name of the child at `index`; returns the number of bytes copied, `0` when there are no more children, or a negative value on failure |
| `appendLine(String, String)` | a path and a line | appends the line and a newline, creating the file when needed; the line may be a runtime string |
| `appendText(String, String)` | a path and text | appends the text with no newline added |
| `appendText(String, String, String)` | a directory, a file stem and text | appends to `directory/stem.txt`, creating it when needed |
| `remove(String)` | a path | deletes the file; returns whether it was removed |
| `removeDirectory(String)` | a path | deletes an empty directory; returns `false` when it is missing or not empty |
| `remove(String, String)` | a directory and a file stem | deletes `directory/stem.txt`; returns whether it was removed |
| `totalKiB()` | none | the card's total capacity in KiB, or `-1` before a successful `begin()` |
| `freeKiB()` | none | the free space in KiB; counts the whole allocation table, so it can take a while on a large card |

## Reading application properties

`application.properties` is a plain text file of `key=value` lines on the card. Open it with `open()`, load it into a
`java.util.Properties`, and read each value with `getProperty`. A key that is missing returns `null`, so check it
before use. The example reads the Wi-Fi credentials used by the examples on this site:

```java
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

SdCard sd = new DataLoggerShield().getSdCard();
if (sd.begin()) {
    try (InputStream file = sd.open("application.properties")) {
        if (file != null) {
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
        }
    } catch (IOException exception) {
        Serial.println("application.properties could not be read");
    }
}
```

The properties file on the card holds the same keys:

```properties
wifi.ssid=your-network
wifi.password=your-password
```

Juno's `Properties` support is bounded: a file may hold up to 8 entries, with keys of up to 15 characters and values of
up to 31 characters. A larger file, or a longer key or value, is not loaded in full.

## Build

Install the Arduino `SdFat` library once, then compile the example through the real toolchain:

```bash
./mvnw -f juno-examples/pom.xml juno:install-deps
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.net.wifi.WifiStatusSD
```

The essential Java flow is:

```java
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

SdCard sdCard = new DataLoggerShield().getSdCard();
if (sdCard.begin()) {
    InputStream file = sdCard.open("application.properties");
    Properties properties = new Properties();
    properties.load(file);
    file.close();
    ArduinoUno.getWifi().begin(
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
TftTouchShield tft = new TftTouchShield();
if (tft.getSdCard().begin()) {
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
`ArduinoUno.getBoardMemory().arenaCapacityBytes() - ArduinoUno.getBoardMemory().arenaUsedBytes()`. `-Djuno.Xss` sets each extra task's stack the same way.
