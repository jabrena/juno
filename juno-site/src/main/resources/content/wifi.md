---
title: "Wifi"
description: "Connecting a Juno program to a Wi-Fi network on the UNO R4 WiFi, UNO WiFi Rev2, UNO Q and other boards with Wi-Fi."
layout: page
---

`Wifi` connects a Juno program to a Wi-Fi network. Get the radio once at startup, call `begin` with the network's name
and password, and wait until `status` reports that the connection is up.

## Connecting to Wi-Fi

Get the board's radio once, at startup. `ArduinoUno.getWifi()` returns it on the boards with onboard Wi-Fi (UNO R4
WiFi, UNO WiFi Rev2 and UNO Q). The UNO R3 has no radio, so the compiler rejects `ArduinoUno.getWifi()` there; stack the
retired [Arduino WiFi Shield](https://docs.arduino.cc/retired/shields/arduino-wifi-shield/) and take the same `Wifi`
from `new ArduinoWifiShield().getWifi()` instead. A unit test passes `mock(Wifi.class)`.

The credentials stay on the microSD card (see [SD Card](../sdcard)), so they are not written in the source. `Wifi.begin`
starts the connection; poll `Wifi.status()` until it equals `Wifi.STATUS_CONNECTED` before using the network:

```java
SdCard sd = new DataLoggerShield().getSdCard();
Wifi wifi = ArduinoUno.getWifi();

if (sd.begin()) {
    try (InputStream file = sd.open("application.properties")) {
        if (file != null) {
            Properties properties = new Properties();
            properties.load(file);
            wifi.begin(properties.getProperty("wifi.ssid"), properties.getProperty("wifi.password"));
        }
    } catch (IOException exception) {
        Serial.println("application.properties could not be read");
    }
}
```

A program that runs for a long time should also decide what to do after a disconnection, for example by calling
`Wifi.begin` again before its next request.

## Troubleshooting

- **Wi-Fi never reaches `STATUS_CONNECTED`:** check the network name and password, and the signal strength. A bounded
  retry loop keeps the failure visible.
