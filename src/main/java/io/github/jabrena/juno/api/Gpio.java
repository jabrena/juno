package io.github.jabrena.juno.api;

/** GPIO operations recognized as compiler intrinsics by Juno. */
public final class Gpio {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int INPUT_PULLUP = 2;

    private Gpio() {
    }

    public static native void pinMode(int pin, int mode);

    public static native void digitalWrite(int pin, boolean high);

    public static native boolean digitalRead(int pin);

    public static native int analogRead(int pin);

    public static native void analogWrite(int pin, int value);

    public static native void toggle(int pin);
}
