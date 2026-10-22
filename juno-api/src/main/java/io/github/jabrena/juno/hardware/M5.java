package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeGpio;

/**
 * The M5Stack Cardputer's built-in hardware. Each getter returns the same instance every call, backed by the
 * compiler's intrinsics. Get each device once at startup and pass it to the classes that need it; a unit test passes a
 * mock of the device interface instead.
 */
public final class M5 {
    private M5() {
    }

    /** The Cardputer's GPIO pins, including GPIO1 and GPIO2 on its HY2.0-4P port. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }
}
