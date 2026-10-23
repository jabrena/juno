---
title: "M5Stack Cardputer"
description: "Send a Java Hello World message from the Cardputer and read it in the serial monitor."
layout: page
---

Juno's first Cardputer support adds the board target and an example that sends text over USB
serial. The example uses Juno's existing `Serial` and `Delay` APIs. The `M5` device facade is the
entry point for Cardputer peripherals.

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

[`M5CardputerWifi`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/M5CardputerWifi.java)
starts an access point and reports its status over USB serial.

```java
Wifi wifi = M5.getWifi();
wifi.beginAP("JunoCardputer", "juno1234");
Serial.println(wifi.status());
```

[`M5CardputerWifiStatusSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/M5CardputerWifiStatusSD.java)
loads `wifi.ssid` and `wifi.password` from `application.properties` on the Cardputer's microSD card, connects to that
network, and refreshes the connection state and numeric Wi-Fi status on the built-in display. The properties file uses
the same format as the other SD-backed Wi-Fi examples:

```properties
wifi.ssid=your-network
wifi.password=your-password
```

Compile and upload it with:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.net.wifi.M5CardputerWifiStatusSD
```

## Display

`M5.getDisplay()` returns the Cardputer's 240 by 135 pixel color display. Colors use RGB565 values, with common colors
available as constants and `Display.color(red, green, blue)` for custom colors. Call `begin()` once before drawing.

```java
Display display = M5.getDisplay();
display.begin();
display.fillScreen(Display.BLACK);
display.fillRect(8, 8, display.width() - 16, 4, Display.BLUE);
display.setCursor(12, 24);
display.setTextColor(Display.GREEN, Display.BLACK);
display.setTextSize(2);
display.println("Hello, Juno!");
```

The display interface also provides `height()`, `drawPixel(...)`, `print(...)`, and `println(...)`. Applications can
receive a mock `Display` in desktop unit tests.

## Keyboard

`M5.getKeyboard()` returns the Cardputer's built-in keyboard. Initialize it once, call `update()` in the main loop,
and inspect the state after `isChanged()` reports a transition.

```java
Keyboard keyboard = M5.getKeyboard();
keyboard.begin();
while (true) {
    keyboard.update();
    if (keyboard.isChanged() && keyboard.isPressed()) {
        for (int index = 0; index < keyboard.keyCount(); index++) {
            Serial.println(keyboard.keyAt(index));
        }
    }
    Delay.millis(20);
}
```

`keyAt(...)` returns printable character codes or `Keyboard.BACKSPACE`, `Keyboard.TAB`, and `Keyboard.ENTER`.
`isKeyPressed(...)` checks one character or control key. Shift, Control, Alt, Fn, and Option have dedicated state
methods, so no array or collection needs to be allocated while polling.

Install the Juno Maven plugin, then run `juno:install-deps` once to install the M5Stack ESP32 board
package and the official M5Cardputer library along with the other supported board cores and libraries. Compile and
upload the example:

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
