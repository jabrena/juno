---
title: "I2C Motor Driver (TB6612FNG)"
description: "Grove I2C Motor Driver (TB6612FNG): two DC motor channels over I2C, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-motor-driver-tb6612fng.png" alt="Grove I2C Motor Driver (TB6612FNG)" width="240">

## API

| Method | What it does |
|---|---|
| `GroveMotorDriverTb6612fng(i2c)` | Creates the driver on the socket's I2C bus. The address is `ADDRESS_DEFAULT` (`0x14`). |
| `getMotorA()`, `getMotorB()` | The two channels, each as a `DCMotor`. |
| `setPower(power)`, `getPower()` | Sets or reads the power of a motor, as a percentage from 0 to 100. |
| `forward()`, `backward()` | Sets the direction of a motor. |
| `stop()`, `flt()` | Brakes a motor, or lets it coast. |

## Example

```java
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.motor.GroveMotorDriverTb6612fng;
import io.github.jabrena.juno.robotics.DCMotor;
import io.github.jabrena.juno.util.Delay;

public final class GroveMotorDriverExample {
    public static void main(String[] args) {
        GroveMotorDriverTb6612fng driver = new GroveMotorDriverTb6612fng(MCU.getI2c());
        DCMotor left = driver.getMotorA();
        DCMotor right = driver.getMotorB();
        left.setPower(60);
        right.setPower(60);

        while (true) {
            left.forward();
            right.forward();
            Delay.millis(2000);
            left.stop();
            right.stop();
            Delay.millis(1000);
        }
    }
}
```
|}
