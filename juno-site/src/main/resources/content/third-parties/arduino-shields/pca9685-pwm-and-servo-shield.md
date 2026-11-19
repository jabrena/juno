---
title: "PCA9685 PWM and Servo Shield"
description: "PCA9685 PWM and Servo Shield: a 16-channel servo driver over I2C, driven by PwmServoShield. Supported on the UNO R4 WiFi."
layout: page
---

{|
A 16-channel, 12-bit PWM board built on an NXP PCA9685 and driven over I2C. The chip generates the servo signals itself, so the board's pins stay free. Servos draw their power from the green terminal block (5 to 6 V), not from the board. The shield answers at address `0x40`; pass another address to the constructor if its address pads are bridged.

<img src="../../../images/shields/shield-pwm-servo.png" alt="PCA9685 PWM and Servo Shield" width="280">

## API

| Method | What it does |
|---|---|
| `PwmServoShield(address)` | Creates the shield handle at an I2C address. `ADDRESS_DEFAULT` is `0x40`. |
| `write(channel, degrees)` | Sets a servo's angle, 0 to 180 degrees, the same range as the Arduino `Servo` library. |
| `writeMicros(channel, micros)` | Sets a raw pulse width. |
| `release(channel)` | Stops driving a servo, so it no longer holds its position. |
| `CHANNELS`, `MIN_ANGLE`, `MAX_ANGLE` | The channel count (16) and the angle range (0 to 180). |

## Example

```java
import io.github.jabrena.juno.hardware.device.servo.PwmServoShield;
import io.github.jabrena.juno.util.Delay;

public final class ServoSweep {
    public static void main(String[] args) {
        PwmServoShield shield = new PwmServoShield(PwmServoShield.ADDRESS_DEFAULT);

        while (true) {
            for (int angle = PwmServoShield.MIN_ANGLE; angle <= PwmServoShield.MAX_ANGLE; angle += 5) {
                shield.write(0, angle);
                Delay.millis(20);
            }
            for (int angle = PwmServoShield.MAX_ANGLE; angle >= PwmServoShield.MIN_ANGLE; angle -= 5) {
                shield.write(0, angle);
                Delay.millis(20);
            }
        }
    }
}
```

[`PwmServoShieldDemo`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/servo/PwmServoShieldDemo.java) sweeps servos on several channels, with the wiring in its class comment.
|}
