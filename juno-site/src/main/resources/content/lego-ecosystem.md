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
| Powered Up | 2017 | Hubs (City, Technic, BOOST, DUPLO Train) with motors and sensors, driven by LEGO's apps | Bluetooth LE | UNO R4 WiFi, UNO Q | [`RemotePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/poweredup/RemotePoweredUpHub.java) |
| Mindstorms EV3 | 2013 | The third Mindstorms brick: four motor outputs, four sensor inputs, a screen and a speaker, running Linux | Bluetooth Classic (serial port profile), LEGO's EV3 direct commands | — | Pending |
| Power Functions | 2007 | Motors, lights and servos plugged into an IR Receiver (8884), normally driven by the IR remote (8885) | Infrared, through an IR LED on the board | UNO R4 WiFi, UNO Q, Nano ESP32 | [`RemotePF`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/pf/RemotePF.java) |
| Mindstorms NXT | 2006 | The second Mindstorms brick: three motor outputs, four sensor inputs, a screen and a speaker | Bluetooth Classic (serial port profile), LEGO's NXT direct commands | — | Pending |
| Mindstorms RCX | 1998 | The yellow programmable brick: three motor outputs, three sensor inputs, an LCD and a speaker | Infrared, through an IR LED and receiver on the board | UNO R4 WiFi, UNO Q, Nano ESP32 | [`RemoteRcx`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/rcx/RemoteRcx.java) |

## LEGO Powered Up

[`RemotePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/poweredup/RemotePoweredUpHub.java)
(`io.github.jabrena.juno.remote.lego.poweredup`) lets a Juno program remote-control a LEGO Powered Up hub — run
its motors and set its status LED — the same way the LEGO apps do. The board acts as a Bluetooth
Low Energy central and speaks the
[LEGO Wireless Protocol 3.0](https://lego.github.io/lego-ble-wireless-protocol-docs/) to the hub.
It is a compiler intrinsic backed by the Arduino `ArduinoBLE` library.

<img src="../images/lego/lego-powered-up-hub.jpg" alt="LEGO Technic Hub 88012 (Powered Up)" width="280">

### Connect to a hub

Create one `RemotePoweredUpHub` at startup, from the board's Bluetooth LE radio, press the hub's green button so it
advertises, and call `connect`. It scans for the first hub it finds, for up to the given milliseconds (`0` waits
forever). The hub drops the link when it switches off or goes out of range, so check `isConnected()` in long-running
loops and connect again. A Technic Hub's built-in sensors (see [Read the sensors](#read-the-sensors)) are subscribed
on every connect. Only the boards with a BLE adapter (UNO R4 WiFi, UNO Q, Nano ESP32, AI Thinker ESP32-CAM and
M5Stack Cardputer) have `getBluetoothLe()`; on the UNO R3 or UNO WiFi Rev2 the compiler rejects the call:

```java
RemotePoweredUpHub hub = new RemotePoweredUpHub(ArduinoUno.getBluetoothLe(), ArduinoUno.getClock());

while (true) {
    if (!hub.isConnected()) {
        if (hub.connect(10_000)) {
            Serial.print("connected to hub type ");
            Serial.println(hub.hubType());   // TYPE_CITY_HUB, TYPE_TECHNIC_HUB, ...
        }
    }
    Delay.millis(500);
}
```

[`PoweredUpHubHello`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpHubHello.java) is the smallest check that a hub connects: it prints the battery level and waits for the hub again if the link drops. Close the LEGO apps first: a hub accepts one
connection at a time.

### Set the LED color

The hub's status LED takes a `HubColor`: `OFF`, `PINK`, `PURPLE`, `BLUE`, `LIGHT_BLUE`, `CYAN`, `GREEN`, `YELLOW`,
`ORANGE`, `RED` or `WHITE`. For any other color, `setLedRgb(red, green, blue)` takes each channel from `0` to `255`:

```java
HubColor[] colors = {
    HubColor.RED, HubColor.GREEN, HubColor.BLUE
};

while (hub.isConnected()) {
    for (HubColor color : colors) {
        hub.setLedColor(color);
        Delay.millis(1000);
    }
}
```

[`PoweredUpHubHello`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/PoweredUpHubHello.java) connects and then cycles the LED through several colors.

### Drive the motors

`getMotor(port)` returns the motor on `HubPort.A` to `HubPort.D`. `drive(percent)` runs it from `-100` (full reverse) to
`100` (full forward). There are three ways to stop: `flt()` lets the motor coast, `stop()` brakes it actively, and
`hold()` holds it where it is. `portDevice(port)` tells which motor the hub detected on a port:

```java
if (hub.portDevice(HubPort.A) != RemotePoweredUpHub.DEVICE_NONE) {
    hub.getMotor(HubPort.A).drive(50);    // half speed forward
    Delay.millis(2000);
    hub.getMotor(HubPort.A).drive(-50);   // half speed backward
    Delay.millis(2000);
    hub.getMotor(HubPort.A).stop();       // brakes; flt() would coast
}
```

### Read the sensors

The Technic Hub (88012) has three built-in motion sensors, each on its own port. `getAccelerometer()` returns the
acceleration as an `Accelerometer`, `getGyroscope()` returns the heading and yaw rate as a `Gyroscope`, and
`getTilt()` returns the yaw, pitch and roll angles as a `SampleProvider`. Each returns the same object on every call,
and `connect` subscribes them, so a read needs no start-up call. A read returns zero until the hub has reported.

```java
Accelerometer accelerometer = hub.getAccelerometer();
Gyroscope gyroscope = hub.getGyroscope();
gyroscope.reset();                                   // heading zero at the current tilt yaw
int heading = gyroscope.getAngle();                  // 0..359, clockwise since reset
int xAccel = accelerometer.getXAccel();              // whole m/s^2
int millivolts = hub.getBattery().voltageMillivolts();
```

The scales come from the community protocol documentation and are not verified on hardware. The
`PoweredUpHubSensorModesMonitor` example prints the raw values so you can check them on your hub.

[`MagicSquarePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/poweredup/MagicSquarePoweredUpHub.java) drives a two-motor vehicle (ports A and B) around a square. To drive two motors as one, as a vehicle's left and
right wheels, pair them with `linkMotors(PORT_A, PORT_B)` and drive the pair with `setLinkedMotorPower`.

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
GroveBaseShield shield = new GroveBaseShield();
PFLink link = new PFLink(new GroveInfraredEmitter(shield.getD3()));
link.begin();
RemotePF remote = new RemotePF(link);
PFMotor left = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.RED);
PFMotor right = new PFMotor(remote, PFChannel.CHANNEL_1, PFPort.BLUE);

