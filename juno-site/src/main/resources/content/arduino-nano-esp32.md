---
title: "Arduino Nano ESP32"
description: "Board notes for the Arduino Nano ESP32 and what Juno supports on it so far."
layout: page
---

The Nano ESP32 puts an Espressif ESP32-S3 (in a u-blox NORA-W106 module) on the Nano form factor.
Unlike the UNO boards, its CPU is an Xtensa LX7, so Juno generates Xtensa assembly for it with its
own backend instead of the Thumb-2 or AVR one.

## Core specification

| Characteristic | Nano ESP32 |
|---|---:|
| Product SKU | ABX00083 |
| Module | u-blox NORA-W106-10B (ESP32-S3) |
| CPU architecture | Dual-core 32-bit Xtensa LX7 |
| CPU clock | 240 MHz |
| Program flash | 16 MB (external) |
| SRAM | 512 kB, plus 8 MB PSRAM |
| Logic and operating voltage | 3.3 V |
| USB connector | USB-C (native USB) |
| Built-in LED | D13 (ESP32-S3 GPIO48) |
| Arduino CLI FQBN | `arduino:esp32:nano_nora` |

## What Juno supports

This is the first step of Nano ESP32 support: `Gpio`, `DigitalOutput`, `Delay` and `Serial` over
the native USB port, with `int`, `long`, `float` and `double` arithmetic, objects, arrays, static and
interface calls, lambdas and method references. Pins keep the numbers printed on the board: the core builds with
`BOARD_HAS_PIN_REMAP`, and Juno calls `pinMode`/`digitalWrite` through wrappers compiled under
that remapping, so `Arduino.getGpio().getBuiltinLed()` is `13` and drives GPIO48. Analog pins keep
Juno's UNO numbers: `Gpio.getA0()` to `Gpio.getA5()` (14 to 19) reach the Nano's A0 to A5, which its
core numbers 17 to 22, so Grove drivers and pin maps such as `GroveBaseShield` work unchanged. As a
result, the onboard RGB LED's core numbers (14 to 16) cannot be reached through `Gpio`.
The runtime resolves these portable identifiers through the core's named `D0`–`D13` and `A0`–`A5`
pins, so GPIO and SD chip-select still reach the same physical pins when the core is configured
with **By GPIO number (legacy)**. Do not pass raw ESP32 GPIO numbers to Juno's GPIO or SD APIs.

`long`, `float` and `double` operations go through the same runtime helpers as on the other boards, which
keep Java semantics (a `long` division by zero stops the program, float-to-int casts saturate, NaN
comparisons are false); the ESP32 toolchain compiles the `float` helpers to the ESP32-S3's
single-precision FPU (`double` has no FPU and runs as soft-float), and the double-precision
`java.lang.Math` functions use the toolchain's libm. [`SerialWideMath`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialWideMath.java)
prints a block of `long` and `float` results and
[`SerialDoubleMath`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialDoubleMath.java)
one of `double` and `Math` results, both to compare with a desktop JVM run;
[`SerialLambdas`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialLambdas.java)
exercises every lambda and method-reference shape.

Threads, `synchronized`, `ReentrantLock`, `StructuredTaskScope` and `ScopedValue` work too. Each
Juno thread is a FreeRTOS task with its own 8 KiB stack (`-Djuno.Xss` changes it), and only one
runs Java code at a time. A busy thread hands the CPU over at its next loop iteration or method
call once its 1 ms time slice is over, so a spin loop cannot starve the others. At most four
threads run at once, `main` included, unless `-Djuno.maxThreads` raises it; starting a fifth stops
the program with `[juno-thread] too many threads`.
[`SerialThreads`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialThreads.java)
runs each of them once at startup and then prints the results.

`SdCard` works over the board's SPI pins (D11 to D13) through the same SdFat runtime as the UNO
boards; see the Nano Connector Carrier below for a ready-made slot.

