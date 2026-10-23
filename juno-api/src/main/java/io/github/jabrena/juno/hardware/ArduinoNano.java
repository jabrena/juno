package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.internal.NativeBoardMemory;
import io.github.jabrena.juno.hardware.internal.NativeBoardModel;
import io.github.jabrena.juno.hardware.internal.NativeClock;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * The Arduino Nano ESP32's features: its {@link Gpio} pins, primary {@link I2c} bus, onboard {@link Wifi},
 * {@link Clock}, board identity ({@link BoardModel}), and arena and flash figures ({@link BoardMemory}). Each getter
 * returns the same instance every call. Get them once, at startup, and pass them to the classes that need them; a unit
 * test can pass a mock of the corresponding interface instead.
 */
public final class ArduinoNano {
    private ArduinoNano() {
    }

    /** The Nano ESP32's pins, using the pin numbers printed on the board. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /** The Nano ESP32's primary I2C bus ({@code Wire}, the SDA/SCL pins). */
    public static I2c getI2c() {
        return NativeI2c.i2c();
    }

    /** The Nano ESP32's onboard Wi-Fi radio. */
    public static Wifi getWifi() {
        return NativeWifi.wifi();
    }

    /** The Nano ESP32's monotonic clock. */
    public static Clock getClock() {
        return NativeClock.clock();
    }

    /** Which board the program was compiled for. */
    public static BoardModel getBoardModel() {
        return NativeBoardModel.boardModel();
    }

    /** Juno's arena usage and the program's flash footprint on the Nano ESP32. */
    public static BoardMemory getBoardMemory() {
        return NativeBoardMemory.boardMemory();
    }
}
