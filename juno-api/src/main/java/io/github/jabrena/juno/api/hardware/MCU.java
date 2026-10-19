package io.github.jabrena.juno.api.hardware;

import io.github.jabrena.juno.api.hardware.internal.NativeClock;
import io.github.jabrena.juno.api.hardware.internal.NativeGpio;
import io.github.jabrena.juno.api.hardware.internal.NativeI2c;
import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.time.Clock;

/**
 * The board's microcontroller unit (MCU): its {@link Gpio} pins, its {@link I2c} bus and its {@link Clock}. Each getter
 * returns the same instance every call, backed by the compiler's intrinsics. Get them once, at startup, and pass them to
 * the classes that need them; a unit test passes {@code mock(Gpio.class)}, {@code mock(I2c.class)} or
 * {@code mock(Clock.class)} instead.
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

    /** The board's monotonic clock. */
    public static Clock getClock() {
        return NativeClock.clock();
    }
}