left.setPower(70);
right.setPower(70);
left.forward();
right.forward();
Delay.millis(2000);
left.flt();
right.flt();
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
Grove modules plugged into Grove sockets. Create the `RemoteRcx` once, at startup, and call `begin()`.

#### HX infrared modules

With an HX-M121 receiver and an HX-53 emitter, here on pins 2 and 3 of an UNO board:

<img src="../images/ir/hx-m121-hx-53.png" alt="HX-53 infrared emitter and HX-M121 infrared receiver modules" width="280">

```java
HXM121InfraredReceiver receiver = new HXM121InfraredReceiver(Gpio.getD2());   // DAT on D2
HX53InfraredEmitter emitter = new HX53InfraredEmitter(Gpio.getD3());         // DAT on D3
RemoteRcx rcx = new RemoteRcx(new RcxLink(), receiver, emitter);
rcx.begin();
```

#### Grove infrared modules

With Grove infrared modules on a Nano ESP32's Nano Connector Carrier, the emitter in the A0/A1 socket and the receiver
on the I2C socket's SCL line, passing the Nano's clock:

<img src="../images/grove/grove-ir-emitter.jpg" alt="Grove Infrared Emitter" width="240">
<img src="../images/grove/grove-ir-receiver.jpg" alt="Grove Infrared Receiver" width="240">

```java
NanoConnectorCarrier carrier = new NanoConnectorCarrier();
GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
RemoteRcx rcx = new RemoteRcx(new RcxLink(ArduinoNano.getClock()), receiver, emitter, ArduinoNano.getClock());
rcx.begin();
```

Either way, the examples below use the same `rcx`.

### Use the motors

The brick has three motor outputs, `getMotorA()`, `getMotorB()` and `getMotorC()`; `and` combines two into one, as a
vehicle's left and right wheels. `drive(speed)` runs them from `-RcxMotor.MAX_LEVEL` (full reverse) to `MAX_LEVEL`
(7, full forward). `stop()` brakes them and `flt()` lets them coast:

