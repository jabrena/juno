---
title: "Raspberry Pi Pico 2 W"
description: "Board notes for the Raspberry Pi Pico 2 W and what Juno supports on it: GPIO, Wi-Fi and the Grove Shield for Pi Pico."
layout: page
---

The Raspberry Pi Pico 2 W pairs the RP2350's Cortex-M33 with an Infineon CYW43439 Wi-Fi radio. The UNO Q is a
Cortex-M33 too, so Juno compiles for the Pico with the same Thumb-2 backend, linking against Earle Philhower's
[arduino-pico](https://github.com/earlephilhower/arduino-pico) core (`rp2040:rp2040:rpipico2w`).

<img src="../images/boards/raspberry-pico2-wifi.png" alt="Raspberry Pi Pico 2 W" width="280">

Juno's support on this board:

- **GPIO and serial** through `RaspberryPico2.getGpio()`. `A0`–`A2` are the ADC pins GP26–GP28, and `GP*`/`GPIO*`
  names reach the header pins. `getBuiltinLed()` is the LED wired to the radio chip, which the core drives as pin 64.
- **Wi-Fi** through `RaspberryPico2.getWifi()`: station and access-point modes, with UDP, TCP sockets and SNTP.
- **[Grove Shield for Pi Pico](#grove-shield-for-pi-pico)** through `GrovePicoShield`, a pin map of
  its D16, D18, D20, A0–A2 and I2C sockets.
- **`java.math`** (`BigInteger`, `BigDecimal`).

HTTP, TLS, Bluetooth LE, `@Watchdog` and threads are not wired yet; a program that uses them is rejected at compile
time with the board's name.

Install the core with `juno:install-deps`. To flash a new board, hold BOOTSEL while plugging it in; `juno:upload`
finds its `UF2_Board` drive by itself, and later uploads reset the board through its USB serial port.

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.RaspberryPico2Blink \
  -Djuno.board=raspberry-pi-pico-2-w
```

`RaspberryPico2Blink` is confirmed on hardware; the Wi-Fi and Grove shield examples are compiled but not yet run on a
board.

## Grove Shield for Pi Pico

<img src="../images/shields/shield-grove-pico.webp" alt="Grove Shield for Pi Pico" width="240">

Seeed Studio's [Grove Shield for Pi Pico v1.0](https://www.seeedstudio.com/Grove-Shield-for-Pi-Pico-v1-0-p-4846.html)
does for the Pico what the [Grove Base Shield](../arduino-shields#grove-base-shield) does for the UNO: it is
passive, and `GrovePicoShield` names its sockets. The digital sockets carry their GPIO number in the label (`D16` is
GP16), the analog sockets `A0`–`A2` are the Pico's ADC pins GP26–GP28, and the I2C0 (GP8/GP9) and I2C1 (GP6/GP7)
socket pins are exposed. Plug I2C modules into the I2C0 socket and pass `shield.getI2c0()`, the bus on its GP8/GP9, to
their driver; the I2C1 pins are only for drivers that wire their own bus. The UART and SPI sockets are not mapped yet. Set the
shield's power switch to 3.3 V for modules that need it.

The shield seen from above, USB at the top, with each socket's pins as printed under it. The first pin is the `SIG`
wire its getter returns. The analog sockets always take 3.3 V; the others take `VCC` from the power switch.

```text
+----------------------------[ USB ]-----------------------------+
| SPI0  GND o o SCK                              VCC switch:  5V |
|       3V3 o o TX                                           3V3 |
|       CS  o o RX                                               |
|                                                                |
| UART0  (not mapped)                     I2C0  SCL=GP9  SDA=GP8 |
| [RX |TX |VCC|GND]                            [SCL|SDA|VCC|GND] |
| UART1  (not mapped)                     I2C1  SCL=GP7  SDA=GP6 |
| [RX |TX |VCC|GND]        Raspberry Pi        [SCL|SDA|VCC|GND] |
| D16  SIG=GP16              Pico 2 W               A0  SIG=GP26 |
| [D16|D17|VCC|GND]        (sits here)         [A0 |NC |3V3|GND] |
| D18  SIG=GP18                                     A1  SIG=GP27 |
| [D18|D19|VCC|GND]                            [A1 |A0 |3V3|GND] |
| D20  SIG=GP20                                     A2  SIG=GP28 |
| [D20|D21|VCC|GND]                            [A2 |A1 |3V3|GND] |
+-------------------[ DEBUG: SWCLK GND SWDIO ]-------------------+
```

| Socket | `GrovePicoShield` getter | Pico pin |
|---|---|---|
| D16 | `getD16()` | GP16 |
| D18 | `getD18()` | GP18 |
| D20 | `getD20()` | GP20 |
| A0 | `getA0()` | GP26 (ADC0) |
| A1 | `getA1()` | GP27 (ADC1) |
| A2 | `getA2()` | GP28 (ADC2) |
| I2C0 | `getI2c0()` (the bus), `getI2c0Sda()`, `getI2c0Scl()` | GP8, GP9 |
| I2C1 | `getI2c1Sda()`, `getI2c1Scl()` | GP6, GP7 |
| UART0, UART1, SPI0 | not mapped yet | |

With a Grove LED in socket D16 and an analog module, such as a light sensor, in A0:

```java
import io.github.jabrena.juno.hardware.RaspberryPico2;
import io.github.jabrena.juno.hardware.device.grove.GrovePicoShield;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GrovePicoExample {
    public static void main(String[] args) {
        Gpio gpio = RaspberryPico2.getGpio();
        GrovePicoShield shield = new GrovePicoShield(gpio);
        DigitalOutput led = DigitalOutput.of(shield.getD16());
        Serial.begin(BaudRate.BAUD_9600);

        while (true) {
            led.high();
            Delay.millis(500);
            led.low();
            Serial.println(gpio.analogRead(shield.getA0()));
            Delay.millis(500);
        }
    }
}
```

[`RaspberryPico2GroveShield`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/RaspberryPico2GroveShield.java)
is the complete example.
