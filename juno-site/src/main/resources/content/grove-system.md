---
title: "Grove system"
description: "Connecting Grove sensors through the Grove Base Shield or the Nano Connector Carrier, and reading them from Java."
layout: page
---

Seeed Studio's [Grove](https://wiki.seeedstudio.com/Grove_System/) system connects sensors with one four-wire cable:
`GND`, `VCC`, and two signal wires, the first of which (yellow) carries the module's main signal. Juno reaches a Grove
module in one of two ways, depending on the board, and its drivers take the socket's pin, so the same driver code works
with either.

## Two ways to connect

### Grove Base Shield, on the UNO boards

<img src="../images/shields/shield-grove.png" alt="Grove Base Shield" width="240">

The [Grove Base Shield](https://wiki.seeedstudio.com/Base_Shield_V2/) is a passive adapter for the UNO headers, used
on the UNO R3, UNO WiFi Rev2, UNO R4 WiFi and UNO Q. Its A0-A3, D2-D8, UART and I2C sockets are ordinary UNO pins;
[`GroveBaseShield`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/GroveBaseShield.java)
names them. Seat it on the board's UNO headers and plug the module into a socket; the diagram is a socket map, not the
shield's physical layout:

```text
+-------------------------------------------------------------+
|                      Grove Base Shield                      |
|                                                             |
|  Digital   [D2] [D3] [D4] [D5] [D6] [D7] [D8]   getD2()...  |
|  Analog    [A0] [A1] [A2] [A3]                  getA0()...  |
|  I2C       [I2C] [I2C] [I2C] [I2C]  SDA A4, SCL A5          |
|  UART      [UART]                   RX D0, TX D1            |
+------------------------------+------------------------------+
                               | seated on the UNO headers
+------------------------------v------------------------------+
|      UNO R3  /  UNO WiFi Rev2  /  UNO R4 WiFi  /  UNO Q     |
+-------------------------------------------------------------+

 Grove DHT22 ---- cable ----> [D4]
 Java:  dht.read(shield.getD4(), reading, reading.length, frame, frame.length)
```

### Nano Connector Carrier, on the Nano ESP32

<img src="../images/shields/shield-nano-connector-carrier.png" alt="Arduino Nano Connector Carrier" width="240">

The Arduino Nano Connector Carrier holds the Nano ESP32 and adds four Grove sockets (two analog, UART and I2C) plus a
Qwiic connector; `NanoConnectorCarrier` names them. Set its input selector switch to 3.3 V for the Nano ESP32. See the
[Nano ESP32](../arduino-nano-esp32#nano-connector-carrier) page for its full pin map.

The carrier seen from its connector side, USB cable at the bottom; `*` marks each Grove socket's yellow signal wire:

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

 Grove light sensor ---- cable ----> [Grove A0/A1]
 Java:  light.rawValue(carrier.getGroveA0())
```

Keep digital modules out of the carrier's I2C socket: its bus pull-ups hold a digital output between low and high.

## Grove Sensors

Every pin-based driver below allocates nothing while it runs and takes the socket's pin as an argument, so it works the
same on the Grove Base Shield (`shield.getD4()`, `shield.getA0()`, ...) and on the Nano Connector Carrier
(`carrier.getGroveA0()`, `carrier.getGroveA2()`). The BMI088 uses the socket's I2C bus and the board's `Clock` instead.
Create each driver once, at startup.

The complete examples below use a Grove Base Shield on an UNO board. To use a Nano Connector Carrier, replace the
shield socket getter with the corresponding carrier getter and use the Nano board's `Gpio` and `Clock` facades.

Create each adapter with the board's `Gpio`, so its socket names are resolved for the selected board:
`new GroveBaseShield(ArduinoUno.getGpio())` on an UNO board and
`new NanoConnectorCarrier(ArduinoNano.getGpio())` on a Nano ESP32.

| Sensor | Socket | Driver | Reads |
|---|---|---|---|
| [Temperature and Humidity Sensor Pro (DHT22)](#temperature-and-humidity-sensor-pro-dht22) | Digital | [`GroveDht22`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveDht22.java) | Temperature and humidity, in tenths |
| [Temperature and Humidity Sensor (DHT11)](#temperature-and-humidity-sensor-dht11) | Digital | [`GroveDht11`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveDht11.java) | Whole-degree temperature and humidity |
| [Temperature Sensor v1.2](#temperature-sensor-v12) | Analog | [`GroveTemperatureV12`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveTemperatureV12.java) | Degrees Celsius from a thermistor |
| [Light Sensor v1.2](#light-sensor-v12) | Analog | [`GroveLightV12`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveLightV12.java) | Light level as a percentage (`LightDetector`) |
| [Sound Sensor v1.6](#sound-sensor-v16) | Analog | [`GroveSoundV16`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveSoundV16.java) | Raw microphone level |
| [Gas sensor](#gas-sensor) | Analog | [`GroveAnalogGas`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveAnalogGas.java) | Raw gas reading |
| [PIR Motion Sensor](#pir-motion-sensor) | Digital | [`GrovePirMotion`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GrovePirMotion.java) | Motion detected or not |
| [BMI088 accelerometer and gyroscope](#bmi088-accelerometer-and-gyroscope) | I2C | [`Bmi088`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/imu/Bmi088.java) | Acceleration, angular velocity and integrated heading |
| [Ultrasonic Ranger](#ultrasonic-ranger) | Digital | [`GroveUltrasonicRanger`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveUltrasonicRanger.java) | Distance in metres |
| [Infrared emitter and receiver](#infrared-emitter-and-receiver) | Digital | [`GroveInfraredEmitter`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveInfraredEmitter.java), [`GroveInfraredReceiver`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/hardware/device/grove/sensors/GroveInfraredReceiver.java) | Infrared link to a LEGO RCX |

### Temperature and Humidity Sensor Pro (DHT22)

Temperature and humidity in tenths of a degree and of a percent. The caller owns the reading and frame buffers:

<img src="../images/grove/grove-dht22.jpg" alt="Grove Temperature and Humidity Sensor Pro (DHT22)" width="240">

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht22;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveDht22Example {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveDht22 dht = new GroveDht22();
        int[] reading = new int[GroveDht22.READING_LENGTH];
        int[] frame = new int[GroveDht22.FRAME_LENGTH];
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            if (dht.read(shield.getD4(), reading, reading.length, frame, frame.length)) {
                Serial.print("temperature tenths C=");
                Serial.print(reading[GroveDht22.TEMPERATURE_TENTHS_CELSIUS]);
                Serial.print(" humidity tenths %= ");
                Serial.println(reading[GroveDht22.HUMIDITY_TENTHS_PERCENT]);
            } else {
                Serial.println("DHT22 read failed");
            }
            Delay.millis(2000);
        }
    }
}
```

[`GroveDht22Monitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/grove/sensors/GroveDht22Monitor.java)
is the maintained version with formatted decimal output.

### Temperature and Humidity Sensor (DHT11)

The DHT11 reports whole numbers; it reads the same way as the DHT22:

<img src="../images/grove/grove-dht11.jpg" alt="Grove Temperature and Humidity Sensor (DHT11)" width="240">

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht11;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveDht11Example {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveDht11 dht = new GroveDht11();
        int[] reading = new int[GroveDht11.READING_LENGTH];
        int[] frame = new int[GroveDht11.FRAME_LENGTH];
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            if (dht.read(shield.getD3(), reading, reading.length, frame, frame.length)) {
                Serial.print("temperature C=");
                Serial.print(reading[GroveDht11.TEMPERATURE_CELSIUS]);
                Serial.print(" humidity %= ");
                Serial.println(reading[GroveDht11.HUMIDITY_PERCENT]);
            } else {
                Serial.println("DHT11 read failed");
            }
            Delay.millis(2000);
        }
    }
}
```

### Temperature Sensor v1.2

A 100-kilohm thermistor divider; the driver converts the ADC count to degrees Celsius with Seeed's B constant:

<img src="../images/grove/grove-temperature-v12.jpg" alt="Grove Temperature Sensor v1.2" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveTemperatureV12;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveTemperatureExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveTemperatureV12 temperature = new GroveTemperatureV12(ArduinoUno.getGpio());
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("temperature C=");
            Serial.println(temperature.celsius(shield.getA0()));
            Delay.millis(1000);
        }
    }
}
```

### Light Sensor v1.2

A `LightDetector` bound to its socket. The light value is the percentage of the ADC full scale: relative
brightness, not illuminance. Pass `GroveLightV12.ADC_12_BIT` as the ADC full-scale argument on the Nano ESP32:

<img src="../images/grove/grove-light-v12.jpg" alt="Grove Light Sensor v1.2" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveLightV12;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.robotics.LightDetector;
import io.github.jabrena.juno.util.Delay;

public final class GroveLightExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        LightDetector light = new GroveLightV12(ArduinoUno.getGpio(), shield.getA1(),
                GroveLightV12.ADC_10_BIT);
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("relative light %= ");
            Serial.println(light.getLightValue());
            Delay.millis(500);
        }
    }
}
```

