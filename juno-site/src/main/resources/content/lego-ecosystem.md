---
title: "LEGO ecosystem"
description: "Remote-controlling LEGO Powered Up hubs and Mindstorms RCX bricks from Java."
layout: page
---

LEGO has made several generations of motorised, remote-controlled systems. Juno drives three of them today from the
same kind of Java program, and two more are pending. Each is a small remote-control class: the board plays the part of LEGO's remote, app or
infrared tower, and the LEGO hardware does the rest.

| System | Since | What it is | Protocol used | Board with support | Juno class |
|---|---|---|---|---|---|
| LEGO Powered Up | 2017 | Hubs (City, Technic, BOOST, DUPLO Train) with motors and sensors, driven by LEGO's apps | Bluetooth LE | UNO R4 WiFi, UNO Q, AI Thinker ESP32-CAM, Nano ESP32, M5Stack Cardputer | [`RemotePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/poweredup/RemotePoweredUpHub.java) |
| LEGO Mindstorms EV3 | 2013 | The third Mindstorms brick: four motor outputs, four sensor inputs, a screen and a speaker, running Linux | Bluetooth Classic (serial port profile), LEGO's EV3 direct commands | — | Pending |
| LEGO Power Functions | 2007 | Motors, lights and servos plugged into an IR Receiver (8884), normally driven by the IR remote (8885) | Infrared, through an IR LED on the board | UNO R4 WiFi, UNO Q, Nano ESP32 | [`RemotePF`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/pf/RemotePF.java) |
| LEGO Mindstorms NXT | 2006 | The second Mindstorms brick: three motor outputs, four sensor inputs, a screen and a speaker | Bluetooth Classic (serial port profile), LEGO's NXT direct commands | — | Pending |
| LEGO Mindstorms RCX | 1998 | The yellow programmable brick: three motor outputs, three sensor inputs, an LCD and a speaker | Infrared, through an IR LED and receiver on the board | UNO R4 WiFi, UNO Q, Nano ESP32 | [`RemoteRcx`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/rcx/RemoteRcx.java) |

## LEGO Powered Up

