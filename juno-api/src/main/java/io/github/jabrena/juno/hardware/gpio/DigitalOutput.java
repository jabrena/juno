package io.github.jabrena.juno.hardware.gpio;

import io.github.jabrena.juno.hardware.internal.NativeDigitalOutput;
/**
 * A zero-cost GPIO handle. Juno represents instances as their integer pin number, so no heap
 * allocation or object header remains in the generated program. The operations are GPIO intrinsics
 * the compiler lowers to the Arduino core calls, keyed on this interface; {@link #of(int)} is the
 * board's {@link NativeDigitalOutput}, which hands out the handle.
 */
public interface DigitalOutput {

    static DigitalOutput of(int pin) {
        return NativeDigitalOutput.of(pin);
    }

    /** Resolves a board-specific name through {@code gpio} before creating the zero-cost output handle. */
    static DigitalOutput of(Gpio gpio, String pinName) {
        return of(gpio.digitalPin(pinName));
    }

    /** Drives the pin high. */
    void high();

    /** Drives the pin low. */
    void low();

    /** Inverts the level the pin is driven to. */
    void toggle();

    /** Returns whether the pin is driven high. */
    boolean isHigh();
}