### Sound Sensor v1.6

An ADC sample of the amplified microphone signal, not a decibel measurement:

<img src="../images/grove/grove-sound-v16.jpg" alt="Grove Sound Sensor v1.6" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveSoundV16;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveSoundExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveSoundV16 sound = new GroveSoundV16(ArduinoUno.getGpio());
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("microphone ADC=");
            Serial.println(sound.rawValue(shield.getA2()));
            Delay.millis(100);
        }
    }
}
```

### Gas sensor

The analog output of a Grove gas sensor. Useful for watching changes; a gas concentration needs the particular
sensor's warm-up and calibration procedure:

<img src="../images/grove/grove-gas-mq2.jpg" alt="Grove Gas Sensor (MQ2)" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveAnalogGas;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveGasExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveAnalogGas gas = new GroveAnalogGas(ArduinoUno.getGpio());
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("gas ADC=");
            Serial.println(gas.rawValue(shield.getA3()));
            Delay.millis(500);
        }
    }
}
```

### PIR Motion Sensor

A digital output that goes high on motion. Configure the socket once, then poll it. On the Nano Connector Carrier, use
an analog socket rather than the I2C one:

<img src="../images/grove/grove-pir-motion.jpg" alt="Grove PIR Motion Sensor" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GrovePirMotion;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GrovePirExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GrovePirMotion pir = new GrovePirMotion(ArduinoUno.getGpio());
        pir.begin(shield.getD2());
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("motion=");
            Serial.println(pir.motionDetected(shield.getD2()));
            Delay.millis(100);
        }
    }
}
```

### BMI088 accelerometer and gyroscope

The Grove 6-Axis Accelerometer & Gyroscope uses two I2C addresses on the same bus. `Bmi088` implements the
`Accelerometer`, `Gyroscope` and `SampleProvider` contracts; it is configured for ±3 g and ±2000 degrees per second.
Heading is integrated from the Z-axis gyro and drifts over time. Keep the module still while
`recalibrateOffset()` runs.

<img src="../images/grove/grove-bmi088.jpg" alt="Grove 6-Axis Accelerometer and Gyroscope (BMI088)" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.imu.Bmi088;

Bmi088 imu = new Bmi088(ArduinoUno.getI2c(), ArduinoUno.getClock());
float[] acceleration = new float[imu.sampleSize()];
imu.recalibrateOffset();
imu.fetchSample(acceleration, 0);
int heading = imu.getAngle();
```