[`RemotePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/poweredup/RemotePoweredUpHub.java)
(`io.github.jabrena.juno.remote.lego.poweredup`) lets a Juno program remote-control a LEGO Powered Up hub — run
its motors, sensors, battery and status LED — the same way the LEGO apps do. The board acts as a Bluetooth
Low Energy central and the Java API speaks the
[LEGO Wireless Protocol 3.0](https://lego.github.io/lego-ble-wireless-protocol-docs/) to the hub.
The board-independent BLE transport is a compiler intrinsic backed by the core's BLE library (`ArduinoBLE` on the
UNO boards, and the native BLE stack on the ESP32 boards).

<img src="../images/lego/lego-powered-up-hub.jpg" alt="LEGO Technic Hub 88012 (Powered Up)" width="280">

### Connect to a hub

Create one `RemotePoweredUpHub` at startup, from the board's Bluetooth LE radio and clock, press the hub's green button
so it advertises, and call `connect`. It scans for the first hub it finds, for up to the given milliseconds (`0` waits
forever). The clock is used for BLE and sensor waits. The hub drops the link when it switches off or goes out of range,
so check `isConnected()` in long-running loops and connect again. A Technic Hub's built-in sensors (see
[Read the sensors](#read-the-sensors)) are subscribed on every connect. Only the UNO R4 WiFi, UNO Q, Nano ESP32,
AI Thinker ESP32-CAM and M5Stack Cardputer have `getBluetoothLe()`; on the UNO R3 or UNO WiFi Rev2 the compiler
rejects the call:

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

public final class PoweredUpConnectExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());

        while (true) {
            if (!hub.isConnected()) {
                if (hub.connect(10_000)) {
                    Serial.print("connected to hub type ");
                    Serial.println(hub.hubType());   // TYPE_CITY_HUB, TYPE_TECHNIC_HUB, ...
                } else {
                    Serial.println("no hub found");
                }
            }
            Delay.millis(500);
        }
    }
}
```

[`PoweredUpHubHello`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpHubHello.java) is the smallest check that a hub connects: it prints the battery level and waits for the hub again if the link drops. Close the LEGO apps first: a hub accepts one connection at a time.

### Set the LED color

The hub's status LED takes a `HubColor`: `OFF`, `PINK`, `PURPLE`, `BLUE`, `LIGHT_BLUE`, `CYAN`, `GREEN`, `YELLOW`,
`ORANGE`, `RED` or `WHITE`. For any other color, `setLedRgb(red, green, blue)` takes each channel from `0` to `255`:

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.HubColor;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

public final class PoweredUpLedExample {
    private static final HubColor[] COLORS = {HubColor.RED, HubColor.GREEN, HubColor.BLUE};

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());

        while (true) {
            if (!hub.isConnected() && !hub.connect(10_000)) {
                Serial.println("no hub found");
                Delay.millis(1000);
                continue;
            }
            for (HubColor color : COLORS) {
                if (!hub.isConnected()) {
                    break;
                }
                hub.setLedColor(color);
                Delay.millis(1000);
            }
            hub.setLedRgb(255, 128, 0); // arbitrary RGB orange
            Delay.millis(1000);
        }
    }
}
```

[`PoweredUpHubHello`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpHubHello.java) connects and then cycles the LED through several colors.

### Drive the motors

`getMotor(port)` returns the motor on `HubPort.A` to `HubPort.D`. `drive(percent)` runs it from `-100` (full reverse) to
`100` (full forward). There are three ways to stop: `flt()` lets the motor coast, `stop()` brakes it actively, and
`hold()` holds it where it is. `portDevice(port)` tells which motor the hub detected on a port:

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

public final class PoweredUpMotorExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());
        while (!hub.isConnected() && !hub.connect(10_000)) {
            Serial.println("no hub found");
            Delay.millis(1000);
        }
        if (!hub.isConnected() || hub.portDevice(HubPort.A) == RemotePoweredUpHub.DEVICE_NONE) {
            Serial.println("no motor on port A");
            return;
        }

        while (true) {
            hub.getMotor(HubPort.A).drive(50);    // half speed forward
            Delay.millis(2000);
            hub.getMotor(HubPort.A).drive(-50);   // half speed backward
            Delay.millis(2000);
            hub.getMotor(HubPort.A).stop();       // brakes; flt() would coast
            Delay.millis(1000);
        }
    }
}
```

### Read the sensors

The Technic Hub (88012) has three built-in motion sensors, each on its own port. `getAccelerometer()` returns the
acceleration as an `Accelerometer`, `getGyroscope()` returns the heading and yaw rate as a `Gyroscope`, and
`getTilt()` returns the yaw, pitch and roll angles as a `SampleProvider`. Each returns the same object on every call,
and `connect` subscribes them, so a read needs no start-up call. A read returns zero until the hub has reported.

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;
import io.github.jabrena.juno.robotics.SampleProvider;
import io.github.jabrena.juno.util.Delay;

