package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.device.imu.Lsm6ds3;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.hid.Mouse;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.imu.Imu;
import io.github.jabrena.juno.hardware.internal.NativeBoardMemory;
import io.github.jabrena.juno.hardware.internal.NativeBoardModel;
import io.github.jabrena.juno.hardware.internal.NativeClock;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import io.github.jabrena.juno.hardware.internal.NativeLedMatrix;
import io.github.jabrena.juno.hardware.internal.NativeMouse;
import io.github.jabrena.juno.hardware.internal.NativeSpi;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.hardware.time.Clock;
import org.jspecify.annotations.Nullable;

/**
 * The Arduino UNO board's microcontroller unit (MCU): its {@link Gpio} pins, its {@link I2c} bus, its {@link Spi} bus, its
 * USB {@link Mouse}, its built-in {@link LedMatrix}, its onboard {@link Wifi}, its onboard {@link Imu}, its {@link Clock},
 * which board it is ({@link BoardModel}) and its arena and flash figures ({@link BoardMemory}). Each getter returns the
 * same instance every call, backed by the compiler's intrinsics. Get them once, at startup, and pass them to the classes
 * that need them; a unit test passes {@code mock(Gpio.class)}, {@code mock(I2c.class)}, {@code mock(Spi.class)},
 * {@code mock(Mouse.class)}, {@code mock(LedMatrix.class)}, {@code mock(Wifi.class)}, {@code mock(Imu.class)},
 * {@code mock(Clock.class)}, {@code mock(BoardModel.class)} or {@code mock(BoardMemory.class)} instead.
 */
public final class ArduinoUno {
    private static @Nullable Imu imu;

    private ArduinoUno() {
    }

    /** The board's pins. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /** The board's primary I2C bus ({@code Wire}, the SDA/SCL pins). */
    public static I2c getI2c() {
        return NativeI2c.i2c();
    }

    /** The board's primary SPI bus (SCK, MOSI and MISO); a device's chip select is a {@link Gpio} pin the program drives. */
    public static Spi getSpi() {
        return NativeSpi.spi();
    }

    /**
     * The board's onboard inertial sensor: the LSM6DS3TR on the UNO WiFi Rev2, read over {@link #getSpi()} with its chip
     * select on the board's IMU pin. The same instance is returned every call. Only the Rev2 has one, so the compiler
     * rejects this call on the other boards.
     */
    public static Imu getImu() {
        Imu sensor = imu;
        if (sensor == null) {
            sensor = new Lsm6ds3(getGpio(), getSpi(), getClock());
            imu = sensor;
        }
        return sensor;
    }

    /**
     * The board acting as a USB HID mouse; only boards with native USB (UNO R4 WiFi) support it. The Nano ESP32's is
     * {@link ArduinoNano#getMouse()}.
     */
    public static Mouse getMouse() {
        return NativeMouse.mouse();
    }

    /** The board's built-in LED matrix; only the UNO R4 WiFi and the UNO Q have one. */
    public static LedMatrix getLedMatrix() {
        return NativeLedMatrix.ledMatrix();
    }

    /**
     * The Arduino UNO board's onboard Wi-Fi radio (UNO R4 WiFi, UNO WiFi Rev2, UNO Q). The UNO R3 has none, so the compiler
     * rejects this call there: stack the Arduino WiFi Shield and use
     * {@link io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield#getWifi()} instead.
     */
    public static Wifi getWifi() {
        return NativeWifi.wifi();
    }

    /** The board's monotonic clock. */
    public static Clock getClock() {
        return NativeClock.clock();
    }

    /** Which board the program was compiled for. */
    public static BoardModel getBoardModel() {
        return NativeBoardModel.boardModel();
    }

    /** Juno's arena usage and the program's flash footprint on this board. */
    public static BoardMemory getBoardMemory() {
        return NativeBoardMemory.boardMemory();
    }
}
