---
title: "LEGO Mindstorms RCX"
description: "The LEGO Mindstorms RCX brick over infrared, driven with RemoteRcx."
layout: page
---

{|
[`RemoteRcx`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/remote/lego/rcx/RemoteRcx.java) (`io.github.jabrena.juno.remote.lego.rcx`) lets a Juno program remote-control a LEGO
Mindstorms RCX brick over infrared: run its motors, read its sensors and buttons, play sounds and read its batteries.
One object represents the brick, and its getters hand out its parts, all allocated once when
the `RemoteRcx` is created. The board needs an IR receiver module and an IR LED, here on pins 2 and 3, pointed at the
brick's infrared window. The motors, sensors, sounds and battery work with LEGO's own firmware; reading the
buttons and writing text on the display need Juno's RCX firmware, downloaded to the brick once with
[`RcxFirmwareDownload`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/remote/lego/rcx/RcxFirmwareDownload.java). The [Nano ESP32](../../../boards/arduino-nano-esp32#remote-control-a-lego-rcx) page shows the same
setup with Grove infrared modules.

<img src="../../../images/lego/lego-rcx.png" alt="LEGO Mindstorms RCX 1.0" width="280">

### Installing the JUno firmware to lego mindstorms RCX

Download Juno's firmware to the brick once. Wire the IR receiver to pin 2 and the IR LED to pin 3, point both at the
switched-on RCX from 10 to 50 cm, and keep them still for about ten minutes; the serial monitor shows the progress:

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash \
  -Djuno.main=io.github.jabrena.juno.api.remote.lego.rcx.RcxFirmwareDownload \
  -Djuno.board=arduino-uno-r4-wifi
./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r4-wifi
```

On a Nano ESP32 with the Nano Connector Carrier, put a Grove infrared emitter in the A0/A1 socket and a Grove infrared
receiver on the I2C socket's SCL line, and run the carrier version instead:

```bash
./mvnw -f juno-examples/pom.xml compile juno:flash \
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

<img src="../../../images/ir/hx-m121-hx-53.png" alt="HX-53 infrared emitter and HX-M121 infrared receiver modules" width="280">

```java
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
        Gpio gpio = MCU.getGpio();
        HXM121InfraredReceiver receiver = new HXM121InfraredReceiver(gpio.digitalPin("D2")); // DAT on D2
        HX53InfraredEmitter emitter = new HX53InfraredEmitter(gpio.digitalPin("D3"));       // DAT on D3
        RcxLink link = new RcxLink(emitter, receiver);
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.println("RCX infrared link ready");
    }
}
```

#### Grove infrared modules

With Grove infrared modules on a Nano ESP32's Nano Connector Carrier, the emitter in the A0/A1 socket and the receiver
on the I2C socket's SCL line, passing the Nano's clock:

<img src="../../../images/grove/grove-ir-emitter.jpg" alt="Grove Infrared Emitter" width="240">
<img src="../../../images/grove/grove-ir-receiver.jpg" alt="Grove Infrared Receiver" width="240">

```java
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
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        GroveInfraredEmitter emitter = new GroveInfraredEmitter(carrier.getGroveA0());
        GroveInfraredReceiver receiver = new GroveInfraredReceiver(carrier.getI2cScl());
        RcxLink link = new RcxLink(emitter, receiver);
        RemoteRcx rcx = new RemoteRcx(link, RcxChannel.CHANNEL_1);
        Serial.println("RCX Grove infrared link ready");
    }
}
```

The complete examples below repeat the setup so that each one can be copied into its own Juno program. They use the
HX modules on an UNO; substitute the typed Grove setup above when using the Nano Connector Carrier.

### Use the motors

The brick has three motor outputs, `getMotorA()`, `getMotorB()` and `getMotorC()`, or `motor(RcxMotorPort.A)` to `motor(RcxMotorPort.C)`, which return the same objects; `and` combines two into one, as a
vehicle's left and right wheels. `drive(speed)` runs them from `-RcxMotor.MAX_LEVEL` (full reverse) to `MAX_LEVEL`
(7, full forward). `stop()` brakes them and `flt()` lets them coast:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
inputs, `getSensor1()` to `getSensor3()`, or `sensor(RcxSensorPort.INPUT_1)` to `sensor(RcxSensorPort.INPUT_3)`. Constructing the sensor configures the input; `reset()` sets the count back to zero:

```java
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
        RcxLink link = new RcxLink(new HX53InfraredEmitter(3), new HXM121InfraredReceiver(2));
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
|}