public final class PoweredUpSensorsExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());
        Accelerometer accelerometer = hub.getAccelerometer();
        Gyroscope gyroscope = hub.getGyroscope();
        SampleProvider tilt = hub.getTilt();
        float[] sample = new float[3];

        while (true) {
            if (!hub.isConnected()) {
                if (!hub.connect(10_000)) {
                    Serial.println("no hub found");
                    Delay.millis(1000);
                    continue;
                }
                gyroscope.reset();                          // heading zero at the current tilt yaw
            }
            int heading = gyroscope.getAngle();             // 0..359, clockwise since reset
            int xAccel = accelerometer.getXAccel();         // whole m/s^2
            tilt.fetchSample(sample, 0);
            Serial.print("heading=");
            Serial.print(heading);
            Serial.print(" x-accel=");
            Serial.print(xAccel);
            Serial.print(" tilt-yaw=");
            Serial.println(sample[0]);
            Serial.print("battery mV=");
            Serial.println(hub.getBattery().voltageMillivolts());
            Delay.millis(500);
        }
    }
}
```

The scales come from the community protocol documentation and are not verified on hardware. The
[`PoweredUpHubSensorModesMonitor`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpHubSensorModesMonitor.java)
example prints the values so you can check them on your hub. [`MagicSquarePoweredUpHubWithGyro`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/MagicSquarePoweredUpHubWithGyro.java)
uses the Technic Hub's heading to turn a vehicle by angle instead of by a calibrated delay.

### Read hub properties and external sensors

The hub also exposes its battery percentage, button, signal strength, name, firmware and hardware versions. External
sensors use their protocol-specific mode: subscribe after every reconnect with `enableSensor`, then poll with
`readSensor` or `readSensorValue` while the program is running. For example, a motor's position is available through
the higher-level `PoweredUpMotor` encoder view:

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.poweredup.HubPort;
import io.github.jabrena.juno.remote.lego.poweredup.PoweredUpMotor;
import io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub;
import io.github.jabrena.juno.util.Delay;

public final class PoweredUpPropertiesExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());
        while (!hub.isConnected() && !hub.connect(10_000)) {
            Serial.println("no hub found");
            Delay.millis(1000);
        }
        if (!hub.isConnected()) {
            return;
        }

        PoweredUpMotor motor = hub.getMotor(HubPort.A);
        hub.enableSensor(HubPort.A, RemotePoweredUpHub.MODE_MOTOR_POSITION);
        motor.resetTachoCount();
        motor.drive(40);
        Delay.millis(2000);
        motor.stop();
        Serial.print("motor degrees=");
        Serial.println(motor.getTachoCount());
        Serial.print("raw position degrees=");
        Serial.println(hub.readSensor(HubPort.A));
        Serial.print("position reports=");
        Serial.println(hub.sensorReportCount(HubPort.A));

        byte[] name = new byte[20];
        int nameLength = hub.hubName(name, name.length);
        Serial.print("name bytes=");
        Serial.println(nameLength);
        Serial.print("battery percent=");
        Serial.println(hub.batteryPercent());
        Serial.print("signal dBm=");
        Serial.println(hub.rssi());
        Serial.print("button pressed=");
        Serial.println(hub.buttonPressed());
    }
}
```

