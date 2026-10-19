package io.github.jabrena.juno.api.io.i2c;

/**
 * Register access to an I2C peripheral on the board's primary I2C bus ({@code Wire}, the SDA/SCL pins).
 * {@link io.github.jabrena.juno.api.hardware.MCU#getI2c()} is the board's own bus, which the compiler lowers to the
 * core-bundled {@code Wire} library; a unit test passes {@code mock(I2c.class)} instead. It is the transport under
 * sensors such as {@link io.github.jabrena.juno.api.thirdparty.imu.NineAxisMotionShield}. Addresses are the 7-bit
 * device addresses. Get the board's bus once, at startup, and pass it to the classes that need it.
 */
public interface I2c {

    /** Starts the bus as a controller, at the default 100 kHz. */
    void begin();

    /** Writes {@code value} (low 8 bits) to {@code register} of the device at {@code address}. */
    void writeRegister(int address, int register, int value);

    /** Reads one byte, {@code 0..255}, from {@code register}, or {@code -1} if the device did not answer. */
    int readRegister(int address, int register);

    /**
     * Reads two bytes starting at {@code register} as a little-endian value, {@code 0..65535}, or
     * {@code -1} if the device did not answer.
     */
    int readRegister16(int address, int register);
}
