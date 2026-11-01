---
title: "Arduino Shields"
description: "The shields and carriers Juno drives, what each one does and which boards it works with."
layout: page
---

A shield plugs onto a board's headers and adds hardware the board does not have. Juno drives each one from plain
Java: most need no Arduino library, and every shield class hands its pins and devices to the program, so a unit test
can pass mocks instead. Seat a shield directly on the board, and check the pins before stacking two.

| Shield | Adds | UNO R3 | UNO WiFi Rev2 | UNO R4 WiFi | UNO Q | Nano ESP32 |
|---|---|:---:|:---:|:---:|:---:|:---:|
| [TFT Touch Shield](#tft-touch-shield) | 2.8" color touch display, microSD | | ✓ | ✓ | ✓ | |
| [Grove Base Shield](#grove-base-shield) | Grove sockets | ✓ | ✓ | ✓ | ✓ | |
| [Arduino 9 Axis Motion Shield](#arduino-9-axis-motion-shield) | BNO055 orientation sensor | ✓ | ✓ | ✓ | ✓ | |
| [LCD Keypad Shield](#lcd-keypad-shield) | 16 × 2 character LCD, five buttons | ✓ | ✓ | ✓ | ✓ | |
| [AZDelivery Data Logger Shield](#azdelivery-data-logger-shield) | microSD, real-time clock | ✓ | ✓ | ✓ | ✓ | |
| [Arduino WiFi Shield](#arduino-wifi-shield) | Wi-Fi for a board without a radio | ✓ | | | | |
| [Nano Connector Carrier](#nano-connector-carrier) | microSD, Grove and Qwiic sockets | | | | | ✓ |

## TFT Touch Shield

<img src="../images/shields/shield-tft.png" alt="ELEGOO 2.8-inch TFT Touch Shield" width="240">

The ELEGOO 2.8" shield: a 240 × 320 ILI9341 color display on an 8-bit parallel bus, a resistive touch panel and a
microSD socket. `TftTouchShield` draws, prints text and reads touches, and `ParallelBus` streams pixels from the
runtime. Juno's games run on it.

## Grove Base Shield

<img src="../images/shields/shield-grove.png" alt="Grove Base Shield" width="240">

Seeed Studio's passive adapter: every Grove socket is an ordinary UNO pin, and `GroveBaseShield` names them. Plug a
Grove module in and pass the socket's getter to its driver. See [Grove](../grove-system) for the sensors.

## Arduino 9 Axis Motion Shield

<img src="../images/shields/shield-nine-axis.png" alt="Arduino 9 Axis Motion Shield" width="240">

A Bosch BNO055 that fuses its accelerometer and gyroscope and reports the heading in degrees. `NineAxisMotionShield`
reads it over I2C with no Arduino library, which suits measuring how far a vehicle has turned. It holds D7 as the
sensor's reset line.

## LCD Keypad Shield

<img src="../images/shields/shield-lcd.png" alt="LCD Keypad Shield" width="240">

A 16 × 2 character LCD on D4-D9, its backlight on D10, and five buttons read through one analog pin. `LcdKeypadShield`
needs no library.

## AZDelivery Data Logger Shield

<img src="../images/shields/shield-data-logger.png" alt="AZDelivery Data Logger Shield" width="240">

A microSD socket on the UNO's SPI pins with chip select D10, plus a DS1307 real-time clock that Juno does not drive.
`DataLoggerShield.getSdCard()` gives the card, which programs use for files and for Wi-Fi credentials kept out of the
source. Its D10 is also the LCD Keypad Shield's backlight.

## Arduino WiFi Shield

<img src="../images/shields/shield-wifi.png" alt="Arduino WiFi Shield" width="240">

The retired Arduino WiFi Shield gives the UNO R3, which has no radio, Wi-Fi, UDP, TCP and plain HTTP through
`new ArduinoWifiShield().getWifi()`. It reserves D4, D7 and D10, so it cannot be stacked with the LCD Keypad Shield.

## Nano Connector Carrier

<img src="../images/shields/shield-nano-connector-carrier.png" alt="Arduino Nano Connector Carrier" width="240">

Arduino's passive carrier for the Nano ESP32: a microSD slot, four Grove sockets and a Qwiic connector, all named by
`NanoConnectorCarrier`. See the [Nano ESP32](../arduino-nano-esp32#nano-connector-carrier) page for its pin map and
examples, including remote-controlling a LEGO RCX over infrared.
