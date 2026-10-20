---
title: "Grove Base Shield and sensors"
description: "Reading Grove temperature, compass, gas and motion sensors through the passive Base Shield."
layout: page
---

The [Grove Base Shield](https://wiki.seeedstudio.com/Base_Shield_V2/) is a passive adapter for
the UNO headers. Its A0-A3, D2-D8, UART and I2C sockets are ordinary UNO pins; Juno exposes their
map through [`GroveBaseShield`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/GroveBaseShield.java).

## Grove Temperature and Humidity Sensor Pro

[`GroveDht22`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveDht22.java)
is a library-free, allocation-free driver for Seeed's [Grove Temperature and Humidity Sensor
Pro](https://wiki.seeedstudio.com/Grove-Temperature_and_Humidity_Sensor_Pro/), which uses the
DHT22 (AM2302) frame format. Plug the module into the shield's D4 socket: the shield supplies
`GND` and `VCC`, and the driver reads the `SIG` wire on `D4`.

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht22;

GroveDht22 dht22 = new GroveDht22();   // create once, at startup
int[] reading = new int[GroveDht22.READING_LENGTH];
int[] frame = new int[GroveDht22.FRAME_LENGTH];
GroveBaseShield shield = new GroveBaseShield();   // the socket map: one object, created once
if (dht22.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
    int humidityTenths = reading[GroveDht22.HUMIDITY_TENTHS_PERCENT];
    int temperatureTenths = reading[GroveDht22.TEMPERATURE_TENTHS_CELSIUS];
}
```

The result and raw-frame buffers belong to the caller and are reused across readings. Values are
stored in tenths of a percent and tenths of a degree Celsius; a failed response, checksum or
out-of-range value returns `false` without changing the previous result. Wait at least two seconds
between reads. The complete serial example is
[`GroveDht22Monitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/sensors/GroveDht22Monitor.java).

`new GroveDht22(gpio, clock)` (and the same for `GroveDht11`) runs the driver on a given `Gpio` and
`Clock` instead of the board's, which is how its unit tests feed it a scripted sensor reply through a
mocked `Gpio`.

This driver is implemented in Java over `Gpio`, `Clock`'s `micros()` and `Delay`; it adds no Arduino
library or compiler intrinsic and therefore works on the UNO R3, UNO WiFi Rev2, UNO R4 WiFi and
UNO Q targets.

## DHT11, temperature, sound and light batch

