---
title: "Arduino UNO R3"
description: "Board notes for the Arduino UNO R3."
layout: page
---

> The official product name is **Arduino UNO R3** (also documented as UNO Rev3). This file keeps
> `ONE` in its filename to match the repository's requested documentation naming convention.

The UNO R3 is the classic 5 V UNO board based on the 8-bit Microchip ATmega328P. It shares the UNO
shield form factor and most high-level Arduino APIs with the UNO R4, but its CPU architecture,
memory capacity, USB implementation and available peripherals are substantially different.

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
| Digital I/O | 14 pins, D0-D13 |
| PWM-capable pins | 6: D3, D5, D6, D9, D10 and D11 |
| Analog inputs | 6 pins, A0-A5, 10-bit ADC |
| Analog output | No true DAC; `analogWrite` produces PWM |
| USB connector | USB Type-B |
| USB interface | ATmega16U2 USB-to-serial converter |
| Built-in LED | D13 |
| Wireless connectivity | None |
| Arduino CLI FQBN | `arduino:avr:uno` |

The ATmega328P executes the sketch. The separate ATmega16U2 implements the board's normal USB
serial connection; it is not a general-purpose coprocessor available to an ordinary UNO sketch.

## Power and electrical characteristics

| Item | Specification |
|---|---|
| GPIO logic level | 5 V |
| Recommended external input | 7-12 V through VIN or the barrel jack |
| External input limit | 6-20 V |
| USB power | 5 V through USB Type-B |
| DC current per I/O pin | 20 mA |
| 3.3 V pin maximum | 50 mA |

Supplying less than 7 V through VIN can leave the regulated 5 V rail below its intended voltage.
Supplying more than 12 V can overheat the linear regulator under load even though the documented
absolute input range extends to 20 V.

Do not drive motors, relays or other high-current loads directly from an I/O pin. Use an
appropriate transistor, MOSFET or driver circuit and observe both per-pin and aggregate
microcontroller current limits.

## I/O and buses

- **Digital:** D0-D13; D13 also drives the built-in LED.
- **PWM:** D3, D5, D6, D9, D10 and D11.
- **External interrupts:** D2 and D3.
- **Analog:** A0-A5 with 10-bit conversion, normally producing values from 0 through 1023.
- **UART:** D0/RX and D1/TX. These signals are also connected to the ATmega16U2 USB-to-serial
  interface.
- **I2C/TWI:** A4/SDA and A5/SCL, duplicated on the SDA/SCL header pins.
- **SPI:** D10/SS, D11/COPI, D12/CIPO and D13/SCK; also exposed on the ICSP header.
- **USB:** ordinary sketches use serial through the ATmega16U2. The ATmega328P does not provide
  native USB HID.

The SPI pin names MOSI and MISO are still common in older UNO R3 material. COPI and CIPO are the
controller/peripheral-neutral names for the same signals.

Timers are scarce shared resources on the ATmega328P. PWM, timing functions, servo libraries and
other libraries can compete for the same hardware timers even when their APIs compile correctly.

## Memory and performance constraints

The R3's 2 kB SRAM is shared by global/static data, the heap and the call stack. Stack exhaustion
can corrupt memory without a clean diagnostic. This is especially relevant to Juno because its
generated methods use 32-bit storage for Java locals and intermediate values, while the native AVR
register width is only 8 bits.

The AVR compiler implements 32-bit Java-style arithmetic using multiple 8-bit instructions and,
for some operations, runtime helper routines. Correct programs can therefore be larger and slower
than equivalent code targeting the 32-bit UNO R4. Division and remainder are notably more
expensive than addition, subtraction and bitwise operations.

## Physical compatibility

The UNO R3 uses the standard UNO shield outline and header layout. Its nominal dimensions are
approximately 68.6 mm by 53.4 mm and its nominal weight is 25 g. The unusual spacing between the
digital header blocks is part of the established UNO shield layout.

UNO-form-factor compatibility does not guarantee that an R4-specific shield or library works on
the R3. Check required voltage levels, current, timers, memory, CPU-specific code and peripheral
availability.

## Arduino toolchain

