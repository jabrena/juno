---
title: "LEGO Powered Up"
description: "Driving LEGO Powered Up motors and hub LEDs over Bluetooth LE, from the UNO R4 WiFi or the UNO Q."
layout: page
---

[`PoweredUpHubRemote`](https://github.com/jabrena/juno/blob/main/juno-api/src/main/java/io/github/jabrena/juno/api/lego/PoweredUpHubRemote.java)
(`io.github.jabrena.juno.api.lego`) lets a Juno program remote-control a LEGO Powered Up hub — run
its motors and set its status LED — the same way the LEGO apps do. The board acts as a Bluetooth
Low Energy central and speaks the
[LEGO Wireless Protocol 3.0](https://lego.github.io/lego-ble-wireless-protocol-docs/) to the hub.
It is a compiler intrinsic backed by the Arduino `ArduinoBLE` library.

## Requirements

- One of the two supported boards. The same Java program runs on both; only the way the
  generated code reaches the radio differs:

  | Board | Bluetooth LE path |
  | --- | --- |
  | UNO R4 WiFi | The on-board ESP32-S3 radio module. |
  | UNO Q | The Linux side's Bluetooth adapter (`hci0`). `ArduinoBLE` tunnels raw HCI packets to it through `Arduino_RouterBridge`, so the board's `arduino-router` must be 0.7.0 or newer (update the board image through App Lab if needed). While the sketch holds the adapter, Linux's own Bluetooth stack can't use it. |

- The Arduino `ArduinoBLE` library installed — 2.1.0 or newer, the first release with the UNO Q
  transport (also done by `juno:install-deps`):

  ```bash
  arduino-cli lib install ArduinoBLE
  ```

- A Powered Up hub: City Hub (88009), Technic Hub (88012), BOOST Move Hub (88006) or the DUPLO
  Train Hub. Close the LEGO apps first — a hub accepts a single connection at a time.

The UNO R4 WiFi's radio module cannot run Bluetooth LE and Wi-Fi at the same time, so a program
for that board that uses `PoweredUpHubRemote` should not also use `Wifi`, `Udp`, or the HTTP/email APIs.

## API

```java
import io.github.jabrena.juno.api.lego.PoweredUpHubRemote;

if (PoweredUpHubRemote.connect(10_000)) {              // scan up to 10 s; 0 waits forever
    PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.COLOR_GREEN);
    PoweredUpHubRemote.setMotorPower(PoweredUpHubRemote.PORT_A, 50);   // -100..100 percent
    PoweredUpHubRemote.brakeMotor(PoweredUpHubRemote.PORT_A);
}
```

| Method | Meaning |
| --- | --- |
| `connect(timeoutMillis)` | Scans for the first advertising hub (press its green button) and connects. Returns `true` once connected, or immediately if already connected. A timeout of `0` or less waits indefinitely. |
| `isConnected()` | `false` once the hub switches off or goes out of range; call `connect` again to reconnect. |
| `hubType()` | The hub's advertised system type: `TYPE_CITY_HUB`, `TYPE_TECHNIC_HUB`, `TYPE_MOVE_HUB`, `TYPE_DUPLO_TRAIN_HUB`, ... (`TYPE_UNKNOWN` before connecting). |
| `setMotorPower(port, percent)` | Runs any Powered Up motor (train, simple, or tacho) on `PORT_A`..`PORT_D`. Negative values reverse, `0` coasts, values beyond ±100 are clamped. |
| `brakeMotor(port)` | Actively brakes the motor, instead of letting it coast. |
| `holdMotor(port)` | Holds a tacho motor where it is, resisting being turned: the third stop level next to coasting (`setMotorPower(port, 0)`) and braking. Any later power command releases it. |
| `portDevice(port)` | The device the hub detected on `PORT_A`..`PORT_D` (a `DEVICE_*` type such as `DEVICE_TECHNIC_LARGE_MOTOR`, or `DEVICE_NONE`). The hub announces its ports after `connect` and when something is plugged or unplugged, so call it regularly. |
| `linkMotors(a, b)`, `setLinkedMotorPower(port, first, second)`, `brakeLinkedMotors(port)`, `unlinkMotors(port)` | Pairs two motors into one virtual port, so a single command drives both in sync. |
| `setLedColor(color)` | Sets the hub's LED to a LEGO color index: `COLOR_OFF`, `COLOR_PINK`, ..., `COLOR_RED`, `COLOR_WHITE`. |
| `enableSensor(port, mode)` | Asks the hub to report every change of one mode of the motor or sensor on `port`. See [Reading motors and sensors](#reading-motors-and-sensors). |
| `readSensor(port)` | The latest value that port reported, or `0` before its first report. |
| `disconnect()` | Drops the connection; the hub stays on and starts advertising again. |
| `switchOff()` | Switches the hub off. |

A program talks to one hub at a time. Commands sent while no hub is connected are ignored, so
check `isConnected()` in long-running loops and reconnect when it turns `false`.

## Reading motors and sensors

Motors with a rotation sensor, sensors, and the Powered Up remote report values once a program
subscribes to one of their *modes*. `enableSensor(port, mode)` subscribes, and from then on the hub
sends a message whenever the value changes; `readSensor(port)` returns the latest one.

```java
PoweredUpHubRemote.enableSensor(PoweredUpHubRemote.PORT_A, PoweredUpHubRemote.MODE_MOTOR_POSITION);
...
int degrees = PoweredUpHubRemote.readSensor(PoweredUpHubRemote.PORT_A);
```

| Device | Mode | `readSensor` value |
| --- | --- | --- |
| Tacho motor (BOOST, Technic, SPIKE motors) | `MODE_MOTOR_POSITION` | Position in degrees, cumulative since the hub started. |
| Tacho motor | `MODE_MOTOR_SPEED` | Speed, as a percentage of full speed. |
| Color and Distance Sensor (88007) | `MODE_COLOR` | The sensor's color number (mostly, but not fully, matching the `COLOR_*` LED values), `-1` for none. |
| Color and Distance Sensor (88007) | `MODE_PROXIMITY` | `0` (touching) to `10` (nothing in range). |
| Powered Up remote (88010), on `PORT_A`/`PORT_B` | `MODE_REMOTE_BUTTONS` | `REMOTE_RELEASED`, `REMOTE_PLUS`, `REMOTE_MINUS` or `REMOTE_STOP`. |

Mode numbers belong to each device, so other devices work too when given their mode number.
Things to know:

- `readSensor` decodes modes that report **one** 8-, 16- or 32-bit value. For modes reporting
  several values at once (a hub's tilt axes, raw RGB), it returns only the first byte.
- Up to **eight** ports report at a time; enabling a ninth is ignored.
- Values arrive while a `PoweredUpHubRemote` call runs, not during `Delay.millis`, so call `readSensor`
  regularly in loops that wait for a value.
- A new `connect()` starts with no ports reporting: enable them again after reconnecting.
- The remote connects as a hub of its own (`TYPE_REMOTE_CONTROL`). Since a program talks to one
  hub at a time, it can't yet drive a train hub from a remote.

### Example: driving a square

[`MagicSquarePoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/lego/MagicSquarePoweredUpHub.java)
drives a two-motor vehicle (left motor on port A, right on port B) around a square, forever: straight
for 3 seconds, stop, turn left 90 degrees, straight for 3 seconds, stop, turn left 90 degrees:

```java
Serial.println("Straight");
drive(POWER, POWER, STRAIGHT_MILLIS);
stop();
Serial.println("Turn left");
drive(-POWER, POWER, TURN_MILLIS);
stop();
```

`TURN_MILLIS` is the time a spin on the spot takes for 90 degrees; tune it for your vehicle.
`MagicSquareRcx` and `MagicSquareScout` in the same package do the same over infrared with an RCX or
Scout brick, using `RcxRemote` and `ScoutRemote`.

## Example: a touch screen remote

[`PoweredUpHubHello`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/lego/PoweredUpHubHello.java)
is the smallest check that a hub connects: it cycles the hub's LED through nine colors and reconnects if the
link drops.

[`PoweredUpHubTFT`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/lego/PoweredUpHubTFT.java)
is a remote for the [ELEGOO 2.8" TFT touch shield](../tft-touch-shield). A button along the bottom connects and
disconnects, and four tabs choose the view:

| Tab | What it does |
| --- | --- |
| INFO | Hub type, battery, signal strength (RSSI), firmware and hardware versions, and the tilt angles of a Technic Hub. |
| LED | A grid of nine colors; a touch sets the hub LED. |
| MOTORS | All four ports with the device the hub detected on each (`portDevice`). A motor gets `-` / `+` buttons for its power and shows its tacho value; STOP brakes every motor and ZERO sets the tacho values to 0 (an offset kept on the Arduino). |
| PAIR | The first two detected motors as a pair. SYNC links them (`linkMotors`) so one `-` / `+` drives both with a single command. Three stop levels for the STOP button: COAST, BRAKE and HOLD. ZERO sets the two tacho values to 0. |

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.lego.PoweredUpHubTFT \
  -Djuno.board=arduino-uno-q
```

The device type numbers, the tacho values and the hold command follow the community documentation of the
LEGO Wireless Protocol; they have been tried on a hub only briefly, so check them on yours.

## Example: a shuttling train

[`LegoTrain`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/lego/LegoTrain.java)
ramps a train motor on port A up to cruising speed, brakes, and repeats in reverse, using the hub's
LED to show the direction:

```java
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class LegoTrain {
    private static final int MOTOR = PoweredUpHubRemote.PORT_A;

    public static void main(String[] args) {
        Serial.begin(9600);
        while (true) {
            if (!PoweredUpHubRemote.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                PoweredUpHubRemote.connect(0);
            }
            run(1, PoweredUpHubRemote.COLOR_GREEN);
            run(-1, PoweredUpHubRemote.COLOR_BLUE);
        }
    }

    private static void run(int direction, int color) {
        PoweredUpHubRemote.setLedColor(color);
        for (int power = 10; power <= 60; power += 10) {
            PoweredUpHubRemote.setMotorPower(MOTOR, direction * power);
            Delay.millis(200);
        }
        Delay.millis(3000);
        PoweredUpHubRemote.brakeMotor(MOTOR);
        PoweredUpHubRemote.setLedColor(PoweredUpHubRemote.COLOR_RED);
        Delay.millis(2000);
    }
}
```

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.lego.LegoTrain \
  -Djuno.board=arduino-uno-r4-wifi   # or arduino-uno-q
```

Switch the hub on after the upload; the sketch connects to it and prints the hub type on the
serial monitor (`juno:monitor`).
