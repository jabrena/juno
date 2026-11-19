---
title: "Grove system"
description: "Connecting Grove sensors through the Grove Base Shield, Grove Shield for Pi Pico or Nano Connector Carrier, and reading them from Java."
layout: page
---

Seeed Studio's [Grove](https://wiki.seeedstudio.com/Grove_System/) system connects sensors with one four-wire cable: `GND`, `VCC`, and two signal wires, the first of which (yellow) carries the module's main signal. Juno reaches a Grove module through the shield or carrier for your board. Pick one below to see its sockets, its API and an example.

## Connect a Grove module

| Shield or carrier | UNO R3 | UNO R4 WiFi | Nano ESP32 | Portenta H7 | Pico 2 W | Grove sockets |
| --- | :---: | :---: | :---: | :---: | :---: | --- |
| [Grove Base Shield](grove-base-shield) | ✓ | ✓ |  |  |  | Digital D2 to D8, analog A0 to A3, I2C, UART |
| [Grove Shield for Pi Pico](grove-shield-for-pi-pico) |  |  |  |  | ✓ | Digital D16, D18, D20; analog A0 to A2; I2C0, I2C1, UART0, UART1, SPI0 |
| [Nano Connector Carrier](nano-connector-carrier) |  |  | ✓ |  |  | Analog A0/A1 and A2/A3, UART, I2C and Qwiic |

## Grove Sensors

Each sensor has its own page, with its wiring and examples.

| Sensor | Socket | Driver | Reads |
|---|---|---|---|
| [GPS (Air530)](gps-air530) | Digital input / UART TX | [`GroveGpsAir530`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveGpsAir530.java) | NMEA GGA latitude, longitude, altitude, satellites and fix quality |
| [Temperature and Humidity Sensor Pro (DHT22)](temperature-and-humidity-sensor-pro-dht22) | Digital | [`GroveDht22`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveDht22.java) | Temperature and humidity, in tenths |
| [Temperature and Humidity Sensor (DHT11)](temperature-and-humidity-sensor-dht11) | Digital | [`GroveDht11`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveDht11.java) | Whole-degree temperature and humidity |
| [Temperature Sensor v1.2](temperature-sensor-v12) | Analog | [`GroveTemperatureV12`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveTemperatureV12.java) | Degrees Celsius from a thermistor |
| [Light Sensor v1.2](light-sensor-v12) | Analog | [`GroveLightV12`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveLightV12.java) | Light level as a percentage (`LightDetector`) |
| [Sound Sensor v1.6](sound-sensor-v16) | Analog | [`GroveSoundV16`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveSoundV16.java) | Raw microphone level |
| [Gas sensor](gas-sensor) | Analog | [`GroveAnalogGas`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveAnalogGas.java) | Raw gas reading |
| [PIR Motion Sensor](pir-motion-sensor) | Digital | [`GrovePirMotion`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GrovePirMotion.java) | Motion detected or not |
| [BMI088 accelerometer and gyroscope](bmi088-accelerometer-and-gyroscope) | I2C | [`Bmi088`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/imu/Bmi088.java) | Acceleration, angular velocity and integrated heading |
| [Ultrasonic Ranger](ultrasonic-ranger) | Digital | [`GroveUltrasonicRanger`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveUltrasonicRanger.java) | Distance in metres |
| [Infrared emitter and receiver](infrared-emitter-and-receiver) | Digital | [`GroveInfraredEmitter`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveInfraredEmitter.java), [`GroveInfraredReceiver`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveInfraredReceiver.java) | Infrared link to a LEGO RCX |

## Displays and actuators

| Device | Socket | Driver |
|---|---|---|
| [OLED 0.96-inch (SSD1315)](oled-ssd1315) | I2C | [`GroveOledSsd1315`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/display/GroveOledSsd1315.java) |
| [I2C Motor Driver (TB6612FNG)](i2c-motor-driver-tb6612fng) | I2C | [`GroveMotorDriverTb6612fng`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/motor/GroveMotorDriverTb6612fng.java) |