Install the AVR board core:

```bash
arduino-cli core update-index
arduino-cli core install arduino:avr
```

Generate and compile a Juno example for the R3 through the Maven plugin:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-uno-r3
```

Uploading changes the firmware on a connected physical board. Find the port with
`arduino-cli board list` before using `arduino-cli upload`.

## Juno compatibility

The UNO R3 is a declared Juno target. Select it with `@Board(ArduinoUnoR3Board.class)` or the board id
`arduino-uno-r3`. Programs without `@Board` can target it with `-Djuno.board=arduino-uno-r3`.
Gladiator remains an UNO Q/UNO R4 WiFi game and does not target the R3.

| Juno API or feature | UNO R3 | Notes |
|---|---:|---|
| `Gpio` and `DigitalOutput` | Yes | Uses standard Arduino GPIO calls |
| `Delay` and `Clock` | Yes | Uses Arduino delay and time functions |
| `Serial` | Yes | Uses the ATmega16U2 USB-to-serial path |
| `@Watchdog` | Yes | ATmega328P hardware watchdog; requested timeouts round up to a supported period, at most 8 seconds |
| `BigInteger`, `BigDecimal`, `MathContext` | Yes, bounded | Uses the shared `java.math` runtime, but the 1 kB arena and 2 kB SRAM limit practical values and program size |
| `UUID.randomUUID()` | No | The R3 has no board-native cryptographic entropy source; Juno rejects this call at compile time |
| `PortNoise.collectSeed(...)` | Yes, non-cryptographic | Samples a user-provided physical noise circuit, GPIO and timer jitter; it is unsuitable for secrets or UUID v4 generation |
| `Mouse` | No | The sketch MCU has no native USB HID support |
| `LedMatrix` | No | The R3 has no onboard 12x8 LED matrix |
| `ArduinoUno.getWifi()` | No | The R3 has no onboard radio; Juno rejects this call at compile time |
| Wi-Fi through the Arduino WiFi Shield | Yes | `new ArduinoWifiShield().getWifi()`, then `Udp`, `Tcp` and plain-HTTP `HttpClient`; no access point or HTTPS |
| Bluetooth | Not applicable | No onboard wireless hardware |
| DAC, RTC and CAN | Not applicable | These peripherals are not built into the R3 board |

The most important practical limit is SRAM. Juno limits the R3 arena to 1 kB and rejects APIs that
need task stacks, HTTPS, an LED matrix or native USB HID. Arbitrary-precision arithmetic is available
for small programs. Class inheritance and inherited/default dispatch are available, but can make the generated
sketch exceed the Arduino linker limits or the 1 kB arena. A single sketch that combines all type demonstrations does
not fit. A sketch passing
the Arduino compiler's global-memory report can still overflow at runtime because
that report does not calculate the maximum Juno call stack.

### Wi-Fi through the Arduino WiFi Shield

The R3 has no radio, so `ArduinoUno.getWifi()` does not compile for it. Stack the retired
[Arduino WiFi Shield](https://docs.arduino.cc/retired/shields/arduino-wifi-shield/) and take its
`Wifi` from the shield instead:

```java
Wifi wifi = new ArduinoWifiShield().getWifi();
wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
```

The shield runs Arduino's legacy `WiFi` library over SPI and reserves D10 (Wi-Fi chip select), D4
(its SD card) and D7 (handshake), so it cannot be stacked with the LCD Keypad Shield. It joins
networks only (no access point) and speaks plain HTTP, not HTTPS. Only small network programs fit
next to the library: build them with `-Djuno.Xmx=512`, as `UdpEcho`, `TcpEcho`, `SntpTime` and
`PlainHttpWeather` do.

## Official references

- [UNO R3 board page](https://docs.arduino.cc/hardware/uno-rev3/)
- [UNO R3 datasheet](https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf)
- [UNO R3 pinout](https://docs.arduino.cc/resources/pinouts/A000066-full-pinout.pdf)
- [ATmega328P product page](https://www.microchip.com/en-us/product/ATmega328P)
- [ArduinoCore-avr](https://github.com/arduino/ArduinoCore-avr)
