---
title: "Arduino Nano ESP32"
description: "Board notes for the Arduino Nano ESP32 and what Juno supports on it so far."
layout: page
---

The Nano ESP32 puts an Espressif ESP32-S3 (in a u-blox NORA-W106 module) on the Nano form factor.
Unlike the UNO boards, its CPU is an Xtensa LX7, so Juno generates Xtensa assembly for it with its
own backend instead of the Thumb-2 or AVR one.

<img src="../images/boards/arduino-nano-esp32.png" alt="Arduino Nano ESP32" width="280">

## Core specification

| Characteristic | Nano ESP32 |
|---|---:|
| Product SKU | ABX00083 |
| Module | u-blox NORA-W106-10B (ESP32-S3) |
| CPU architecture | Dual-core 32-bit Xtensa LX7 |
| CPU clock | 240 MHz |
| Program flash | 16 MB (external) |
| SRAM | 512 kB, plus 8 MB PSRAM |
| Logic and operating voltage | 3.3 V |
| USB connector | USB-C (native USB) |
| Built-in LED | D13 (ESP32-S3 GPIO48) |
| Arduino CLI FQBN | `arduino:esp32:nano_nora` |
| Juno board id | `arduino-nano-esp32` |

The Nano ESP32 includes 8 MB of PSRAM. It is separate from the Java arena and garbage collector; use the static
`Psram` API for caller-owned byte buffers when the fixed internal arena is too small.

## What Juno supports

| Feature | Status | Notes | Example |
|---|:---:|---|---|
| `Gpio`, `DigitalOutput` | ✓ | Resolve printed names such as `digitalPin("D13")` and `analogPin("A0")`; D13 is translated to ESP32-S3 GPIO48. Never pass raw ESP32 GPIO numbers; the onboard RGB LED is not reachable. | |
| `Delay`, `Serial` | ✓ | `Serial` over the native USB port. | |
| `int`, `long`, `float`, `double` | ✓ | Java semantics kept (a `long` division by zero stops the program, float-to-int casts saturate). `float` uses the S3's FPU; `double` is soft-float. | [`SerialWideMath`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialWideMath.java), [`SerialDoubleMath`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialDoubleMath.java) |
| Double-precision `java.lang.Math` | ✓ | Uses the toolchain's libm. | [`SerialDoubleMath`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialDoubleMath.java) |
| Objects, arrays, static and interface calls | ✓ | | |
| Lambdas and method references | ✓ | Every shape. | [`SerialLambdas`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialLambdas.java) |
| `BigInteger`, `BigDecimal`, `MathContext` | ✓ | Runs in the arena with the JDK's rounding and text rules. | [`SerialBigNumbers`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialBigNumbers.java) |
| Threads, `synchronized`, `ReentrantLock`, `StructuredTaskScope`, `ScopedValue` | ✓ | Each thread is a FreeRTOS task with an 8 KiB stack (`-Djuno.Xss`); one runs Java at a time, handing over after a 1 ms slice. On the AVR boards at most four threads run, including `main`. | [`SerialThreads`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialThreads.java) |
| `@Watchdog` | ✓ | ESP32-S3 task watchdog; the timeout is rounded up to whole seconds. | |
| `UUID.randomUUID()` | ✓ | Hardware RNG through `esp_fill_random`; may be weaker before Wi-Fi or Bluetooth start. | [`SerialUuids`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialUuids.java) |
| Wi-Fi station | ✓ | `begin`, `status`, `localIP`, `gatewayIP`, `macAddress`; credentials from the microSD card (see [SD Card](../sdcard)). | [`NanoWifiStatusSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/NanoWifiStatusSD.java) |
| Wi-Fi access point | ✓ | `beginAP(ssid, password)`. | [`NanoAccessPointSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/NanoAccessPointSD.java) |
| `Udp`, SNTP (`LocalDateTime.now()`) | ✓ | Through the core's `WiFiUDP`; optional `sntp.host`/`sntp.port` from the card. | [`NanoSntpTimeSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/udp/NanoSntpTimeSD.java) |
| `HttpClient` (HTTP) | ✓ | Host is a literal in the program; the path can come from the card. | [`NanoHttpGetSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/NanoHttpGetSD.java) |
| `HttpClient.requestHttps` | ✓ | Checks the chain against Mozilla's root certificates, bundled into the program (about 55 KB of flash); validity dates are not checked. | |
| `HttpServer` | ✓ | Runs on the station address. | [`NanoHttpServerSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/http/NanoHttpServerSD.java) |
| `Smtp.sendTls`, `Pop3Client` | ✓ | Implicit TLS on ports 465 and 995, checked against the bundled roots. Password, subject and body must fit in 31 bytes each. | [`NanoSmtpSendSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoSmtpSendSD.java), [`NanoPop3InboxSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoPop3InboxSD.java) |
| `Smtp.send` (STARTTLS) | ✓ | Needs `arduino-cli lib install ESP_SSLClient`. After the upgrade the server's chain, and its dates, are checked against the bundled Mozilla roots; the first send sets the board's clock over SNTP (`pool.ntp.org`). | [`NanoSmtpStarttlsCredentialsSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/email/NanoSmtpStarttlsCredentialsSD.java) |
| USB HID mouse | ✓ | `MCU.getMouse()` through TinyUSB; shares the USB port with `Serial`. | [`NanoRatonLoco`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/hid/NanoRatonLoco.java) |

## Features

These need only the Nano ESP32 and its USB cable.

### Blink the built-in LED

`getBuiltinLed()` is D13, the pin printed on the board (GPIO48 on the chip).

```java
Serial.begin(BaudRate.BAUD_115200);
DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());

