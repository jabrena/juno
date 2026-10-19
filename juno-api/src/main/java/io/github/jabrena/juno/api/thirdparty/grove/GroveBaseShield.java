package io.github.jabrena.juno.api.thirdparty.grove;

import io.github.jabrena.juno.api.io.gpio.Gpio;

/**
 * Pin map for the passive Grove Base Shield for UNO. The shield does not add a protocol or a
 * runtime object: every socket is an ordinary UNO pin, so these constants can be passed directly
 * to a Grove driver or to {@link Gpio}.
 *
 * <p>Use {@link #D4}, for example, for the {@code SIG} wire of a Grove DHT module plugged into
 * the shield's D4 socket. The module's {@code GND} and {@code VCC} wires are supplied by the
 * socket; the driver only needs the signal pin.
 */
public final class GroveBaseShield {
    /** Analog sockets A0 through A3. */
    private static final int A0 = Gpio.getA0();
    private static final int A1 = Gpio.getA1();
    private static final int A2 = Gpio.getA2();
    private static final int A3 = Gpio.getA3();

    /** Digital sockets D2 through D8. */
    private static final int D2 = Gpio.getD2();
    private static final int D3 = Gpio.getD3();
    private static final int D4 = Gpio.getD4();
    private static final int D5 = Gpio.getD5();
    private static final int D6 = Gpio.getD6();
    private static final int D7 = Gpio.getD7();
    private static final int D8 = Gpio.getD8();

    /** The UART socket's receive and transmit pins. */
    private static final int UART_RX = Gpio.getD0();
    private static final int UART_TX = Gpio.getD1();

    /** The I2C sockets' shared bus pins. */
    private static final int I2C_SDA = Gpio.getA4();
    private static final int I2C_SCL = Gpio.getA5();

    public GroveBaseShield() {
    }

    /** The UNO pin behind the A0 socket. */
    public int getA0() {
        return A0;
    }

    /** The UNO pin behind the A1 socket. */
    public int getA1() {
        return A1;
    }

    /** The UNO pin behind the A2 socket. */
    public int getA2() {
        return A2;
    }

    /** The UNO pin behind the A3 socket. */
    public int getA3() {
        return A3;
    }

    /** The UNO pin behind the D2 socket. */
    public int getD2() {
        return D2;
    }

    /** The UNO pin behind the D3 socket. */
    public int getD3() {
        return D3;
    }

    /** The UNO pin behind the D4 socket. */
    public int getD4() {
        return D4;
    }

    /** The UNO pin behind the D5 socket. */
    public int getD5() {
        return D5;
    }

    /** The UNO pin behind the D6 socket. */
    public int getD6() {
        return D6;
    }

    /** The UNO pin behind the D7 socket. */
    public int getD7() {
        return D7;
    }

    /** The UNO pin behind the D8 socket. */
    public int getD8() {
        return D8;
    }

    /** The UNO pin behind the UART_RX socket. */
    public int getUartRx() {
        return UART_RX;
    }

    /** The UNO pin behind the UART_TX socket. */
    public int getUartTx() {
        return UART_TX;
    }

    /** The UNO pin behind the I2C_SDA socket. */
    public int getI2cSda() {
        return I2C_SDA;
    }

    /** The UNO pin behind the I2C_SCL socket. */
    public int getI2cScl() {
        return I2C_SCL;
    }
}
