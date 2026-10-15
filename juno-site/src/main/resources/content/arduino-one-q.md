---
title: "Arduino UNO Q"
description: "Board notes for the Arduino UNO Q (STM32U585, Zephyr core)."
layout: page
---

> The official product name is **Arduino UNO Q**. This file keeps `ONE` in its filename to match
> the repository's existing documentation naming convention.

The UNO Q combines two processors on one UNO-form-factor board. A Qualcomm Dragonwing QRB2210
runs Debian Linux for application-level and multimedia workloads, while an STM32U585 runs Arduino
sketches under Zephyr for deterministic real-time I/O. Juno targets the STM32 microcontroller; it
does not run generated Java programs on the Linux processor.

## Core specification

| Characteristic | Linux side | Real-time side used by Juno |
|---|---|---|
| Processor | Qualcomm Dragonwing QRB2210 | STMicroelectronics STM32U585AIGT6 |
| CPU architecture | Quad-core Arm Cortex-A53 | 32-bit Arm Cortex-M33 with floating-point unit |
| Maximum CPU clock | 2.0 GHz | 160 MHz |
| Operating system | Debian Linux | Zephyr RTOS |
| Memory | 2 GB or 4 GB LPDDR4 | 786 kB SRAM |
| Non-volatile storage | 16 GB or 32 GB eMMC | 2 MB flash |
| Graphics | Adreno GPU | Not applicable |
| Wireless | Dual-band Wi-Fi 5 and Bluetooth 5.1 | Reached through the Linux side, not direct MCU radios |
| Arduino CLI FQBN | Not applicable | `arduino:zephyr:unoq` |

The two processors communicate through Arduino's Bridge/RPC facilities. That architecture lets a
Linux application handle networking, storage, vision or a user interface while an Arduino sketch
handles timing-sensitive GPIO. Juno currently compiles only the MCU sketch portion of that model.

## Power and electrical characteristics

The traditional UNO-style headers expose the STM32U585's **3.3 V** I/O. Most MCU digital pins are
5 V tolerant when configured as digital inputs, but outputs still drive 3.3 V. The analog inputs
must remain within the selected analog-reference range; in particular, A0 and A1 are not 5 V
tolerant. A4 and A5 used as I2C require pull-ups to 3.3 V.

The separate JCTL and some bottom-connector signals belong to the QRB2210's **1.8 V** domain. Do
not connect them to 3.3 V or 5 V logic. Check the official pinout before attaching a shield,
carrier, debugger or USB-to-UART adapter because the board contains all three voltage domains.

The board can be powered through USB-C or VIN. The official pinout marks VIN for 7-24 V DC input.
Use an external driver and suitable supply for motors, relays and other high-current loads rather
than powering them directly from a GPIO pin.

## MCU I/O and buses

- **Digital:** D0-D21 on the UNO-style headers; D0/D1 provide the user UART and D20/D21 provide
  the primary I2C bus.
- **Analog:** A0-A5 support ADC input. A0 and A1 also provide DAC outputs.
- **SPI:** D10-D13 provide the traditional UNO SPI mapping; the six-pin SPI header exposes a
  second mapping. Consult the pinout before trying to use both interfaces simultaneously.
- **I2C:** SDA/SCL are exposed as D20/D21. The Qwiic connector provides another 3.3 V I2C bus.
- **CAN:** D4 and D5 expose the STM32 FDCAN transmit and receive signals. An external transceiver
  is required for a physical CAN bus.
- **USB-C:** supplies power and connects the Linux system to USB peripherals and displays through
  a suitable powered adapter.
- **Expansion:** bottom connectors expose camera, display, audio and additional mixed MPU/MCU
  signals for compatible carriers.

The classic UNO outline does not imply 5 V electrical compatibility. Confirm the voltage,
current and library requirements of every shield before connecting it to the UNO Q.

## Arduino toolchain

Install Arduino CLI and the Zephyr core:

```bash
arduino-cli core update-index
arduino-cli core install arduino:zephyr
arduino-cli core list
```

Install the `Arduino_RouterBridge` library. It is required for every UNO Q sketch, not just ones
using `Wifi`/`Udp`: without it, the zephyr core's `Arduino.h` fails to compile with `#error
"Please install the Arduino_RouterBridge library from the Library Manager for proper Serial
support on this board."`