### Ultrasonic Ranger

The Grove Ultrasonic Ranger measures the time for an echo and returns metres, or `Float.NaN` when no echo arrives:

<img src="../images/grove/grove-ultrasonic-ranger.png" alt="Grove Ultrasonic Ranger" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveUltrasonicRanger;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class GroveUltrasonicExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveUltrasonicRanger ranger = new GroveUltrasonicRanger(
                ArduinoUno.getGpio(), ArduinoUno.getClock(), shield.getD5());
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            Serial.print("distance metres=");
            Serial.println(ranger.getRange());
            Delay.millis(100);
        }
    }
}
```

### Infrared emitter and receiver

A Grove infrared emitter and receiver form the infrared link to a LEGO Mindstorms RCX brick. Wrap each socket's pin,
then construct an `RcxLink` from both modules and pass it to `RemoteRcx`:

<img src="../images/grove/grove-ir-emitter.jpg" alt="Grove Infrared Emitter" width="240">
<img src="../images/grove/grove-ir-receiver.jpg" alt="Grove Infrared Receiver" width="240">

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class GroveRcxExample {
    public static void main(String[] args) {
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(shield.getD4());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(shield.getD3());
        RcxLink link = new RcxLink(emitter, receiver, ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.begin(BaudRate.BAUD_115200);

        while (true) {
            int millivolts = rcx.getBattery().millivolts();
            Serial.print("RCX battery mV=");
            Serial.println(millivolts);
            Delay.millis(1000);
        }
    }
}
```

See [LEGO](../lego-ecosystem) for driving the brick, and the
[Nano ESP32](../arduino-nano-esp32#remote-control-a-lego-rcx) page for a complete example.
