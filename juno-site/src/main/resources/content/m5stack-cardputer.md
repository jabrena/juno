---
title: "M5Stack Cardputer"
description: "Send a Java Hello World message from the Cardputer and read it in the serial monitor."
layout: page
---

Juno's first Cardputer support adds the board target and an example that sends text over USB
serial. The example uses Juno's existing `Serial` and `Delay` APIs. The `M5` device facade is the
entry point for Cardputer peripherals. Display, keyboard and other
Cardputer peripherals are not part of this first step.

## GPIO

`M5.getGpio()` returns the Cardputer's GPIO interface. The following example toggles GPIO2 on the
HY2.0-4P port once a second:

```java
Gpio gpio = M5.getGpio();
int groveOutput = Gpio.getD2();
gpio.pinMode(groveOutput, Gpio.getOutput());
while (true) {
    gpio.digitalWrite(groveOutput, true);
    Delay.millis(1000);
    gpio.digitalWrite(groveOutput, false);
    Delay.millis(1000);
}
```

## Hello World

[`M5CardputerHelloWorld`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/m5stack/M5CardputerHelloWorld.java)
prints `Hello, World!` once a second, after a short startup pause so the serial monitor has time to
connect.

## SD card

`M5.getSd()` returns the Cardputer's built-in microSD socket. Calling `begin()` uses its standard SPI wiring: chip
select GPIO12, MOSI GPIO14, MISO GPIO39, and clock GPIO40.

```java
SdCard sd = M5.getSd();
if (sd.begin()) {
    Serial.println("SD card ready");
}
```

## Wi-Fi

`M5.getWifi()` returns the Cardputer's built-in ESP32-S3 radio. It uses the existing `Wifi` interface for station and
access point modes, so application services can receive a mock in unit tests.

```java
Wifi wifi = M5.getWifi();
wifi.beginAP("JunoCardputer", "juno1234");
Serial.println(wifi.status());
```
Install the Juno Maven plugin, then run `juno:install-deps` once to install the M5Stack ESP32 board
package along with the other supported board cores and libraries. Compile and upload the example:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.m5stack.M5CardputerHelloWorld
```

Open the serial monitor in another terminal:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=m5stack-cardputer
```

The monitor uses 115200 baud by default, matching `Serial.begin(BaudRate.BAUD_115200)`.