[`MagicSquarePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/MagicSquarePoweredUpHub.java) drives a two-motor vehicle (ports A and B) around a square with two `PoweredUpMotor` objects.
For a single synchronized protocol command, pair the ports with `linkMotors(HubPort.A, HubPort.B)`, drive the returned
virtual port with `setLinkedMotorPower`, and call `unlinkMotors` when finished. The
[`PoweredUpMotorEncoders`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpMotorEncoders.java)
example shows the lower-level position reports.

## LEGO Power Functions

[`RemotePF`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/pf/RemotePF.java)
drives the red (A) and blue (B) outputs of a LEGO Power Functions IR Receiver 8884. `PFLink` sends the 38 kHz
Power Functions protocol through an HX-53 emitter on a GPIO pin or a Grove infrared emitter. The link and receiver
have been tested with Power Functions motors; Juno also exposes the outputs as `PFMotor` instances implementing the
board-independent `DCMotor` contract.

<img src="../images/lego/lego-power-functions.jpg" alt="LEGO Power Functions IR Receiver 8884" width="280">

With a Grove Base Shield, connect the clear Grove infrared emitter to D3 and point it at the receiver. Create the link
once at startup, then bind motors to the receiver channel and output:

```java
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.pf.PFChannel;
import io.github.jabrena.juno.remote.lego.pf.PFLink;
import io.github.jabrena.juno.remote.lego.pf.PFMotor;
import io.github.jabrena.juno.remote.lego.pf.PFPort;
import io.github.jabrena.juno.remote.lego.pf.RemotePF;
import io.github.jabrena.juno.util.Delay;

public final class PowerFunctionsExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        GroveBaseShield shield = new GroveBaseShield(ArduinoUno.getGpio());
        PFLink link = new PFLink(new GroveInfraredEmitter(shield.getD3()));
        RemotePF remote = new RemotePF(link);
        PFMotor left = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);
        PFMotor right = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.BLUE);

        while (true) {
            left.setPower(70);
            right.setPower(70);
            left.forward();
            right.forward();
            Delay.millis(500);
            left.backward();
            right.backward();
            Delay.millis(500);
            left.flt();
            right.flt();
            Delay.millis(1000);
        }
    }
}
```

`PFMotor.setPower` accepts `0..100` percent and maps it to the receiver's seven protocol speed steps. Use
`forward()`, `backward()`, `stop()` for an active brake, or `flt()` to let an output coast. The receiver channel switch
must match the `PFChannel` used by both motors. See [`PFPowerSequenceWithGrove`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/pf/PFPowerSequenceWithGrove.java)
and [`MagicSquarePFWithGrove`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/pf/MagicSquarePFWithGrove.java)
for complete examples.

## Lego mindstorms RCX

[`RemoteRcx`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/rcx/RemoteRcx.java) (`io.github.jabrena.juno.remote.lego.rcx`) lets a Juno program remote-control a LEGO
Mindstorms RCX brick over infrared: run its motors, read its sensors and buttons, play sounds and read its batteries.
One object represents the brick, and its getters hand out its parts, all allocated once when
the `RemoteRcx` is created. The board needs an IR receiver module and an IR LED, here on pins 2 and 3, pointed at the
brick's infrared window. The motors, sensors, sounds and battery work with LEGO's own firmware; reading the
buttons and writing text on the display need Juno's RCX firmware, downloaded to the brick once with
[`RcxFirmwareDownload`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxFirmwareDownload.java). The [Nano ESP32](../arduino-nano-esp32#remote-control-a-lego-rcx) page shows the same
setup with Grove infrared modules.

<img src="../images/lego/lego-rcx.png" alt="LEGO Mindstorms RCX 1.0" width="280">

### Installing the JUno firmware to lego mindstorms RCX

Download Juno's firmware to the brick once. Wire the IR receiver to pin 2 and the IR LED to pin 3, point both at the
switched-on RCX from 10 to 50 cm, and keep them still for about ten minutes; the serial monitor shows the progress:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.remote.lego.rcx.RcxFirmwareDownload \
  -Djuno.board=arduino-uno-r4-wifi
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi
```

On a Nano ESP32 with the Nano Connector Carrier, put a Grove infrared emitter in the A0/A1 socket and a Grove infrared
receiver on the I2C socket's SCL line, and run the carrier version instead:

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.remote.lego.rcx.NanoCarrierRcxFirmwareDownload \
  -Djuno.board=arduino-nano-esp32
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32
```

### Connecting with the brick

A brick that already runs firmware is left alone, so resetting the board afterwards cannot delete it.

A program reaches the brick through an infrared receiver and emitter pair, either HX modules wired to plain pins or
Grove modules plugged into Grove sockets. Create the `RcxLink` and `RemoteRcx` once, at startup; construction configures
the infrared transport and brick.

#### HX infrared modules

With an HX-M121 receiver and an HX-53 emitter, here on pins 2 and 3 of an UNO board:

<img src="../images/ir/hx-m121-hx-53.png" alt="HX-53 infrared emitter and HX-M121 infrared receiver modules" width="280">

```java
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;