The DHT11 is a separate, whole-number sensor from the DHT22-format Temperature and Humidity
Sensor Pro. Use [`GroveDht11`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveDht11.java)
for the [Seeed DHT11 module](https://www.seeedstudio.com/Grove-Temperature-Humidity-Sensor-DHT11.html).
The DHT11 signal is digital, so connect it to **D4** (or another digital socket and pass that pin
to the driver).

For the new analog modules, use separate analog sockets. The recommended wiring for
[`GroveSensorBatchMonitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/sensors/GroveSensorBatchMonitor.java)
is:

| Module | Shield socket | Juno driver | Result |
|---|---|---|---|
| [Grove Temperature Sensor v1.2](https://wiki.seeedstudio.com/Grove-Temperature_Sensor_V1.2/) | **A0** | `GroveTemperatureSensorV12` | Celsius and raw ADC count |
| [Grove Sound Sensor v1.6](https://wiki.seeedstudio.com/Grove-Sound_Sensor/) | **A1** | `GroveSoundSensorV16` | Raw ADC count |
| [Grove Light Sensor v1.2](https://www.seeedstudio.com/Grove-Light-Sensor-p-746.html) | **A2** | `GroveLightSensorV12` | Raw ADC count |

All three analog modules expose a voltage on `SIG`; they cannot be read from D7 or D8. The
temperature driver applies Seeed's v1.2 thermistor equation through the float-returning
`celsius(int)` of a `GroveTemperatureSensorV12` created once at startup, using the documented 100 kΩ nominal resistance and
`B = 4275 K` constants. Its allocation-free float logarithm keeps the driver compatible with
the UNO WiFi Rev2, whose toolchain does not provide Juno's double-precision Math intrinsics.
`rawValue(int)` and `celsiusFromRaw(int)` are also available when an application needs to retain
the sample or inspect the calibration. The sound module is an amplified microphone signal, not a
calibrated dB meter, and the light module's ADC count is not automatically lux. Each sensor is an
object: `new GroveLightSensorV12()` reads the board's pins, and `new GroveLightSensorV12(gpio)` reads
a given `Gpio`, which is how the unit tests mock the analog pin; the same holds for
`GroveSoundSensorV16`, `GroveTemperatureSensorV12` and `GrovePirMotion`.

Run the dedicated monitor with:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.board=arduino-uno-r3-wifi-rev2 \
  -Djuno.main=io.github.jabrena.juno.api.hardware.device.grove.sensors.GroveSensorBatchMonitor
```

### Observed DHT22-format readings

The connected module on the UNO WiFi Rev2 produced the checksum-valid raw frame
`2 120 0 242 108`. Interpreting the first and third bytes as whole-number values would display
`2% / 0°C`, but the frame follows the [DHT22 format](https://wiki.seeedstudio.com/Grove-Temperature_and_Humidity_Sensor_Pro/):
`(2 × 256 + 120) / 10 = 63.2%` humidity and `(0 × 256 + 242) / 10 = 24.2°C`.
This identifies the **wire format**, not necessarily the marking or silicon inside the module.
The API stores values in tenths of a degree Celsius and tenths of a percent, without floating point
or allocations in the loop. Occasional bad-checksum or incomplete frames still return `false`; they
are not reported as measurements.

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.board=arduino-uno-r3-wifi-rev2 \
  -Djuno.main=io.github.jabrena.juno.api.hardware.device.grove.sensors.GroveDht22Monitor
```

## Compass, gas and PIR wiring check

Grove sockets have the same connector, but they do not all carry the same signals:

| Module | Correct shield socket | What the example reads |
|---|---|---|
| [Grove 3-Axis Digital Compass V2.0 (BMM150)](https://wiki.seeedstudio.com/Grove-3-Axis_Digitial_Compass_v2.0/) | Any **I2C** socket | Raw X/Y/Z magnetic counts |
| [Analog Grove gas sensor](https://wiki.seeedstudio.com/Grove-Gas_Sensor/) | **A0** (or another analog socket) | Raw ADC value, not calibrated gas concentration |
| [Grove PIR motion sensor](https://wiki.seeedstudio.com/Grove-PIR_Motion_Sensor/) | **D3** (or another digital socket) | Digital motion signal |

A compass plugged into D8 is not connected to SDA/SCL, and an analog gas sensor plugged into D7
cannot be measured with `analogRead`. Move those plugs before using
[`GroveSensorWiringMonitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/sensors/GroveSensorWiringMonitor.java).
The probe identifies the V2.0 compass's BMM150 chip and the loop reads its raw X/Y/Z magnetic
counts. These counts are uncalibrated sensor units, not a heading in degrees. The monitor also
prints the gas ADC value and PIR state. The exact gas sensor model and its calibration are required before
turning ADC readings into concentrations or alarm thresholds.

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.board=arduino-uno-r3-wifi-rev2 \
  -Djuno.main=io.github.jabrena.juno.api.hardware.device.grove.sensors.GroveSensorWiringMonitor
```

## Publishing temperatures to Supabase

[`SupabaseGroveTemperatures`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/usecases/supabase/publisher/SupabaseGroveTemperatures.java)
reads three temperature sensors once a minute and inserts them as one row into a Supabase
`sensor-data` table through PostgREST:

| Column | Module | Shield socket |
|---|---|---|
| `temp1` | Temperature and Humidity Sensor Pro (DHT22 format) | **D4** |
| `temp2` | DHT11 | **D2** |
| `temp3` | Temperature Sensor v1.2 | **A0** |
| `ts` | UTC from `LocalDateTime.now()` | — |

Temperatures are sent as JSON numbers with one decimal; a sensor that fails to read is sent as
`null`. `id` is left to the table's default. The request headers are all chosen by the program:

```java
HttpHeaders headers = new HttpHeaders(requestHeaders, REQUEST_HEADERS_CAPACITY)
        .add("apikey", System.getenv("SUPABASE_API_KEY"))
        .add("Content-Type", "application/json")
        .add("Prefer", "return=representation");
```

The body is built at runtime in a caller-owned buffer and sent with the allocation-free
`HttpClient.post(url, requestHeaders, length, requestBody, length, …)` overload. A
`Content-Type` header supplied this way replaces the default `application/json`.
`SUPABASE_API_URL` is the complete table endpoint, for example
`https://<project>.supabase.co/rest/v1/sensor-data`, and together with `SUPABASE_API_KEY`,
`JUNO_WIFI_SSID` and `JUNO_WIFI_PASSWORD` it comes from the module's `.env` file. Use a publishable
key protected by Row Level Security, never a secret key. Wi-Fi, TLS and the three drivers exceed the
UNO WiFi Rev2's flash, so this example targets the UNO R4 WiFi and UNO Q:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.board=arduino-uno-r4-wifi \
  -Djuno.main=io.github.jabrena.juno.usecases.supabase.publisher.SupabaseGroveTemperatures
```
