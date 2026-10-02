---
title: "LEGO Powered Up"
description: "Driving LEGO Powered Up motors and hub LEDs over Bluetooth LE."
layout: page
---

[`PoweredUpHub`](https://github.com/jabrena/juno/blob/main/juno/src/main/java/io/github/jabrena/juno/api/lego/PoweredUpHub.java)
(`io.github.jabrena.juno.api.lego`) lets a Juno program remote-control a LEGO Powered Up hub — run
its motors and set its status LED — the same way the LEGO apps do. The board acts as a Bluetooth
Low Energy central and speaks the
[LEGO Wireless Protocol 3.0](https://lego.github.io/lego-ble-wireless-protocol-docs/) to the hub.
It is a compiler intrinsic backed by the Arduino `ArduinoBLE` library.

## Requirements

- `@Board(ArduinoUnoR4WiFi.class)`. On the UNO R4 WiFi, BLE is provided by the ESP32-S3 radio
  module; Juno rejects `PoweredUpHub` at compile time on boards without a BLE radio it can reach
  (the UNO Q's radio belongs to its Linux side).
- The Arduino `ArduinoBLE` library installed (also done by `juno:install-deps`):

  ```bash
  arduino-cli lib install ArduinoBLE
  ```

- A Powered Up hub: City Hub (88009), Technic Hub (88012), BOOST Move Hub (88006) or the DUPLO
  Train Hub. Close the LEGO apps first — a hub accepts a single connection at a time.

The UNO R4 WiFi's radio module cannot run Bluetooth LE and Wi-Fi at the same time, so a program
that uses `PoweredUpHub` should not also use `Wifi`, `Udp`, or the HTTP/email APIs.

## API

```java
import io.github.jabrena.juno.api.lego.PoweredUpHub;

if (PoweredUpHub.connect(10_000)) {              // scan up to 10 s; 0 waits forever
    PoweredUpHub.setLedColor(PoweredUpHub.COLOR_GREEN);
    PoweredUpHub.setMotorPower(PoweredUpHub.PORT_A, 50);   // -100..100 percent
    PoweredUpHub.brakeMotor(PoweredUpHub.PORT_A);
}
```

| Method | Meaning |
| --- | --- |
| `connect(timeoutMillis)` | Scans for the first advertising hub (press its green button) and connects. Returns `true` once connected, or immediately if already connected. A timeout of `0` or less waits indefinitely. |
| `isConnected()` | `false` once the hub switches off or goes out of range; call `connect` again to reconnect. |
| `hubType()` | The hub's advertised system type: `TYPE_CITY_HUB`, `TYPE_TECHNIC_HUB`, `TYPE_MOVE_HUB`, `TYPE_DUPLO_TRAIN_HUB`, ... (`TYPE_UNKNOWN` before connecting). |
| `setMotorPower(port, percent)` | Runs any Powered Up motor (train, simple, or tacho) on `PORT_A`..`PORT_D`. Negative values reverse, `0` coasts, values beyond ±100 are clamped. |
| `brakeMotor(port)` | Actively brakes the motor, instead of letting it coast. |
| `setLedColor(color)` | Sets the hub's LED to a LEGO color index: `COLOR_OFF`, `COLOR_PINK`, ..., `COLOR_RED`, `COLOR_WHITE`. |
| `disconnect()` | Drops the connection; the hub stays on and starts advertising again. |
| `switchOff()` | Switches the hub off. |

A program talks to one hub at a time. Commands sent while no hub is connected are ignored, so
check `isConnected()` in long-running loops and reconnect when it turns `false`.

## Example: a shuttling train

[`LegoTrain`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/lego/LegoTrain.java)
ramps a train motor on port A up to cruising speed, brakes, and repeats in reverse, using the hub's
LED to show the direction:

```java
@Board(ArduinoUnoR4WiFi.class)
public final class LegoTrain {
    private static final int MOTOR = PoweredUpHub.PORT_A;

    public static void main(String[] args) {
        Serial.begin(9600);
        while (true) {
            if (!PoweredUpHub.isConnected()) {
                Serial.println("Waiting for a Powered Up hub...");
                PoweredUpHub.connect(0);
            }
            run(1, PoweredUpHub.COLOR_GREEN);
            run(-1, PoweredUpHub.COLOR_BLUE);
        }
    }

    private static void run(int direction, int color) {
        PoweredUpHub.setLedColor(color);
        for (int power = 10; power <= 60; power += 10) {
            PoweredUpHub.setMotorPower(MOTOR, direction * power);
            Delay.millis(200);
        }
        Delay.millis(3000);
        PoweredUpHub.brakeMotor(MOTOR);
        PoweredUpHub.setLedColor(PoweredUpHub.COLOR_RED);
        Delay.millis(2000);
    }
}
```

```bash
./mvnw -f juno-examples/pom.xml compile juno:upload \
  -Djuno.main=io.github.jabrena.juno.api.lego.LegoTrain
```

Switch the hub on after the upload; the sketch connects to it and prints the hub type on the
serial monitor (`juno:monitor`).
