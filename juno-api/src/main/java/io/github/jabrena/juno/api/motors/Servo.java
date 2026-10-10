package io.github.jabrena.juno.api.motors;

/**
 * A zero-cost servo motor handle for a standard 3-wire hobby servo (signal, power, ground),
 * backed by the Arduino {@code Servo} library. Juno represents instances as their integer pin
 * number, so no heap allocation or object header remains in the generated program.
 *
 * <p>{@link #write(int)} takes an angle in degrees, {@code 0..180}, matching the Arduino {@code
 * Servo} library's own range. A servo whose physical travel exceeds 180 degrees (e.g. a 270
 * degree servo) can only be commanded across its first 180 degrees through this API; the
 * remaining travel is unreachable with {@code write}.
 */
public final class Servo {
    private Servo() {
    }

    public static native Servo of(int pin);

    public native void write(int angleDegrees);
}
