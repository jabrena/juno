package io.github.jabrena.juno.hardware.device.grove;

import io.github.jabrena.juno.hardware.gpio.Gpio;

/**
 * Pin map for the passive Grove Base Shield for UNO. The shield does not add a protocol or a
 * runtime object: every socket is an ordinary UNO pin, so these resolved pins can be passed directly
 * to a Grove driver or to {@link Gpio}.
 *
 * <p>Use {@link #getD4()}, for example, for the {@code SIG} wire of a Grove DHT module plugged into
 * the shield's D4 socket. The module's {@code GND} and {@code VCC} wires are supplied by the
 * socket; the driver only needs the signal pin.
 */
public final class GroveBaseShield {
    private final int a0;
    private final int a1;
    private final int a2;
    private final int a3;
    private final int d2;
    private final int d3;
    private final int d4;
    private final int d5;
    private final int d6;
    private final int d7;
    private final int d8;
    private final int uartRx;
    private final int uartTx;
    private final int i2cSda;
    private final int i2cScl;

    /** Creates the shield handle for a board GPIO context. Create it once, at startup, and reuse it. */
    public GroveBaseShield(Gpio gpio) {
        a0 = gpio.analogPin("A0");
        a1 = gpio.analogPin("A1");
        a2 = gpio.analogPin("A2");
        a3 = gpio.analogPin("A3");
        d2 = gpio.digitalPin("D2");
        d3 = gpio.digitalPin("D3");
        d4 = gpio.digitalPin("D4");
        d5 = gpio.digitalPin("D5");
        d6 = gpio.digitalPin("D6");
        d7 = gpio.digitalPin("D7");
        d8 = gpio.digitalPin("D8");
        uartRx = gpio.digitalPin("D0");
        uartTx = gpio.digitalPin("D1");
        i2cSda = gpio.analogPin("A4");
        i2cScl = gpio.analogPin("A5");
    }

    /** The UNO pin behind the A0 socket. */
    public int getA0() {
        return a0;
    }

    /** The UNO pin behind the A1 socket. */
    public int getA1() {
        return a1;
    }

    /** The UNO pin behind the A2 socket. */
    public int getA2() {
        return a2;
    }

    /** The UNO pin behind the A3 socket. */
    public int getA3() {
        return a3;
    }

    /** The UNO pin behind the D2 socket. */
    public int getD2() {
        return d2;
    }

    /** The UNO pin behind the D3 socket. */
    public int getD3() {
        return d3;
    }

    /** The UNO pin behind the D4 socket. */
    public int getD4() {
        return d4;
    }

    /** The UNO pin behind the D5 socket. */
    public int getD5() {
        return d5;
    }

    /** The UNO pin behind the D6 socket. */
    public int getD6() {
        return d6;
    }

    /** The UNO pin behind the D7 socket. */
    public int getD7() {
        return d7;
    }

    /** The UNO pin behind the D8 socket. */
    public int getD8() {
        return d8;
    }

    /** The UNO pin behind the UART_RX socket. */
    public int getUartRx() {
        return uartRx;
    }

    /** The UNO pin behind the UART_TX socket. */
    public int getUartTx() {
        return uartTx;
    }

    /** The UNO pin behind the I2C_SDA socket. */
    public int getI2cSda() {
        return i2cSda;
    }

    /** The UNO pin behind the I2C_SCL socket. */
    public int getI2cScl() {
        return i2cScl;
    }
}
