# Arduino UNO WiFi Rev2 technical specification

> The linked board's official product name is **Arduino UNO WiFi Rev2**, not “UNO R3 WiFi.” This
> file keeps `ARDUINO-ONE-R3-WIFI.md` as its requested filename, but the hardware is based on the
> ATmega4809 and Arduino's megaAVR core rather than the UNO R3's ATmega328P and classic AVR core.

The UNO WiFi Rev2 combines an 8-bit ATmega4809 application microcontroller with a u-blox
NINA-W102 radio module. It retains the familiar UNO form factor and 5 V GPIO while adding Wi-Fi,
Bluetooth, a secure element and a six-axis inertial measurement unit (IMU).

## Core specification

| Characteristic | UNO WiFi Rev2 |
|---|---:|
| Product SKU | ABX00021 |
| Main microcontroller | Microchip ATmega4809 |
| CPU architecture | 8-bit megaAVR |
| CPU clock | 16 MHz |
| Program flash | 48 kB |
| SRAM | 6,144 bytes |
| EEPROM | 256 bytes |
| Logic and operating voltage | 5 V |
| Digital I/O | 14 pins, D0-D13 |
| PWM-capable pins | 5: D3, D5, D6, D9 and D10 |
| Analog inputs | 6 pins, A0-A5 |
| Analog output | No true DAC; `analogWrite` produces PWM |
| USB connector | USB Type-B |
| Built-in LED | Logical Arduino pin 25 |
| Radio module | u-blox NINA-W102 |
| Secure element | Microchip ATECC608A |
| IMU | STMicroelectronics LSM6DS3TR |
| Arduino CLI FQBN | `arduino:megaavr:uno2018` |

The ATmega4809 executes the Arduino sketch. The NINA-W102 is a separate radio processor with its
own firmware and TCP/IP stack. Applications normally communicate with it through Arduino's WiFiNINA
and ArduinoBLE libraries instead of programming the module directly.

## Power and electrical characteristics

| Item | Specification |
|---|---|
| GPIO logic level | 5 V |
| Recommended VIN/barrel-jack input | 6-20 V |
| USB power | 5 V through USB Type-B |
| DC current per I/O pin | 20 mA |
| 3.3 V pin maximum | 50 mA |

The traditional UNO headers use 5 V logic, but the radio and other onboard components operate in
lower-voltage domains through board-level translation and regulation. Do not connect external
loads directly to internal radio-module signals or exceed the documented header-pin limits.

Motors, relays and other high-current loads require an appropriate transistor, MOSFET or driver
circuit and normally a separate power supply. The per-pin limit is not a power budget for the
whole microcontroller; aggregate port and device-current limits also apply.

## I/O and buses

- **Digital:** D0-D13. Unlike the UNO R3 and R4, the onboard LED is not attached to D13; Arduino
  maps `LED_BUILTIN` to internal logical pin 25.
- **PWM:** D3, D5, D6, D9 and D10.
- **Analog:** A0-A5, backed by the ATmega4809 ADC.
- **UART:** D0/RX and D1/TX expose the user hardware serial interface. The USB serial monitor uses
  a separate internal serial path.
- **I2C/TWI:** exposed through SDA/SCL and the matching UNO header positions.
- **SPI:** exposed through the ICSP header; board-level SPI connections are also used to
  communicate with the NINA-W102.
- **USB:** used for programming and serial monitoring through the board's USB interface. The
  ATmega4809 does not provide native USB HID to the application sketch.

The ATmega4809 is not register-compatible with the UNO R3's ATmega328P. Arduino's megaAVR core
provides a compatibility layer for portable sketches, but libraries that manipulate AVR registers,
interrupt vectors or timers directly may require changes.

## Wireless, security and motion hardware

### NINA-W102 radio

The u-blox NINA-W102 is a self-contained radio module with 2.4 GHz Wi-Fi and Bluetooth/Bluetooth
Low Energy support. Its network stack and Arduino firmware allow the ATmega4809 to use wireless
features without implementing radio protocols itself. Wi-Fi is normally accessed through the
`WiFiNINA` library and Bluetooth Low Energy through `ArduinoBLE`.

The module can be reflashed or programmed directly for advanced experiments, but doing so is
outside the standard Arduino sketch workflow and can invalidate the module's radio certification.

### ATECC608A secure element

The secure element provides hardware-backed storage and cryptographic operations used for secure
network identity and TLS-related workflows. Applications access it through compatible Arduino
libraries; it is not ordinary EEPROM or general-purpose application memory.

### LSM6DS3TR IMU

The onboard LSM6DS3TR combines a three-axis accelerometer and three-axis gyroscope. It is connected
internally to the application MCU and is supported by Arduino's IMU libraries. Its presence does
not consume any of the six user-facing analog input pins.

## Memory and performance constraints

