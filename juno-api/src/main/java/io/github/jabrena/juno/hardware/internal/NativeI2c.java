package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.i2c.I2c;

/**
 * The board's {@link I2c}: each method is an I2C intrinsic the compiler lowers to the core-bundled {@code Wire}
 * library, so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link I2c} instead.
 */
public final class NativeI2c implements I2c {
    /** The board's primary I2C bus: the only instance. */
    public static final NativeI2c INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.MCU#getI2c()} returns: the board's bus, unless a whole-program
     * emulator in a test replaced it.
     */
    private static I2c i2c;

    static {
        INSTANCE = new NativeI2c();
        i2c = INSTANCE;
    }

    private NativeI2c() {
    }

    /** The bus {@link io.github.jabrena.juno.hardware.MCU#getI2c()} hands out. */
    public static I2c i2c() {
        return i2c;
    }

    /**
     * Makes {@link io.github.jabrena.juno.hardware.MCU#getI2c()} hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setI2c(I2c replacement) {
        i2c = replacement;
    }

    @Override
    public native void begin();

    @Override
    public native void writeRegister(int address, int register, int value);

    @Override
    public native int readRegister(int address, int register);

    @Override
    public native int readRegister16(int address, int register);
}
