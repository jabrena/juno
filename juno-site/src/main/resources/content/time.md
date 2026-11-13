---
title: "Time"
description: "Reading UTC with LocalDateTime.now() over SNTP, configuring the server, and measuring elapsed time with Clock."
layout: page
---

Juno gives a program two clocks. [`LocalDateTime.now()`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/net/udp/SntpClock.java)
returns the current UTC date and time, synchronized over SNTP on the boards with Wi-Fi. [`Clock`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/time/Clock.java)
counts the milliseconds and microseconds since the board started, with no network at all. Use `LocalDateTime` for wall-clock
time and `Clock` for intervals.

| API | Argument | Notes |
|---|---|---|
| `LocalDateTime.now()` | none | UTC, from SNTP; returns `LocalDateTime.MIN` until the clock has synchronized |
| `getYear()`, `getMonthValue()`, `getDayOfMonth()` | none | date fields of a `LocalDateTime` |
| `getHour()`, `getMinute()`, `getSecond()` | none | time fields of a `LocalDateTime` |
| `equals(Object)`, `toString()` | a `LocalDateTime` or none | `equals` compares the fields; `toString` prints ISO-8601 |
| `LocalDateTime.MIN` | none | the "not synchronized" sentinel |
| `SntpClock.configure(Properties)` | a `Properties` loaded from the SD card | replaces the SNTP host and port at run time; returns `false` if a value is invalid |
| `Clock.millis()` | none | milliseconds since start, wrapping after about 24.8 days |
| `Clock.micros()` | none | microseconds since start, wrapping after about 35.8 minutes |

Any other `java.time.LocalDateTime` method, such as `plus`, `of`, `parse` or `format`, is rejected at compile time with a
`CompileException`; the subset is deliberately small.

## Reading the time with LocalDateTime.now()

`LocalDateTime.now()` needs the board to be on a Wi-Fi network first. Connect with [`Wifi`](../wifi), wait until
`status()` reports `Wifi.STATUS_CONNECTED`, and then call `now()`:

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import java.time.LocalDateTime;

Wifi wifi = MCU.getWifi();
wifi.begin(ssid, password);
while (wifi.status() != Wifi.STATUS_CONNECTED) {
    Delay.millis(500);
}

LocalDateTime now = LocalDateTime.now();
int hour = now.getHour();
String text = now.toString();      // for example "2026-11-01T14:05:09"
```

`toString()` follows Java's format: the seconds are printed only when they are not zero, so `14:05` is shown when the
second is `0`.

### How the clock synchronizes

The first `now()` call after the connection is up starts one SNTP exchange. The steps are:

- Juno sends a 48-byte SNTP request on its own UDP socket and waits up to 2 seconds for the reply. The socket is separate
  from the one [`Udp`](../wifi) uses, so the two never share state.
- The server's transmit time becomes the base; later calls advance it with the board's millisecond counter, so most calls
  send no network traffic.
- The base is refreshed from the server once an hour. If a synchronization fails, Juno retries no sooner than every
  60 seconds, and `now()` returns `LocalDateTime.MIN` in the meantime.

Until the first successful exchange, `now()` returns `LocalDateTime.MIN` rather than a guess, so a program must check for
it before trusting the value. Every call allocates a small object in the program's arena, so read the time once per
printed line or once per second, not in a tight loop.

### Choosing the SNTP server

The default is `pool.ntp.org` on port `123`. Change it at build time with the `JUNO_SNTP_HOST` and `JUNO_SNTP_PORT`
environment variables, or at run time with `SntpClock.configure`, which reads the `sntp.host` and `sntp.port` keys of a
`Properties` object:

```java
import io.github.jabrena.juno.hardware.net.udp.SntpClock;
import java.util.Properties;

Properties properties = /* loaded from application.properties on the SD card */;
SntpClock.configure(properties);   // call once, before the first now()
```

A missing key keeps the build-time value. The host must be at most 63 characters of letters, digits, dots and hyphens,
and the port must be between 1 and 65535; otherwise `configure` returns `false` and leaves the settings unchanged. The
loaded `Properties` is typically read from the SD card as shown in [SD Card](../sdcard).

## Measuring elapsed time with Clock

`Clock` is the board's own monotonic counter, so it does not depend on Wi-Fi. Get it once at startup with
`MCU.getClock()` and pass it to whatever needs it:

```java
import io.github.jabrena.juno.hardware.time.Clock;

Clock clock = MCU.getClock();

int start = clock.millis();
doWork();
int elapsed = clock.millis() - start;    // correct across one wrap of the counter
```

Subtracting two `millis()` readings gives the right answer even after the counter wraps, as long as the interval itself is
shorter than the wrap period. Use `micros()` for short intervals, and `Delay` ([Delay](../delay)) when you only need to pause.
A unit test passes `mock(Clock.class)` instead of the board's clock.

## A minimal example

[`SntpTime`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTime.java)
joins Wi-Fi, then prints UTC every ten seconds, or `sntp unavailable` when the clock has not synchronized yet:

```java
LocalDateTime now = LocalDateTime.now();
if (now.equals(LocalDateTime.MIN)) {
    Serial.println("sntp unavailable");
} else {
    Serial.println(now.toString());
}
Delay.millis(10000);
```

The same program reads its Wi-Fi credentials and SNTP server from the SD card in
[`SntpTimeSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/SntpTimeSD.java),
and [`NanoSntpTimeSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/NanoSntpTimeSD.java)
does the same on a Nano ESP32 in a Nano Connector Carrier.

Build and watch the output with the Maven plugin, as described in [Serial](../serial):

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.hardware.net.udp.SntpTime

./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=arduino-uno-r4-wifi
```

## Troubleshooting

- **`sntp unavailable` on every line:** the board is not connected to Wi-Fi, the network blocks outbound UDP port `123`,
  or the configured server does not answer within 2 seconds. Check `wifi.status()` first, then point `sntp.host` at
  another server.
- **The time is wrong by a fixed amount:** the clock is UTC. Apply the local offset in the program; Juno has no time-zone
  support.
- **The time stops advancing:** `now()` advances from the board's counter and only resynchronizes hourly, so a
  synchronization that failed long ago keeps the last base. Call `now()` periodically to let the retry run.
- **`CompileException` for a `LocalDateTime` method:** the method is outside the supported subset listed above.
- **`SntpClock.configure` returns `false`:** a supplied host or port is invalid; the settings are unchanged.
