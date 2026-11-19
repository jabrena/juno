---
title: "BMI088 accelerometer and gyroscope"
description: "BMI088 accelerometer and gyroscope: how Juno reads it, with its driver API and an example."
layout: page
---

{|
<img src="../../../images/grove/grove-bmi088.jpg" alt="Grove 6-Axis Accelerometer and Gyroscope (BMI088)" width="240">

## API

| Method | What it does |
|---|---|
| `Bmi088(i2c, clock)` | Creates the driver on the socket's I2C bus, with the board's clock. |
| `reset()`, `recalibrateOffset()` | Resets the sensor, or recalibrates its offset. |
| `getXAccel()`, `getYAccel()`, `getZAccel()` | Acceleration on each axis. |
| `getAngle()`, `getAngularVelocity()` | Integrated heading, and angular velocity. |
| `startBuffering()`, `readBufferedRotation()`, `bufferedSamples()`, `samplePeriodMillis()` | Background sampling into a buffer, and reading it back. |
| `sampleSize()`, `fetchSample(sample, offset)` | Copies one sample into an array the program owns. |

## Example

```java
import io.github.jabrena.juno.hardware.device.imu.Bmi088;

Bmi088 imu = new Bmi088();
float[] acceleration = new float[imu.sampleSize()];
imu.recalibrateOffset();
imu.fetchSample(acceleration, 0);
int heading = imu.getAngle();
```
|}
