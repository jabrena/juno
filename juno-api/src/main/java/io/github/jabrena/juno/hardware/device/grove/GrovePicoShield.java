package io.github.jabrena.juno.hardware.device.grove;

import io.github.jabrena.juno.hardware.gpio.Gpio;

/**
 * Pin map for Seeed's passive <a href="https://www.seeedstudio.com/Grove-Shield-for-Pi-Pico-v1-0-p-4846.html">Grove
 * Shield for Pi Pico v1.0</a> on a Raspberry Pi Pico 2 W. The shield adds no protocol or runtime object: every socket is
 * an ordinary Pico pin, so these resolved pins can be passed directly to a Grove driver or to {@link Gpio}. Each getter
 * returns the socket's primary {@code SIG} pin.
 *
 * <p>The digital sockets carry the GPIO number in their silkscreen label ({@code D16} is GP16), and the analog sockets
 * {@code A0}..{@code A2} are the Pico's ADC pins GP26..GP28, read with {@code Gpio.analogRead}. The I2C socket pins are
 * exposed for drivers that wire their own bus; Juno's {@code I2c} uses the core's default {@code Wire} pins. The UART
 * and SPI sockets are not mapped yet.
 */
public final class GrovePicoShield {
    private final int a0;
    private final int a1;
    private final int a2;
    private final int d16;
    private final int d18;
    private final int d20;
    private final int i2c0Sda;
    private final int i2c0Scl;
    private final int i2c1Sda;
    private final int i2c1Scl;

    /** Creates the shield handle for the Pico's GPIO context. Create it once, at startup, and reuse it. */
    public GrovePicoShield(Gpio gpio) {
        a0 = gpio.analogPin("A0");
        a1 = gpio.analogPin("A1");
        a2 = gpio.analogPin("A2");
        d16 = gpio.digitalPin("GP16");
        d18 = gpio.digitalPin("GP18");
        d20 = gpio.digitalPin("GP20");
        i2c0Sda = gpio.digitalPin("GP8");
        i2c0Scl = gpio.digitalPin("GP9");
        i2c1Sda = gpio.digitalPin("GP6");
        i2c1Scl = gpio.digitalPin("GP7");
    }

    /** The Pico pin behind the A0 socket: GP26, ADC0. */
    public int getA0() {
        return a0;
    }

    /** The Pico pin behind the A1 socket: GP27, ADC1. */
    public int getA1() {
        return a1;
    }

    /** The Pico pin behind the A2 socket: GP28, ADC2. */
    public int getA2() {
        return a2;
    }

    /** The Pico pin behind the D16 socket: GP16. */
    public int getD16() {
        return d16;
    }

    /** The Pico pin behind the D18 socket: GP18. */
    public int getD18() {
        return d18;
    }

    /** The Pico pin behind the D20 socket: GP20. */
    public int getD20() {
        return d20;
    }

    /** The SDA pin of the I2C0 socket: GP8. */
    public int getI2c0Sda() {
        return i2c0Sda;
    }

    /** The SCL pin of the I2C0 socket: GP9. */
    public int getI2c0Scl() {
        return i2c0Scl;
    }

    /** The SDA pin of the I2C1 socket: GP6. */
    public int getI2c1Sda() {
        return i2c1Sda;
    }

    /** The SCL pin of the I2C1 socket: GP7. */
    public int getI2c1Scl() {
        return i2c1Scl;
    }
}
