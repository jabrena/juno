---
title: "Migrating to MCU"
description: "Mapping every removed board facade (ArduinoUno, ArduinoNano, RaspberryPico2, Esp32Cam, M5StackCardputer) to the single MCU facade."
layout: page
---

Juno now has one facade for a board's own services: [`MCU`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/MCU.java).
The `@Board` annotation, or the build's `juno.board` option, selects the target, so the facade name no longer repeats it.
This is a **breaking change**: the five board facades are deleted, with no deprecated forwarders or aliases. Switch your
imports and calls as shown below.

Written for: application developers who compile Juno programs and need a mechanical migration.

## Mechanical rules

1. Replace `import io.github.jabrena.juno.hardware.<Facade>;` with `import io.github.jabrena.juno.hardware.MCU;`.
2. Replace `<Facade>.getX()` with `MCU.getX()`, using the table below. The getter names are the same except `getSd()`, which is now `getSdCard()`.
3. Where a constructor was given a facade getter that is the board's own singleton, you may drop that argument and use the convenience constructor (see [Constructors](#constructors)). Passing `MCU.getX()` explicitly keeps working.
4. Keep the `@Board` annotation. Nothing else about target selection changes.

## Getter mapping

| Removed call | Replacement |
| --- | --- |
| `ArduinoUno.getGpio()`, `ArduinoNano.getGpio()`, `RaspberryPico2.getGpio()`, `Esp32Cam.getGpio()`, `M5StackCardputer.getGpio()` | `MCU.getGpio()` |
| `ArduinoUno.getI2c()`, `ArduinoNano.getI2c()`, `RaspberryPico2.getI2c()`, `Esp32Cam.getI2c()`, `M5StackCardputer.getI2c()` | `MCU.getI2c()` |
| `ArduinoUno.getSpi()` | `MCU.getSpi()` |
| `ArduinoUno.getClock()`, `ArduinoNano.getClock()`, `RaspberryPico2.getClock()`, `Esp32Cam.getClock()`, `M5StackCardputer.getClock()` | `MCU.getClock()` |
| `ArduinoUno.getBoardModel()`, `ArduinoNano.getBoardModel()` | `MCU.getBoardModel()` |
| `ArduinoUno.getBoardMemory()`, `ArduinoNano.getBoardMemory()` | `MCU.getBoardMemory()` |
| `ArduinoUno.getWifi()`, `ArduinoNano.getWifi()`, `RaspberryPico2.getWifi()`, `Esp32Cam.getWifi()`, `M5StackCardputer.getWifi()` | `MCU.getWifi()` |
| `ArduinoUno.getBluetoothLe()`, `ArduinoNano.getBluetoothLe()`, `RaspberryPico2.getBluetoothLe()`, `Esp32Cam.getBluetoothLe()`, `M5StackCardputer.getBluetoothLe()` | `MCU.getBluetoothLe()` |
| `ArduinoUno.getMouse()`, `ArduinoNano.getMouse()`, `RaspberryPico2.getMouse()`, `M5StackCardputer.getMouse()` | `MCU.getMouse()` |
| `ArduinoUno.getLedMatrix()` | `MCU.getLedMatrix()` |
| `ArduinoUno.getAccelerometer()`, `ArduinoUno.getGyroscope()` | `MCU.getAccelerometer()`, `MCU.getGyroscope()` |
| `Esp32Cam.getCamera()` | `MCU.getCamera()` |
| `Esp32Cam.getSdCard()`, `M5StackCardputer.getSd()` | `MCU.getSdCard()` |
| `M5StackCardputer.getDisplay()` | `MCU.getDisplay()` |
| `M5StackCardputer.getKeyboard()` | `MCU.getKeyboard()` |
| `M5StackCardputer.getAudio()` | `MCU.getAudio()` |
| `M5StackCardputer.getBattery()` | `MCU.getBattery()` |

Shield- and carrier-owned sockets are unchanged. `DataLoggerShield.getSdCard()` and `NanoConnectorCarrier.getSdCard()` are
not onboard sockets, so they do not move to `MCU`.

## Capability-gated getters

`MCU` exposes every onboard service, but a getter that the target board does not provide is rejected at compile time,
and the diagnostic names the getter, the offending board and the boards that do support it:

- `MCU.getWifi()` on the UNO R3 (no radio), `MCU.getBluetoothLe()` on boards without a BLE radio, `MCU.getLedMatrix()` without an LED matrix.
- `MCU.getAccelerometer()` and `MCU.getGyroscope()` on anything but the UNO WiFi Rev2, `MCU.getCamera()` on anything but the AI Thinker ESP32-CAM.
- `MCU.getSdCard()` on boards without an onboard SD socket (only the M5Stack Cardputer and the AI Thinker ESP32-CAM).
- `MCU.getMouse()` on boards without a USB HID stack (the UNO Q and the UNO WiFi Rev2 have none).

A multi-board `@Board` declaration must support every reachable getter on **each** declared board, even when one board is
selected for the build. Portable getters (`getGpio`, `getI2c`, `getSpi`, `getClock`, `getBoardModel`, `getBoardMemory`)
work everywhere.

## Constructors

Where the only non-default argument was a board's own singleton, use the convenience constructor. The explicit forms stay
for mocks, alternate buses or clocks, and non-default topology.

| Before | After |
| --- | --- |
| `new GroveBaseShield(MCU.getGpio())` | `new GroveBaseShield()` |
| `new GrovePicoShield(MCU.getGpio())` | `new GrovePicoShield()` |
| `new NanoConnectorCarrier(MCU.getGpio())` | `new NanoConnectorCarrier()` |
| `new GroveTemperatureV12(MCU.getGpio())` | `new GroveTemperatureV12()` |
| `new GrovePirMotion(MCU.getGpio())`, `new GroveAnalogGas(MCU.getGpio())`, `new GroveSoundV16(MCU.getGpio())` | the same class with `()` |
| `new GroveLightV12(MCU.getGpio(), pin, adc)` | `new GroveLightV12(pin, adc)` |
| `new GroveGpsAir530(MCU.getGpio(), MCU.getClock(), pin)` | `new GroveGpsAir530(pin)` |
| `new Bmi088(MCU.getI2c(), MCU.getClock())` | `new Bmi088()` |
| `new PwmServoShield(MCU.getI2c(), address)` | `new PwmServoShield(address)` |
| `new RcxLink(emitter, receiver, MCU.getClock())` | `new RcxLink(emitter, receiver)` |
| `new RcxRemoteControl(receiver, MCU.getClock())` | `new RcxRemoteControl(receiver)` |
| `new RemotePoweredUpHub(MCU.getBluetoothLe(), MCU.getClock())` | `new RemotePoweredUpHub()` |

Pins, addresses, buffers, shield or carrier buses, and explicitly supplied collaborators remain constructor arguments.

## Diagnostics

Compile-time messages name the service through `MCU`, for example `MCU.getDisplay requires @Board(M5StackCardputerBoard.class)`.
The Esp32-CAM camera and I2C conflict now reads `MCU.getCamera() and MCU.getI2c() cannot be used in one program`.