```java
RcxMotor wheels = rcx.getMotorA().and(rcx.getMotorC());

wheels.drive(RcxMotor.MAX_LEVEL);    // forward
Delay.millis(2000);
wheels.drive(-RcxMotor.MAX_LEVEL);   // backward
Delay.millis(2000);
wheels.stop();
```

[`RcxDriveAC`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxDriveAC.java) drives a vehicle with its motors on outputs A and C.

### Use the rotation sensor

A rotation sensor counts 16 steps per turn of its axle, signed by direction. Plug it into one of the brick's three
inputs, `getSensor1()` to `getSensor3()`, and call `begin()` after `rcx.begin()`; `reset()` sets the count back to zero:

```java
RcxRotationSensor rotation = new RcxRotationSensor(rcx.getSensor1());
rotation.begin();
rotation.reset();

while (true) {
    Serial.print(rotation.steps());       // 16 per turn
    Serial.print(" steps, ");
    Serial.print(rotation.degrees());     // 22.5 per step
    Serial.println(" degrees");
    Delay.millis(200);
}
```

[`RcxRotationSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxRotationSensorExample.java) prints the steps and the angle each time the axle turns.

### Use the touch sensor

`isPressed()` tells whether the touch sensor's button is down:

```java
RcxTouchSensor touch = new RcxTouchSensor(rcx.getSensor1());
touch.begin();

while (!touch.isPressed()) {
    Delay.millis(50);
}
Serial.println("Pressed");
```

[`RcxTouchSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxTouchSensorExample.java) prints `Pressed` or `Released` each time it changes.

### Use the temperature sensor

`temperatureTenths()` returns the temperature in tenths of a degree Celsius, so `235` is 23.5 °C:

```java
RcxTemperatureSensor temperature = new RcxTemperatureSensor(rcx.getSensor1());
temperature.begin();

int tenths = temperature.temperatureTenths();
Serial.print(tenths / 10);
Serial.print(".");
Serial.print(tenths % 10);
Serial.println(" C");
```

[`RcxTemperatureSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxTemperatureSensorExample.java) prints the temperature once a second.

### Use the light sensor

`lightPercent()` returns the light level from `0` to `100`, or `-1` if the brick did not answer:

```java
RcxLightSensor light = new RcxLightSensor(rcx.getSensor1());
light.begin();

int percent = light.lightPercent();
if (percent >= 0) {
    Serial.print(percent);
    Serial.println(" %");
}
```

[`RcxLightSensorExample`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxLightSensorExample.java) prints the light level twice a second.

### Play music

The brick's speaker plays one of six built-in sounds (`RcxAudio.SOUND_BEEP`, `SOUND_SWEEP_UP`, ...) or a tone of a
given frequency, from about 31 to 20000 Hz, for a duration in hundredths of a second. A tone replaces any sound
still playing, so wait for it to end before the next:

```java
RcxAudio audio = rcx.getAudio();
audio.playSound(RcxAudio.SOUND_BEEP);
Delay.millis(500);

int[] notes = {262, 294, 330, 349, 392};   // C, D, E, F, G
for (int note : notes) {
    audio.playTone(note, 30);              // 0.3 seconds
    Delay.millis(350);
}
```

[`RcxFurElise`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxFurElise.java) plays the melody of Beethoven's "Für Elise" on the brick.

### Read the battery

`getBattery().millivolts()` asks the brick for its battery voltage. A negative value means the brick did not answer:
it is off, out of range, or not facing the IR LED. That makes it a good first check that the link works:

```java
int millivolts = rcx.getBattery().millivolts();
if (millivolts < 0) {
    Serial.println("No answer from the RCX");
} else {
    Serial.print(millivolts);
    Serial.println(" mV");
}
```

[`RcxPing`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxPing.java) reads the battery in a loop and reports whether the brick answers.

### Read the buttons

With Juno's firmware, clicking View, Prgm or Run changes nothing on the brick: it remembers the click, and
`getButtons().clicked()` returns the buttons clicked since the previous call as a mask of `RcxButtons.VIEW`, `PRGM`
and `RUN`. The program decides what the display shows: `getLcd().showText(...)` writes up to four characters on the
digits, and `getLcd().clear()` blanks them:

```java
rcx.getLcd().clear();
while (true) {
    int clicked = rcx.getButtons().clicked();
    if ((clicked & RcxButtons.RUN) != 0) {
        rcx.getLcd().showText("RUN");
    }
    Delay.millis(200);
}
```
