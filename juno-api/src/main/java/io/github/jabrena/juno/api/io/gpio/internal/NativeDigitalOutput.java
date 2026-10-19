package io.github.jabrena.juno.api.io.gpio.internal;

import io.github.jabrena.juno.api.io.gpio.DigitalOutput;

/**
 * The board's {@link DigitalOutput} factory: {@link #of} is a GPIO intrinsic the compiler lowers to a
 * pin mode and returns the pin number as the handle. It has no Java body and cannot run on a desktop JVM.
 */
public final class NativeDigitalOutput {
    private NativeDigitalOutput() {
    }

    public static native DigitalOutput of(int pin);
}