```bash
arduino-cli lib install Arduino_RouterBridge
```

Programs using [`PoweredUpHubRemote`](../lego) also need `ArduinoBLE` 2.1.0 or newer, which reaches the
Linux side's Bluetooth adapter through the same bridge (and `arduino-router` 0.7.0 or newer on the
board):

```bash
arduino-cli lib install ArduinoBLE
```

Connect the board and confirm that its FQBN is detected:

```bash
arduino-cli board list
```

The expected FQBN is `arduino:zephyr:unoq`. The first upload may also install or update the
Zephyr loader. Current ArduinoCore-zephyr releases automate this initial setup; if recovery is
needed, follow the core's loader instructions rather than substituting an UNO R4 bootloader
procedure.

## Juno compatibility

Annotate the Java entry point to select the UNO Q:

```java
import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.Board;

@Board(ArduinoUnoQ.class)
public final class Example {
    public static void main(String[] args) {
        // Program executed by the STM32U585.
    }
}
```

Juno lowers the reachable Java bytecode to GNU Arm Thumb-2 assembly and emits a small C++ runtime
shim. Arduino's Zephyr core assembles, compiles and packages those files as a sketch dynamically
loaded by the board's Zephyr firmware.

| Juno API or feature | UNO Q | Notes |
|---|---:|---|
| `Gpio` and `DigitalOutput` | Yes | Uses the STM32's Arduino GPIO mapping |
| `Delay`, `Clock` and `Random` | Yes | Uses Arduino/Zephyr timing support |
| `UUID.randomUUID()` | Yes | Uses Zephyr's cryptographic random service on the local STM32U585 TRNG; it does not depend on Linux, Wi-Fi traffic or the Bridge |
| `Serial` | Yes | Uses the MCU sketch's serial path |
| LCD keypad and TFT touch shields | Yes | Built on GPIO, timing and analog input |
| TFT games | Yes | Battleship, Blackjack, Chess, Tempest and Texas Hold'em support both UNO Q and UNO R4 WiFi; Empire Strikes Back, Missile Command, Pac-Man, Red Baron, Star Trek, Star Wars and DOOM target UNO Q only; Lunar Lander targets UNO R4 WiFi only |
| Onboard LED matrix | Yes | 13x8 through the Zephyr core's `Arduino_LED_Matrix`; `LedCanvas.width()` is 13 here and 12 on the UNO R4 WiFi, so frames and fonts adapt. Compiles with `arduino-cli`; not yet confirmed on hardware |
| `Wifi` and `Udp` | Yes | Linux owns Wi-Fi; the MCU exchanges datagrams through `Arduino_RouterBridge` and `BridgeUDP` |
| Juno HTTPS client API | Yes | TLS connections use `BridgeTCPClient` and the Linux system trust store |
| Juno HTTP server and email APIs | No | Current intrinsics target the UNO R4 WiFi's `WiFiS3` libraries |
| `@Watchdog` | No | The STM32U585 has watchdog hardware, but the current Arduino Zephyr firmware builds with `CONFIG_WATCHDOG` disabled, so sketches cannot safely use it |
| USB mouse, SD card, servo and email APIs | Untested | Do not assume UNO R4 library compatibility |
| Linux applications | Not yet | Juno emits the MCU sketch; networking uses the board's existing `arduino-router` service |

The Arduino linker report is authoritative for a generated sketch's usable memory. Its reported
limits describe the Zephyr user-sketch partition and runtime budget, which are smaller than the
STM32U585's complete physical flash and SRAM.

## Official references

- [UNO Q board page](https://docs.arduino.cc/hardware/uno-q/)
- [UNO Q user manual](https://docs.arduino.cc/tutorials/uno-q/user-manual/)
- [UNO Q datasheet](https://docs.arduino.cc/resources/datasheets/ABX00162-datasheet.pdf)
- [UNO Q pinout](https://docs.arduino.cc/resources/pinouts/ABX00162-full-pinout.pdf)
- [ArduinoCore-zephyr](https://github.com/arduino/ArduinoCore-zephyr)
