package io.github.jabrena.juno.api.hardware;

import io.github.jabrena.juno.api.hardware.internal.NativeBoardMemory;
import io.github.jabrena.juno.api.hardware.internal.NativeBoardModel;
import io.github.jabrena.juno.api.hardware.internal.NativeClock;
import io.github.jabrena.juno.api.hardware.internal.NativeGpio;
import io.github.jabrena.juno.api.hardware.internal.NativeI2c;
import io.github.jabrena.juno.api.hardware.internal.NativeMouse;
import io.github.jabrena.juno.api.hid.Mouse;
import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.time.Clock;

/**
 * The board's microcontroller unit (MCU): its {@link Gpio} pins, its {@link I2c} bus, its USB {@link Mouse}, its
 * {@link Clock}, which board it is ({@link BoardModel}) and its arena and flash figures ({@link BoardMemory}). Each
 * getter returns the same instance every call, backed by the compiler's intrinsics. Get them once, at startup, and pass
 * them to the classes that need them; a unit test passes {@code mock(Gpio.class)}, {@code mock(I2c.class)},
 * {@code mock(Mouse.class)}, {@code mock(Clock.class)}, {@code mock(BoardModel.class)} or
 * {@code mock(BoardMemory.class)} instead.
 */
public final class MCU {
    private MCU() {
    }

    /** The board's pins. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /** The board's primary I2C bus ({@code Wire}, the SDA/SCL pins). */
    public static I2c getI2c() {
        return NativeI2c.i2c();
    }

    /** The board acting as a USB HID mouse; only boards with native USB (UNO R4 WiFi) support it. */
    public static Mouse getMouse() {
        return NativeMouse.mouse();
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
