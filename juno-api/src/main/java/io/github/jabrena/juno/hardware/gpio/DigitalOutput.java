package io.github.jabrena.juno.hardware.gpio;

import io.github.jabrena.juno.hardware.gpio.internal.NativeDigitalOutput;
/**
 * A zero-cost GPIO handle. Juno represents instances as their integer pin number, so no heap
 * allocation or object header remains in the generated program. The operations are GPIO intrinsics
 * the compiler lowers to the Arduino core calls, keyed on this interface; {@link #of} is the
 * board's {@link NativeDigitalOutput}, which hands out the handle.
 */
public interface DigitalOutput {

    static DigitalOutput of(int pin) {
        return NativeDigitalOutput.of(pin);
    }

    void high();

    void low();

    void toggle();

    boolean isHigh();
}
