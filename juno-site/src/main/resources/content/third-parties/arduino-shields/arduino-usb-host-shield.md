---
title: "Arduino USB Host Shield"
description: "Arduino USB Host Shield: a MAX3421E USB host controller that enumerates one device, driven by UsbHostShield."
layout: page
---

{|
A Maxim MAX3421E USB host controller with a USB-A socket. `UsbHostShield` drives it over SPI as plain Java, with no Arduino library: it enumerates one full- or low-speed device plugged straight into the socket (no hubs) and offers control and bulk transfers. The shield takes SPI from the ICSP header and selects the chip with D10; D9, its interrupt line, stays free because the driver polls.

Two drivers build on it. `Cp210xSerial` opens a Silicon Labs CP210x USB-to-UART bridge (baud rate, 8N1, DTR and RTS), and `RplidarA2` reads a Slamtec RPLIDAR A2 (A2M8) through that bridge, one revolution at a time into a 360-entry array of distances in millimetres.

<img src="../../../images/shields/shield-usb-host.png" alt="Arduino USB Host Shield" width="280">

## API

| Method | What it does |
|---|---|
| `begin()` | Starts the bus, resets the controller and puts it in host mode. |
| `attach()` | Waits for the bus to settle and enumerates the connected device. |
| `isAttached()`, `isLowSpeed()` | Whether a device enumerated, and whether it is low speed (1.5 Mbit/s) or full speed. |
| `vendorId()`, `productId()` | The device's USB vendor and product IDs. |
| `setConfiguration(value)` | Selects a configuration of the device. |
| `controlIn(...)`, `controlOut(...)` | Control transfers, with setup, data and status stages. |
| `bulkIn(...)`, `bulkOut(...)` | Bulk transfers on one endpoint. |

## Example

```java
import io.github.jabrena.juno.hardware.device.lidar.RplidarA2;
import io.github.jabrena.juno.hardware.device.usb.Cp210xSerial;
import io.github.jabrena.juno.hardware.device.usb.UsbHostShield;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

public final class LidarExample {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        UsbHostShield usb = new UsbHostShield();
        RplidarA2 lidar = new RplidarA2(new Cp210xSerial(usb));
        int[] scan = new int[RplidarA2.DEGREES];   // mm per whole degree, 0 = no return

        if (usb.begin() && lidar.begin() && lidar.startMotor() && lidar.startScan()) {
            while (true) {
                if (lidar.poll(scan)) {
                    Serial.print("front=");
                    Serial.println(scan[0]);
                }
            }
        }
    }
}
```

`startMotor()` sends the A2's `SET_MOTOR_PWM` command: without it the head stands still, and a scan delivers no measurements. Call `poll` continuously while scanning, since the bridge buffers only half a second of data. [`UsbHostRplidarA2Scan`](https://github.com/jabrena/juno/blob/main/juno-examples/src/main/java/io/github/jabrena/juno/api/hardware/device/lidar/UsbHostRplidarA2Scan.java) prints a summary every ten revolutions and reconnects when the lidar is unplugged.
|}