Wi-Fi works in station mode through the module's onboard radio: `Arduino.getWifi()` joins a network
with `begin(ssid, password)` and reports `status()`, `localIP()`, `gatewayIP()` and `macAddress()`.
[`NanoWifiStatusSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/NanoWifiStatusSD.java)
reads the credentials from `application.properties` on the carrier's microSD card (see
[SD Card Storage](../storage)). Plain HTTP works through `HttpClient`: the host is a literal in the
program and the request path can come from the card, as in
[`NanoHttpGetSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/NanoHttpGetSD.java).
HTTPS works the same way through `HttpClient.requestHttps`, with the host and path read from the
card. The Nano's TLS client does not check the server's certificate chain yet; the connection is
encrypted, but the server's identity is not verified. Email works over implicit TLS, `Smtp.sendTls` on port 465 and `Pop3Client` on port 995, with every
server, credential and message field read from the card:
[`NanoSmtpSendSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoSmtpSendSD.java)
sends one message and [`NanoPop3InboxSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoPop3InboxSD.java)
counts the mailbox's messages. A runtime `String` holds 31 bytes, so the password, subject and body must fit in
31 bytes each. STARTTLS (`Smtp.send`, port 587) upgrades a plain connection with the `ESP_SSLClient`
library, as on the UNO R4 WiFi: install it once with `arduino-cli lib install ESP_SSLClient`. The email
client does not check the server's certificate chain yet.

`HttpServer` runs on the station address:
[`NanoHttpServerSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/NanoHttpServerSD.java)
prints the board's address and answers `GET /status` on port 80.

`Arduino.getWifi().beginAP(ssid, password)` starts the board's own access point, so a phone or laptop can
join the Nano ESP32 directly, as in
[`NanoAccessPointSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/NanoAccessPointSD.java).
UDP, TCP, BLE and the other optional capabilities are not wired yet; Juno rejects a program that uses them
at compile time.

## Nano Connector Carrier

The Arduino Nano Connector Carrier (ASX00061) is passive: every connector is a Nano header pin, and
`NanoConnectorCarrier` names them so programs don't hard-code numbers:

| Connector | Nano pins | `NanoConnectorCarrier` |
|---|---|---|
| microSD slot (SPI) | CS D4 (D2/D3 by solder jumper), MOSI D11, MISO D12, SCK D13 | `getSdCard()`, `getSdChipSelect()`, `getSdChipSelectJumperD2()`/`D3()`, `getSpiMosi()`/`Miso()`/`Sck()` |
| Grove A0/A1 (analog) | A0 signal, A1 | `getGroveA0()`; wires `getA0()`, `getA1()` |
| Grove A2/A3 (analog) | A2 signal, A3 | `getGroveA2()`; wires `getA2()`, `getA3()` |
| Grove UART | RX D0 (signal), TX D1 | `getGroveUart()`; wires `getUartRx()`, `getUartTx()` |
| Grove I2C and Qwiic | SDA A4, SCL A5 | `getI2cSda()`, `getI2cScl()` (one bus, `Arduino.getI2c()`) |

As with `GroveBaseShield`, plug a single-signal Grove module in and pass the socket's getter to its
driver: `pir.begin(carrier.getGroveA2())`. The analog sockets work for digital modules too. Keep
digital modules out of the I2C socket: its bus pull-ups held a Grove PIR's output at about 1.4 V at
rest, neither low nor high. The class Javadoc has a diagram of the connectors, and pin details are in
the [ASX00061 datasheet](https://docs.arduino.cc/resources/datasheets/ASX00061-datasheet.pdf).

The input selector switch sets the Grove connectors' VCC to 3.3 V or 5 V; use 3.3 V with the Nano
ESP32. The Qwiic connector is always 3.3 V.

```java
NanoConnectorCarrier carrier = new NanoConnectorCarrier();
SdCard sdCard = carrier.getSdCard();
if (sdCard.begin()) {
    // read or write files on the microSD card
}
int light = new GroveLightSensorV12().rawValue(carrier.getGroveA0());
```

[`SdFileOperations`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java)
exercises the microSD slot end to end and reports each step over serial;
[`NanoCarrierGroveSensors`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/carrier/NanoCarrierGroveSensors.java)
reads a Grove PIR in the A2/A3 socket and a light sensor in A0/A1.

## Build and run

Install the core once with `arduino-cli core install arduino:esp32`. `Blink` builds for any board,
and [`SerialBlink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialBlink.java)
targets the Nano ESP32 and reports every LED change at 115200 baud:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-nano-esp32
```

The board can list two ports at once, its USB DFU port (for example `1-1`) and its serial port, so
the plugin cannot pick one alone. Upload through the DFU port and monitor the serial one:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.serial.SerialBlink -Djuno.port=1-1
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 \
  -Djuno.port=/dev/cu.usbmodem<ID> -Djuno.baudRate=115200
```

The USB port re-enumerates when the program starts, so a program should wait a moment after
`Serial.begin` before its first line, as `SerialBlink` does.
