package io.github.jabrena.juno.api.io;

/**
 * A zero-cost GPIO handle. Juno represents instances as their integer pin number, so no heap
 * allocation or object header remains in the generated program.
 */
public final class DigitalOutput {
    private DigitalOutput() {
    }

    public static native DigitalOutput of(int pin);

    public native void high();

    public native void low();

    public native void toggle();

    public native boolean isHigh();
}
