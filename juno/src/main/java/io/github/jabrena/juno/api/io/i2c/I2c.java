package io.github.jabrena.juno.api.io.i2c;

/**
 * Register access to an I2C peripheral on the board's primary I2C bus ({@code Wire}, the SDA/SCL
 * pins), recognized as compiler intrinsics by Juno and backed by the core-bundled {@code Wire}
 * library. It is the transport under sensors such as {@link io.github.jabrena.juno.api.imu.Bno055}.
 * Addresses are the 7-bit device addresses.
 */
public final class I2c {
    private I2c() {
    }

    /** Starts the bus as a controller, at the default 100 kHz. */
    public static native void begin();

    /** Writes {@code value} (low 8 bits) to {@code register} of the device at {@code address}. */
    public static native void writeRegister(int address, int register, int value);

    /** Reads one byte, {@code 0..255}, from {@code register}, or {@code -1} if the device did not answer. */
    public static native int readRegister(int address, int register);

    /**
     * Reads two bytes starting at {@code register} as a little-endian value, {@code 0..65535}, or
     * {@code -1} if the device did not answer.
     */
    public static native int readRegister16(int address, int register);
}