public final class RcxHxConnectExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Gpio gpio = ArduinoUno.getGpio();
        HXM121InfraredReceiver receiver = new HXM121InfraredReceiver(gpio.digitalPin("D2")); // DAT on D2
        HX53InfraredEmitter emitter = new HX53InfraredEmitter(gpio.digitalPin("D3"));       // DAT on D3
        RcxLink link = new RcxLink(emitter, receiver, ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.println("RCX infrared link ready");
    }
}
```

#### Grove infrared modules

With Grove infrared modules on a Nano ESP32's Nano Connector Carrier, the emitter in the A0/A1 socket and the receiver
on the I2C socket's SCL line, passing the Nano's clock:

<img src="../images/grove/grove-ir-emitter.jpg" alt="Grove Infrared Emitter" width="240">
<img src="../images/grove/grove-ir-receiver.jpg" alt="Grove Infrared Receiver" width="240">

```java
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.grove.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredEmitter;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveInfraredReceiver;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;

public final class RcxGroveConnectExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(ArduinoNano.getGpio());
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(emitter, receiver, ArduinoNano.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.println("RCX Grove infrared link ready");
    }
}
```

The complete examples below repeat the setup so that each one can be copied into its own Juno program. They use the
HX modules on an UNO; substitute the typed Grove setup above when using the Nano Connector Carrier.

### Use the motors

The brick has three motor outputs, `getMotorA()`, `getMotorB()` and `getMotorC()`; `and` combines two into one, as a
vehicle's left and right wheels. `drive(speed)` runs them from `-RcxMotor.MAX_LEVEL` (full reverse) to `MAX_LEVEL`
(7, full forward). `stop()` brakes them and `flt()` lets them coast:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxMotor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxMotorExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxMotor wheels = rcx.getMotorA().and(rcx.getMotorC());

        while (true) {
            wheels.drive(RcxMotor.MAX_LEVEL);    // forward
            Delay.millis(2000);
            wheels.drive(-RcxMotor.MAX_LEVEL);   // backward
            Delay.millis(2000);
            wheels.stop();
            Delay.millis(1000);
        }
    }
}
```

[`RcxDriveAC`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxDriveAC.java) drives a vehicle with its motors on outputs A and C.

### Use the rotation sensor

A rotation sensor counts 16 steps per turn of its axle, signed by direction. Plug it into one of the brick's three
inputs, `getSensor1()` to `getSensor3()`. Constructing the sensor configures the input; `reset()` sets the count back to zero:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxRotationSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxRotationExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxRotationSensor rotation = new RcxRotationSensor(rcx.getSensor1());
        rotation.reset();

        while (true) {
            Serial.print(rotation.steps());       // 16 per turn
            Serial.print(" steps, ");
            Serial.print(rotation.degrees());     // 22.5 per step
            Serial.println(" degrees");
            Delay.millis(200);
        }
    }
}
```

[`RcxRotationSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxRotationSensorExample.java) prints the steps and the angle each time the axle turns.

### Use the touch sensor

`isPressed()` tells whether the touch sensor's button is down:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxTouchSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxTouchExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxTouchSensor touch = new RcxTouchSensor(rcx.getSensor1());

        boolean wasPressed = false;
        while (true) {
            boolean pressed = touch.isPressed();
            if (pressed != wasPressed) {
                Serial.println(pressed ? "Pressed" : "Released");
                wasPressed = pressed;
            }
            Delay.millis(100);
        }
    }
}
```

[`RcxTouchSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxTouchSensorExample.java) prints `Pressed` or `Released` each time it changes.

### Use the temperature sensor

`temperatureTenths()` returns the temperature in tenths of a degree Celsius, so `235` is 23.5 °C:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RcxTemperatureSensor;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxTemperatureExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxTemperatureSensor temperature = new RcxTemperatureSensor(rcx.getSensor1());

        while (true) {
            int tenths = temperature.temperatureTenths();
            Serial.print(tenths / 10);
            Serial.print(".");
            Serial.print(tenths % 10);
            Serial.println(" C");
            Delay.millis(1000);
        }
    }
}
```

[`RcxTemperatureSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxTemperatureSensorExample.java) prints the temperature once a second.

