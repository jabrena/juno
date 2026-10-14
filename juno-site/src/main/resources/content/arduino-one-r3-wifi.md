---
title: "Arduino UNO R3 WiFi"
description: "Board notes for the Arduino UNO R3 WiFi."
layout: page
---

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
java -jar juno-compiler/target/juno-compiler-0.1.0-SNAPSHOT.jar compile \
  --main <ExampleClassName> \
  --classpath juno-examples/target/classes:juno-api/target/classes

arduino-cli compile \
  --fqbn arduino:megaavr:uno2018 \
  build/juno/<ExampleClassName>
```

Uploading changes the firmware on a connected physical board. Find its port with
`arduino-cli board list` before using `arduino-cli upload`.

## Juno compatibility

The UNO WiFi Rev2 is a Juno target: select it with `@Board(ArduinoUnoWiFiRev2.class)` (board id
`arduino-uno-wifi-rev2`). Juno compiles for it with a dedicated **AVR backend**, separate from the
Thumb-2 one the UNO R4 WiFi and UNO Q share, and links against the `arduino:megaavr` core and the
`WiFiNINA` library.

### Java types on an 8-bit core

Java's primitive types keep their JVM widths and semantics, even though avr-gcc's own `int` is 16
bits and its `double` is 32 bits:

- `int` is four bytes in four registers; add, subtract, logic and constant shifts are inline, and
  multiply, divide, remainder and variable shifts follow Java's wrap-around and masking rules.
- `long` is eight bytes, computed in the runtime shim.
- `float` and `double` are IEEE 754 binary32/binary64 computed by Juno's own software
  floating-point (round-to-nearest-even, subnormals, NaN, signed zero, saturating casts), so results
  are bit-identical to a JVM's. Printing a `double` with `Serial` uses Arduino's two-decimal format,
  as on the other boards.
- References are 16-bit addresses kept in 32-bit words, so objects and arrays have the same layout
  as on the 32-bit boards.

`Math` supports `abs`, `min`, `max`, `clamp`, `floorDiv`, `floorMod`, `signum`, `round`, `floor`,
`ceil`, `sqrt`, `toRadians` and `toDegrees`. The transcendental functions (`sin`, `cos`, `exp`,
`log`, `pow`, ...) are rejected at compile time, because the AVR C library only has single-precision
versions, which would silently change results.

### `BigInteger` and `BigDecimal`

`java.math` uses the same runtime as the 32-bit boards, with exact JDK results, and
`BigInteger.doubleValue`/`BigDecimal.doubleValue` correctly rounded on the software `double`.
The board's memory sets the practical limits:

| Resource | Budget | What it means |
|---|---|---|
| Arena (heap) | 1 KB by default, at most 3 KB (`-Djuno.Xmx=3k`) | Counters past `long`, money-style decimals and conversions fit in the default; high-precision `sqrt`, long divisions or values near `double`'s limits need the 3 KB maximum, and anything larger panics with `[juno-big] OOM` |
| Task stacks | 512 bytes for each of up to three forked tasks, at most 1 KB (`-Djuno.Xss=1k`) | 1.5 KB of RAM once a program forks; the last 128 bytes of each stack are kept for the C calls of its deepest frame, and a task that would recurse past them stops with `[juno-thread] stack overflow` |
| Flash | 48 KB | The arithmetic runtime alone takes about 20 KB, so a sketch combining `java.math` with Wi-Fi may not fit; `arduino-cli` then reports the sketch as too big |

Operations that run out of memory panic loudly; they never return a wrong value.

### API support

| Juno API or feature | UNO WiFi Rev2 | Notes |
|---|---:|---|
| `Gpio`, `DigitalOutput`, `Delay`, `Clock`, `Random` | Yes | `Gpio.builtinLed()` is logical pin 25 |
| `Serial` | Yes | |
| `Wifi`, `Udp` | Yes | Through WiFiNINA |
| `HttpClient` with `http://` URLs, `Json` | Yes | |
| `HttpClient` with `https://` URLs | Yes | TLS runs on the NINA-W102 module, which trusts the root certificates in its firmware; add others with the Arduino IDE's certificate uploader |
| `Smtp.sendTls`, `Pop3Client` | Yes | Implicit TLS (usually ports 465 and 995) on the NINA-W102 module, as `WiFiSSLClient` does on the UNO R4 WiFi |
| `Smtp.send` (STARTTLS, usually port 587) | No | Upgrading a plain connection needs a TLS stack on the board itself (the UNO R4 WiFi runs ESP_SSLClient), far beyond 6 KB of RAM; use `Smtp.sendTls` on port 465 |
| `HttpServer` | No | Rejected at compile time |
| `SdCard`, `Properties` | Yes | At most 2 open files; `Properties` of 8 entries, 15-character keys, 31-character values |
| `RandomAccessFile` | Untested | Generated, but not yet run in the simulator |
| `LcdKeypadShield`, `TftTouchShield` | Yes | |
| `Servo`, `I2c`, `Infrared` | Untested | Generated, but not yet run in the simulator or on hardware |
| `BigInteger`, `BigDecimal`, `MathContext` | Yes | See the limits above |
| `UUID`, `UuidCodec` | Yes | Construction, bit access, version/variant, equality/ordering, canonical text and 16-byte network conversion; `randomUUID()` uses the onboard ATECC608 secure-element RNG |
| `PortNoise` | Yes | Explicitly non-cryptographic startup sampling from a user-provided analog noise circuit and digital comparator; it is not a fallback for `randomUUID()` |
| `StructuredTaskScope`, `ScopedValue`, `synchronized`, `ReentrantLock`, atomics | Yes | The same scheduler as the other boards: virtual threads preempted at loop back-edges and method entries (AVR safepoints), subtasks cooperative at loop back-edges, at most four threads including `main`; see the task stacks above |
| `Mouse` | No | The application MCU has no native USB HID support |
| `@Watchdog` | Yes | ATmega4809 hardware watchdog; requested timeouts round up to a supported period, at most approximately 8.2 seconds |
| `LedMatrix` | No | |

### How it is tested

The AVR backend is checked by running programs, not only by compiling them: `SimavrRunIT` builds
each program with avr-gcc against a stand-in megaavr core and runs it under the simavr simulator,
then compares its serial output with OpenJDK's. It runs on a local `gcc-avr`/`libsimavr-dev`
install or in Docker:

```bash
./mvnw -pl juno-compiler -am -Psimavr verify
```

The suite covers the language conformance programs (including every primitive type's edge cases,
`java.math` and structured concurrency, scoped values, locks and atomics), plus a peripheral program that exercises HTTP, HTTPS, SMTP over TLS, POP3, JSON, UDP, the SD card,
`Properties` and the LCD and TFT shields against simulated hardware. Generated sketches have not
yet been run on a physical UNO WiFi Rev2.

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
