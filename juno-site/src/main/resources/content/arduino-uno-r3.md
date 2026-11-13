---
title: "Arduino UNO R3"
description: "Board notes for the Arduino UNO R3 (ATmega328P) and what Juno supports on it."
layout: page
---

The UNO R3 is the classic 5 V UNO board based on the 8-bit Microchip ATmega328P. Juno compiles for it with its **AVR
backend** and links against the `arduino:avr` core; the `ArduinoUno` device facade is the entry point for the board's
peripherals. With 2 KB of SRAM it is Juno's smallest target: the arena is 1 KB, and programs should allocate once at
startup and stay small.

<img src="../images/boards/arduino-uno-r3.png" alt="Arduino UNO R3" width="280">

## Core specification

| Characteristic | UNO R3 |
|---|---:|
| Main microcontroller | Microchip ATmega328P |
| CPU architecture | 8-bit AVR |
| CPU clock | 16 MHz |
| Program flash | 32 kB; 0.5 kB is used by the bootloader |
| SRAM | 2 kB |
| EEPROM | 1 kB |
| Logic and operating voltage | 5 V |
| Digital I/O | 14 pins, D0-D13 (6 with PWM: D3, D5, D6, D9, D10, D11) |
| Analog inputs | 6 pins, A0-A5, 10-bit ADC |
| USB connector | USB Type-B, through an ATmega16U2 USB-to-serial converter |
| Built-in LED | D13 |
| Wireless connectivity | None |
| Arduino CLI FQBN | `arduino:avr:uno` |
| Juno board id | `arduino-uno-r3` |

## What Juno supports

| Feature | Status | Notes | Example |
|---|:---:|---|---|
| `Gpio`, `DigitalOutput`, `Delay`, `Clock` | ✓ | Resolve `D0`–`D13` with `digitalPin` and `A0`–`A5` with `analogPin`; modes come from the board's `Gpio`. | [`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) |
| `analogRead`, `analogWrite` | ✓ | Analog reads on A0–A5 through the 10-bit ADC; PWM output on D3, D5, D6, D9, D10 and D11. | |
| `Serial` | ✓ | Through the ATmega16U2 USB-to-serial path. | |
| Classes, interfaces and inheritance | ✓ | Larger object graphs may exceed the 1 KB arena or the flash. | [`Inheritance`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/Inheritance.java), [`Interfaces`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/Interfaces.java) |
| `BigInteger`, `BigDecimal`, `MathContext` | ✓ | Bounded by the 1 KB arena and 2 KB of SRAM. | |
| `@Watchdog` | ✓ | ATmega328P hardware watchdog, at most 8 seconds. | |
| Non-cryptographic UUIDs (`PortNoise`) | ✓ | The R3 has no hardware entropy, so `UUID.randomUUID()` is rejected; `PortNoise` gives best-effort node identities, never secrets. | [`WeakUuid`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/language/WeakUuid.java) |
| `SdCard` | ✓ | On the Data Logger Shield's microSD slot. | [`StorageOccupancy`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/StorageOccupancy.java) |
| Grove Base Shield | ✓ | Passive: every socket is an UNO pin. | |
| 9 Axis Motion Shield | ✓ | BNO055 over I2C; its reset line is D7. | [`NineAxisMotionShieldSerial`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/imu/NineAxisMotionShieldSerial.java) |
| LCD Keypad Shield | ✓ | Uses D4-D10 for the LCD and backlight. | [`LcdKeypadDemo`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/lcd/LcdKeypadDemo.java) |

## Features

These need only the UNO R3 and its USB cable.

### Blink the built-in LED

```java
DigitalOutput led = DigitalOutput.of(ArduinoUno.getGpio().getBuiltinLed());

while (true) {
    led.high();
    Delay.millis(500);
    led.low();
    Delay.millis(500);
}
```