### Use the light sensor

`lightPercent()` returns the light level from `0` to `100`, or `-1` if the brick did not answer:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxLightSensor;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxLightExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxLightSensor light = new RcxLightSensor(rcx.getSensor1());

        while (true) {
            int percent = light.lightPercent();
            if (percent >= 0) {
                Serial.print(percent);
                Serial.println(" %");
            } else {
                Serial.println("light sensor: no answer");
            }
            Delay.millis(500);
        }
    }
}
```

[`RcxLightSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxLightSensorExample.java) prints the light level twice a second.

### Play music

The brick's speaker plays one of six built-in sounds (`RcxAudio.SOUND_BEEP`, `SOUND_SWEEP_UP`, ...) or a tone of a
given frequency, from about 31 to 20000 Hz, for a duration in hundredths of a second. A tone replaces any sound
still playing, so wait for it to end before the next:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxAudio;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxMusicExample {
    private static final int[] NOTES = {262, 294, 330, 349, 392}; // C, D, E, F, G

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxAudio audio = rcx.getAudio();

        while (true) {
            audio.playSound(RcxAudio.SOUND_BEEP);
            Delay.millis(500);
            for (int note : NOTES) {
                audio.playTone(note, 30);              // 0.3 seconds
                Delay.millis(350);
            }
            Delay.millis(1000);
        }
    }
}
```

[`RcxFurElise`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxFurElise.java) plays the melody of Beethoven's "Für Elise" on the brick.

### Read the battery

`getBattery().millivolts()` asks the brick for its battery voltage. A negative value means the brick did not answer:
it is off, out of range, or not facing the IR LED. That makes it a good first check that the link works:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxBattery;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxBatteryExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxBattery battery = rcx.getBattery();

        while (true) {
            int millivolts = battery.millivolts();
            if (millivolts < 0) {
                Serial.println("No answer from the RCX");
            } else {
                Serial.print(millivolts);
                Serial.println(" mV");
            }
            Delay.millis(2000);
        }
    }
}
```

[`RcxPing`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxPing.java) reads the battery in a loop and reports whether the brick answers.

### Read the buttons

With Juno's firmware, clicking View, Prgm or Run changes nothing on the brick: it remembers the click, and
`getButtons().clicked()` returns the buttons clicked since the previous call as a mask of `RcxButtons.VIEW`, `PRGM`
and `RUN`. The program decides what the display shows: `getLcd().showText(...)` writes up to four characters on the
digits, and `getLcd().clear()` blanks them:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.hx.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.hx.HXM121InfraredReceiver;
import io.github.jabrena.juno.remote.lego.rcx.RcxChannel;
import io.github.jabrena.juno.remote.lego.rcx.RcxButtons;
import io.github.jabrena.juno.remote.lego.rcx.RcxLcd;
import io.github.jabrena.juno.remote.lego.rcx.RcxLink;
import io.github.jabrena.juno.remote.lego.rcx.RemoteRcx;
import io.github.jabrena.juno.util.Delay;

public final class RcxButtonsExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2), ArduinoUno.getClock());
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        RcxButtons buttons = rcx.getButtons();
        RcxLcd lcd = rcx.getLcd();
        while (!lcd.clear()) {
            Serial.println("RCX firmware not responding");
            Delay.millis(1000);
        }

        while (true) {
            int clicked = buttons.clicked();
            if (clicked > 0) {
                if ((clicked & RcxButtons.VIEW) != 0) {
                    lcd.showText("VIEW");
                    Serial.println("VIEW");
                }
                if ((clicked & RcxButtons.PRGM) != 0) {
                    lcd.showText("PRGM");
                    Serial.println("PRGM");
                }
                if ((clicked & RcxButtons.RUN) != 0) {
                    lcd.showText("RUN");
                    Serial.println("RUN");
                }
            }
            Delay.millis(200);
        }
    }
}
```