while (true) {
    led.high();
    Serial.println("on");
    Delay.millis(500);
    led.low();
    Serial.println("off");
    Delay.millis(500);
}
```

[`SerialBlink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialBlink.java) also prints a cycle counter with each change.

### USB HID mouse

`MCU.getMouse()` makes the board a USB mouse for the computer it is plugged into; `Serial` keeps working on the
same USB port. Call `begin()` once, then `move(x, y)` moves the pointer by that many pixels:

```java
Mouse mouse = MCU.getMouse();
mouse.begin();

while (true) {
    mouse.move(50, 0);   // right
    Delay.millis(1000);
    mouse.move(0, 50);   // down
    Delay.millis(1000);
    mouse.move(-50, 0);  // left
    Delay.millis(1000);
    mouse.move(0, -50);  // up
    Delay.millis(1000);
}
```

Once uploaded, the program keeps moving the pointer; disconnect the board or upload another program to stop it.
[`NanoRatonLoco`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/hid/NanoRatonLoco.java) also prints each side over `Serial`.

## Shields

### Nano Connector Carrier

<img src="../images/shields/shield-nano-connector-carrier.png" alt="Arduino Nano Connector Carrier" width="280">

The Arduino Nano Connector Carrier (ASX00061) is passive: every connector is a Nano header pin, and
`NanoConnectorCarrier` names them so programs don't hard-code numbers:

| Connector | Nano pins | `NanoConnectorCarrier` |
|---|---|---|
| microSD slot (SPI) | CS D4 (D2/D3 by solder jumper), MOSI D11, MISO D12, SCK D13 | `getSdCard()`, `getSdChipSelect()`, `getSdChipSelectJumperD2()`/`D3()`, `getSpiMosi()`/`Miso()`/`Sck()` |
| Grove A0/A1 (analog) | A0 signal, A1 | `getGroveA0()`; wires `getA0()`, `getA1()` |
| Grove A2/A3 (analog) | A2 signal, A3 | `getGroveA2()`; wires `getA2()`, `getA3()` |
| Grove UART | RX D0 (signal), TX D1 | `getGroveUart()`; wires `getUartRx()`, `getUartTx()` |
| Grove I2C and Qwiic | SDA A4, SCL A5 | `getI2cSda()`, `getI2cScl()` (one bus, `MCU.getI2c()`) |

