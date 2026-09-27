# SD-card configuration

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
display: see [`WifiStatusTFT`](TFT-TOUCH-SHIELD.md#examples) for the same flow shown on the TFT.

### Using the LCD Keypad Shield at the same time

The LCD Keypad Shield uses D10 for backlight control, while the data logger shield uses D10 for SD
chip select. Do not stack both shields unchanged: electrically isolate or reroute the LCD
backlight's D10 connection first. `WifiStatusSD` initializes the LCD before the SD card and never
changes the backlight after SD initialization, but software ordering cannot remove the physical pin
conflict. The LCD itself continues to use D4-D9 and can display the connection state once D10 has
been isolated.

## Build

Install the Arduino `SdFat` library once, then compile the example through the real toolchain:

```bash
./mvnw -f juno-examples/pom.xml juno:install-deps
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.io.net.WifiStatusSD
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
