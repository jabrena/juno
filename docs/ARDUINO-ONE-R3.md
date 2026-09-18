# Arduino UNO R3 technical specification

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

Generate a Juno sketch, then compile it for the R3:

```bash
java -jar juno/target/juno-0.1.0-SNAPSHOT.jar compile \
  --main <ExampleClassName> \
  --classpath juno-examples/target/classes:juno-api/target/classes

arduino-cli compile \
  --fqbn arduino:avr:uno \
  build/juno/<ExampleClassName>
```

Uploading changes the firmware on a connected physical board. Find the port with
`arduino-cli board list` before using `arduino-cli upload`.

## Juno compatibility

UNO R3 support is currently experimental rather than a declared Juno target. The existing backend
emits standard Arduino calls for the portable APIs, and both `Blink` and `SerialCounter` have been
compiled successfully with the `arduino:avr:uno` core.

| Juno API or feature | UNO R3 | Notes |
|---|---:|---|
| `Gpio` and `DigitalOutput` | Yes | Uses standard Arduino GPIO calls |
| `Delay` and `Clock` | Yes | Uses Arduino delay and time functions |
| `Serial` | Yes | Uses the ATmega16U2 USB-to-serial path |
| `Mouse` | No | The sketch MCU has no native USB HID support |
| `LedMatrix` | No | The R3 has no onboard 12x8 LED matrix |
| Wi-Fi and Bluetooth | Not applicable | No onboard wireless hardware |
| DAC, RTC and CAN | Not applicable | These peripherals are not built into the R3 board |

The most important practical limit is SRAM. A sketch passing the Arduino compiler's global-memory
report can still overflow at runtime because that report does not calculate the maximum Juno call
stack. R3 support should therefore include representative AVR compile tests and stack-budget
testing before it is considered production-ready.

## Official references

- [UNO R3 board page](https://docs.arduino.cc/hardware/uno-rev3/)
- [UNO R3 datasheet](https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf)
- [UNO R3 pinout](https://docs.arduino.cc/resources/pinouts/A000066-full-pinout.pdf)
- [ATmega328P product page](https://www.microchip.com/en-us/product/ATmega328P)
- [ArduinoCore-avr](https://github.com/arduino/ArduinoCore-avr)