The board has three times the SRAM and 50% more flash than the UNO R3, but it remains an 8-bit
target with much less memory than either UNO R4 variant. SRAM is shared by global/static data, the
heap and the call stack. Wireless and IMU libraries also need working memory, reducing the amount
available to application code.

Juno represents supported Java values and generated temporaries as 32-bit integers. On the
ATmega4809, arithmetic on those values requires multiple 8-bit operations, and every live 32-bit
local consumes four bytes. Larger generated methods therefore need careful flash, SRAM and stack
budgeting even though the board is less constrained than the UNO R3.

## Physical compatibility

The board follows the UNO shield outline and header arrangement. Its nominal dimensions are
68.6 mm by 53.4 mm and its nominal weight is 25 g.

Mechanical compatibility does not guarantee software compatibility. Shields and libraries can
depend on R3-specific registers, timers or interrupt behavior, and the WiFi Rev2 reuses internal
SPI and serial resources for its onboard peripherals. Check the board pinout and library support
before selecting a shield.

## Arduino toolchain

The UNO WiFi Rev2 uses Arduino's **megaAVR** core, not the classic `arduino:avr` core used by the
UNO R3:

```bash
arduino-cli core update-index
arduino-cli core install arduino:megaavr
```

Generate a Juno sketch, then compile it for the board:

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main <ExampleClassName> \
  --classpath juno-examples/target/classes:juno/target/classes

arduino-cli compile \
  --fqbn arduino:megaavr:uno2018 \
  build/juno/<ExampleClassName>
```

Uploading changes the firmware on a connected physical board. Find its port with
`arduino-cli board list` before using `arduino-cli upload`.

## Juno compatibility

The UNO WiFi Rev2 is not currently a declared or continuously tested Juno target. The Juno backend
emits standard Arduino calls for its portable APIs, so the basic GPIO, timing and serial subset is
expected to work with the megaAVR core. It still requires end-to-end compiler and hardware testing
before being considered officially supported.

| Juno API or feature | UNO WiFi Rev2 | Notes |
|---|---:|---|
| `Gpio` and `DigitalOutput` | Expected | Uses standard Arduino GPIO calls |
| `Delay` and `Clock` | Expected | Uses Arduino delay and time functions |
| `Serial` | Expected | Uses the board's USB serial-monitor path |
| `Mouse` | No | The application MCU has no native USB HID support |
| `LedMatrix` | No | There is no onboard 12x8 LED matrix |
| Wi-Fi and Bluetooth | Not yet exposed | Juno has no WiFiNINA or ArduinoBLE intrinsic API |
| IMU and secure element | Not yet exposed | Juno has no intrinsic API for these peripherals |

The existing `Blink` example selects pin 13 explicitly and therefore does not address this board's
onboard LED. A WiFi Rev2-specific example must use logical pin 25 unless Juno gains a board-aware
`LED_BUILTIN` abstraction.

## Comparison with the UNO R3 and R4

| Characteristic | UNO R3 | UNO WiFi Rev2 | UNO R4 |
|---|---:|---:|---:|
| Main MCU | ATmega328P | ATmega4809 | Renesas RA4M1 |
| Architecture | 8-bit AVR | 8-bit megaAVR | 32-bit Arm Cortex-M4 |
| Clock | 16 MHz | 16 MHz | 48 MHz |
| Flash | 32 kB | 48 kB | 256 kB |
| SRAM | 2 kB | 6 kB | 32 kB |
| Built-in wireless | No | Wi-Fi and Bluetooth | WiFi variant only |
| Native USB on sketch MCU | No | No | Yes |
| Built-in LED pin | D13 | Logical pin 25 | D13 |
| Arduino core | `arduino:avr` | `arduino:megaavr` | `arduino:renesas_uno` |

## Official references

- [UNO WiFi Rev2 product page](https://store.arduino.cc/products/arduino-uno-wifi-rev2)
- [UNO WiFi Rev2 documentation](https://docs.arduino.cc/hardware/uno-wifi-rev2/)
- [UNO WiFi Rev2 pinout](https://docs.arduino.cc/resources/pinouts/ABX00021-full-pinout.pdf)
- [UNO WiFi Rev2 schematic](https://docs.arduino.cc/resources/schematics/ABX00021-schematics.pdf)
- [ATmega4808/4809 datasheet](https://ww1.microchip.com/downloads/en/DeviceDoc/ATmega4808-4809-Data-Sheet-DS40002173A.pdf)
- [NINA-W10 series datasheet](https://content.arduino.cc/assets/Arduino_NINA-W10_DataSheet_%28UBX-17065507%29.pdf)
- [LSM6DS3 datasheet](https://www.st.com/resource/en/datasheet/lsm6ds3.pdf)
- [ArduinoCore-megaavr](https://github.com/arduino/ArduinoCore-megaavr)

