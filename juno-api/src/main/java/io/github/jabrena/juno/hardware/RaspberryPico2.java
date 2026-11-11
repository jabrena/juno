package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.internal.NativeBluetoothLe;
import io.github.jabrena.juno.hardware.internal.NativeClock;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.net.BluetoothLe;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * The Raspberry Pi Pico 2 W's board features. Its built-in LED sits on the CYW43439 radio chip rather than an RP2350
 * pin; {@link Gpio#getBuiltinLed()} returns the number the board's core routes to it.
 */
public final class RaspberryPico2 {

    private RaspberryPico2() {
    }

    /** The Raspberry Pi Pico 2 W's GPIO pins. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /**
     * The Raspberry Pi Pico 2 W's I2C bus: I2C0 on GP8 (SDA) and GP9 (SCL), the pins of the Grove Shield for Pi Pico's
     * I2C0 socket.
     */
    public static I2c getI2c() {
        return NativeI2c.i2c();
    }

    /** The Raspberry Pi Pico 2 W's onboard CYW43439 Wi-Fi radio, in station or access-point mode. */
    public static Wifi getWifi() {
        return NativeWifi.wifi();
    }

    /** The Raspberry Pi Pico 2 W's CYW43439 Bluetooth LE radio, used as a central. */
    public static BluetoothLe getBluetoothLe() {
        return NativeBluetoothLe.bluetoothLe();
    }

    /** The board's monotonic clock. */
    public static Clock getClock() {
        return NativeClock.clock();
    }
}