As with `GroveBaseShield`, plug a single-signal Grove module in and pass the socket's getter to its
driver: `pir.begin(carrier.getGroveA2())`. The analog sockets work for digital modules too. Keep
digital modules out of the I2C socket: its bus pull-ups held a Grove PIR's output at about 1.4 V at
rest, neither low nor high. The class Javadoc has a diagram of the connectors, and pin details are in
the [ASX00061 datasheet](https://docs.arduino.cc/resources/datasheets/ASX00061-datasheet.pdf).

The input selector switch sets the Grove connectors' VCC to 3.3 V or 5 V; use 3.3 V with the Nano
ESP32. The Qwiic connector is always 3.3 V.

The carrier seen from its connector side, USB cable at the bottom; `*` marks each Grove socket's yellow
signal wire (Grove pin 1):

```text
+---------------------------------------------+
|  Grove I2C                      Grove UART  |
|  [ GND  ]                       [ RX * ]    |
|  [ VCC  ]                       [ TX   ]    |
|  [ SDA  ] A4                    [ VCC  ]    |
|  [ SCL *] A5                    [ GND  ]    |
|                 +-------------+             |
|  Qwiic          |   microSD   |             |
|  [ I2C  ]       |   slot      |             |
|  A4/A5, 3.3 V   |   CS D4     |             |
|                 +-------------+             |
|  Grove A0/A1                    Grove A2/A3 |
|  [ GND  ]                       [ A2 * ]    |
|  [ VCC  ]                       [ A3   ]    |
|  [ A1   ]                       [ VCC  ]    |
|  [ A0 * ]                       [ GND  ]    |
+-------------------[ USB ]-------------------+
```

#### Features

##### Read the SD card

The Nano has no SD slot of its own, so `SdCard` needs the carrier: `getSdCard()` reaches its microSD
slot (chip select D4, SPI D11–D13) through the same SdFat runtime as the UNO boards. The examples
that read settings from the card (the `…SD` programs above) therefore need the carrier too.

```java
NanoConnectorCarrier carrier = new NanoConnectorCarrier();
SdCard sd = carrier.getSdCard();
if (sd.begin() && sd.exists("application.properties")) {
    InputStream in = sd.open("application.properties");
    // read the file
}
```

[`SdFileOperations`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/sd/SdFileOperations.java) exercises every `SdCard` file
operation end to end and reports each step over serial.

##### Connect to Wi-Fi with credentials from the SD card

Keep the network's credentials on the card rather than in the source: put them in `application.properties` at the
card's root,

```properties
wifi.ssid=your-network
wifi.password=your-password
```

then read the file with `java.util.Properties` and join the network:

```java
SdCard sd = new NanoConnectorCarrier().getSdCard();
Wifi wifi = MCU.getWifi();

if (sd.begin()) {
    try (InputStream file = sd.open("application.properties")) {
        if (file != null) {
            Properties properties = new Properties();
            properties.load(file);
            wifi.begin(properties.getProperty("wifi.ssid"), properties.getProperty("wifi.password"));
        }
    }
}
while (wifi.status() != Wifi.STATUS_CONNECTED) {
    Delay.millis(1000);
}
```

[`NanoWifiStatusSD`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/net/wifi/NanoWifiStatusSD.java) is the complete program: it joins the network and
prints its status once a second.

##### Read a Grove sensor

Plug a single-signal Grove module into an analog socket and pass the socket's getter to its driver.

```java
NanoConnectorCarrier carrier = new NanoConnectorCarrier();
GrovePirMotion pir = new GrovePirMotion();
LightDetector light = new GroveLightV12(carrier.getGroveA0(),
    GroveLightV12.ADC_12_BIT);
pir.begin(carrier.getGroveA2());

boolean motion = pir.motionDetected(carrier.getGroveA2());
float percent = light.getLightValue();
```

[`NanoCarrierGroveSensors`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/carrier/NanoCarrierGroveSensors.java) prints a
Grove PIR in the A2/A3 socket and a light sensor in the A0/A1 socket once a second.

##### Remote-control a LEGO RCX

With a Grove infrared emitter in the A0/A1 socket and a Grove infrared receiver on the I2C socket's
SCL line, the Nano talks to a LEGO Mindstorms RCX brick through `RemoteRcx` (see
[LEGO](../lego-ecosystem)). Download Juno's RCX firmware to the brick once with
[`NanoCarrierRcxFirmwareDownload`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxFirmwareDownload.java): keep the brick 10 to 50 cm away and still for about ten
minutes. The brick then answers on channel 1:

```java
NanoConnectorCarrier carrier = new NanoConnectorCarrier();
GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
RcxLink link = new RcxLink(emitter, receiver);
RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);

RcxMotor motor = rcx.getMotorA();
motor.setPower(57);
motor.forward();
```

One example per part of the brick: [`NanoCarrierRcxMotorA`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxMotorA.java), [`NanoCarrierRcxButtons`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxButtons.java),
[`NanoCarrierRcxTouchSensors`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxTouchSensors.java), [`NanoCarrierRcxLightSensor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxLightSensor.java),
[`NanoCarrierRcxRotation`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxRotation.java), [`NanoCarrierRcxTemperature`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxTemperature.java), [`NanoCarrierRcxLcd`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxLcd.java),
[`NanoCarrierRcxLamps`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxLamps.java), [`NanoCarrierRcxSound`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxSound.java) and [`NanoCarrierRcxBattery`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/NanoCarrierRcxBattery.java).

## Build and run

Install the core once with `arduino-cli core install arduino:esp32`. `Blink` builds for any board,
and [`SerialBlink`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/serial/SerialBlink.java)
targets the Nano ESP32 and reports every LED change at 115200 baud:

```bash
./mvnw -f juno-examples/pom.xml compile juno:verify \
  -Djuno.main=io.github.jabrena.juno.api.hardware.gpio.Blink \
  -Djuno.board=arduino-nano-esp32
```

The board can list two ports at once, its USB DFU port (for example `1-1`) and its serial port, so
the plugin cannot pick one alone. Upload through the DFU port and monitor the serial one:

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.hardware.serial.SerialBlink -Djuno.port=1-1
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 \
  -Djuno.port=/dev/cu.usbmodem<ID> -Djuno.baudRate=115200
```

The USB port re-enumerates when the program starts, so a program should wait a moment after
`Serial.begin` before its first line, as `SerialBlink` does.
