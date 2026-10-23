package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.gpio.Gpio;

/**
 * The board's {@link Gpio}: each method is a GPIO intrinsic the compiler lowers to the Arduino core call, so it has no
 * Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link Gpio} instead.
 */
public final class NativeGpio implements Gpio {
    /** The board's pins: the only instance. */
    public static final NativeGpio INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.ArduinoOne#getGpio()} returns: the board's pins, unless a whole-program
     * emulator in a test replaced them.
     */
    private static Gpio gpio;

    static {
        INSTANCE = new NativeGpio();
        gpio = INSTANCE;
    }

    private NativeGpio() {
    }

    /** The pins {@link io.github.jabrena.juno.hardware.ArduinoOne#getGpio()} hands out. */
    public static Gpio gpio() {
        return gpio;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.ArduinoOne#getGpio()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setGpio(Gpio replacement) {
        gpio = replacement;
    }

    @Override
    public native void pinMode(int pin, int mode);

    @Override
    public native void digitalWrite(int pin, boolean high);

    @Override
    public native boolean digitalRead(int pin);

    @Override
    public native int analogRead(int pin);

    @Override
    public native void analogWrite(int pin, int value);

    @Override
    public native void toggle(int pin);

    @Override
    public native int getBuiltinLed();
}
