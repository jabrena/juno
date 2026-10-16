package io.github.jabrena.juno.api.thirdparty.grove;

import io.github.jabrena.juno.api.io.Gpio;

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
    public static final int A0 = Gpio.A0;
    public static final int A1 = Gpio.A1;
    public static final int A2 = Gpio.A2;
    public static final int A3 = Gpio.A3;

    /** Digital sockets D2 through D8. */
    public static final int D2 = Gpio.D2;
    public static final int D3 = Gpio.D3;
    public static final int D4 = Gpio.D4;
    public static final int D5 = Gpio.D5;
    public static final int D6 = Gpio.D6;
    public static final int D7 = Gpio.D7;
    public static final int D8 = Gpio.D8;

    /** The UART socket's receive and transmit pins. */
    public static final int UART_RX = Gpio.D0;
    public static final int UART_TX = Gpio.D1;

    /** The I2C sockets' shared bus pins. */
    public static final int I2C_SDA = Gpio.A4;
    public static final int I2C_SCL = Gpio.A5;

    private GroveBaseShield() {
    }
}
