---
title: "Arduino 9 Axis Motion Shield"
description: "Arduino 9 Axis Motion Shield: a Bosch BNO055 that reports heading, angular velocity and acceleration over I2C, driven by NineAxisMotionShield."
layout: page
---

{|
A Bosch BNO055 that fuses its accelerometer and gyroscope and reports the heading in degrees. `NineAxisMotionShield` reads it over I2C with no Arduino library, which suits measuring how far a vehicle has turned. It holds D7 as the sensor's reset line.

<img src="../../../images/shields/shield-nine-axis.png" alt="Arduino 9 Axis Motion Shield" width="280">

## API

| Method | What it does |
|---|---|
| `NineAxisMotionShield()` | Creates the shield on the board's own pins and initializes the sensor. |
| `getAngle()` | Heading, in degrees. |
| `getAngularVelocity()` | Angular velocity (yaw rate). |
| `getXAccel()`, `getYAccel()`, `getZAccel()` | Acceleration on each axis. |
| `fetchSample(float[] sample, offset)` | Copies one sample into an array the program owns, with `sampleSize()` giving its length. |
| `reset()` | Resets the sensor. |
| `recalibrateOffset()` | Recalibrates the sensor's offset. |

## Example

```java
import io.github.jabrena.juno.hardware.device.imu.NineAxisMotionShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

public final class NineAxisExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        NineAxisMotionShield imu = new NineAxisMotionShield();
        imu.reset();

        while (true) {
            Serial.print("heading=");
            Serial.print(imu.getAngle());
            Serial.print(" yaw=");
            Serial.print((int) imu.getAngularVelocity());
            Serial.print(" ax=");
            Serial.print(imu.getXAccel());
            Serial.print(" ay=");
            Serial.print(imu.getYAccel());
            Serial.print(" az=");
            Serial.println(imu.getZAccel());
            Delay.millis(200);
        }
    }
}
```

The same monitor with startup diagnostics is [`NineAxisMotionShieldSerial`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/imu/NineAxisMotionShieldSerial.java).
|}
