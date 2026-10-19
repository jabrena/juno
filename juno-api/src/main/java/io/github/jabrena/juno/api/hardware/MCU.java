package io.github.jabrena.juno.api.hardware;

import io.github.jabrena.juno.api.hardware.internal.NativeClock;
import io.github.jabrena.juno.api.hardware.internal.NativeGpio;
import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.time.Clock;

/**
 * The board's microcontroller unit (MCU): its {@link Gpio} pins and its {@link Clock}. Each getter returns the same instance every
 * call, backed by the compiler's intrinsics. Get them once, at startup, and pass them to the classes that need them;
 * a unit test passes {@code mock(Gpio.class)} or {@code mock(Clock.class)} instead.
 */
public final class MCU {
    private MCU() {
    }

    /** The board's pins. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /** The board's monotonic clock. */
    public static Clock getClock() {
        return NativeClock.clock();
    }
}