[`Blink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/gpio/Blink.java) is the complete program; it builds for any board, so pass `-Djuno.board=arduino-uno-r3`.

## Shields

Seat each shield directly on the UNO R3. Check which pins a shield uses before stacking two: the compiler rejects a
program whose shields share a pin, and the 9 Axis Motion Shield's sensor reset uses D7.

### Grove Base Shield

<img src="../images/shields/shield-grove.png" alt="Grove Base Shield" width="280">

The Grove Base Shield is passive: every socket is an ordinary UNO pin, and `GroveBaseShield` names them so programs
don't hard-code numbers. Plug a Grove module in and pass the socket's getter to its driver; the socket supplies the
module's `GND` and `VCC`. A Grove DHT22 temperature and humidity sensor in the D4 socket:

```java
GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
GroveDht22 dht = new GroveDht22();
int[] reading = new int[GroveDht22.READING_LENGTH];
int[] frame = new int[GroveDht22.FRAME_LENGTH];

if (dht.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
    Serial.print(reading[GroveDht22.TEMPERATURE_TENTHS_CELSIUS]);
    Serial.println(" tenths of a degree");
}
```

[`GroveDht22Monitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/sensors/GroveDht22Monitor.java) prints a DHT22's readings; it is written
for the UNO WiFi Rev2, and the snippet above is the same read for the R3. See [Grove](../grove-system) for the
sensor drivers.

### Arduino 9 Axis Motion Shield

<img src="../images/shields/shield-nine-axis.png" alt="Arduino 9 Axis Motion Shield" width="280">

The shield's Bosch BNO055 fuses its accelerometer and gyroscope itself and reports the heading in degrees.
`NineAxisMotionShield` reads it over I2C (A4/A5) as plain Java, with no Arduino library. The heading is relative to the
direction the sensor pointed at construction. Construction drives D7, the sensor's reset line, so don't use D7 for
anything else:

```java
NineAxisMotionShield imu = new NineAxisMotionShield();
imu.reset();                                      // count the turn from here
while (true) {
    Serial.println(imu.getAngle());               // heading since the reset, whole degrees, clockwise
    Delay.millis(200);
}
```

[`NineAxisMotionShieldSerial`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/imu/NineAxisMotionShieldSerial.java) prints heading, yaw rate,
and acceleration readings.

### LCD Keypad Shield

<img src="../images/shields/shield-lcd.png" alt="LCD Keypad Shield" width="280">

A 16 × 2 character LCD with five buttons read through one analog pin (A0). `LcdKeypadShield` needs no extra library:
the LCD uses D4-D9 and its backlight D10.

```java
LcdKeypadShield lcd = new LcdKeypadShield();
lcd.print("Hello, Juno");

while (true) {
    if (lcd.readButton() == LcdKeypadShield.SELECT) {
        lcd.setCursor(0, 1);
        lcd.print("SELECT pressed");
    }
    Delay.millis(100);
}
```

[`LcdKeypadDemo`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/lcd/LcdKeypadDemo.java) shows each button press.

### AZDelivery Data Logger Shield

<img src="../images/shields/shield-data-logger.png" alt="AZDelivery Data Logger Shield" width="280">

A microSD socket on the UNO's SPI pins (chip select D10, MOSI D11, MISO D12, SCK D13), plus a DS1307 real-time clock
that Juno does not drive. Format the card as FAT16 or FAT32.

```java
SdCard sd = new DataLoggerShield().getSdCard();
if (sd.begin()) {
    Serial.print("used KiB: ");
    Serial.println(sd.totalKiB() - sd.freeKiB());
}
```

[`StorageOccupancy`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/StorageOccupancy.java) reports the card's used and total space.

## Build and run

Install the AVR core once. Then compile and upload an example, for instance the UUID demo, which prints over serial:

```bash
arduino-cli core install arduino:avr
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.language.WeakUuid \
  -Djuno.board=arduino-uno-r3
```

Open the serial monitor in another terminal:

```bash
./mvnw -f juno-examples/pom.xml juno:monitor \
  -Djuno.board=arduino-uno-r3
```

The monitor uses 115200 baud by default, matching `Serial.begin(BaudRate.BAUD_115200)`.
