package io.github.jabrena.juno.hardware.device.grove;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.internal.NativeI2c;

/**
 * Pin map for Seeed's passive <a href="https://www.seeedstudio.com/Grove-Shield-for-Pi-Pico-v1-0-p-4846.html">Grove
 * Shield for Pi Pico v1.0</a> on a Raspberry Pi Pico 2 W. The shield adds no protocol or runtime object: every socket is
 * an ordinary Pico pin, so these resolved pins can be passed directly to a Grove driver or to {@link Gpio}. Each getter
 * returns the socket's primary {@code SIG} pin.
 *
 * <p>The digital sockets carry the GPIO number in their silkscreen label ({@code D16} is GP16), and the analog sockets
 * {@code A0}..{@code A2} are the Pico's ADC pins GP26..GP28, read with {@code Gpio.analogRead}. Plug I2C modules, such
 * as the Grove OLED display, into the I2C0 socket and pass {@link #getI2c0()}, the bus on its GP8/GP9, to their driver.
 * The I2C1 socket pins are exposed for drivers that wire their own bus. UART0, UART1 and SPI0 expose their signal
 * pins individually, so a serial or SPI driver can select the socket without duplicating the shield's pinout.
 *
 * <p>The shield seen from above, USB at the top, each socket's pins as printed under it. The first pin is the
 * {@code SIG} wire its getter returns. The analog sockets always take 3.3 V; the others take {@code VCC} from the
 * power switch.
 *
 * <pre>
 * +----------------------------[ USB ]-----------------------------+
 * | SPI0  GND o o SCK                              VCC switch:  5V |
 * |       3V3 o o TX                                           3V3 |
 * |       CS  o o RX                                               |
 * |                                                                |
 * | UART0  RX=GP1 TX=GP0                    I2C0  SCL=GP9  SDA=GP8 |
 * | [RX |TX |VCC|GND]                            [SCL|SDA|VCC|GND] |
 * | UART1  RX=GP5 TX=GP4                    I2C1  SCL=GP7  SDA=GP6 |
 * | [RX |TX |VCC|GND]        Raspberry Pi        [SCL|SDA|VCC|GND] |
 * | D16  SIG=GP16              Pico 2 W               A0  SIG=GP26 |
 * | [D16|D17|VCC|GND]        (sits here)         [A0 |NC |3V3|GND] |
 * | D18  SIG=GP18                                     A1  SIG=GP27 |
 * | [D18|D19|VCC|GND]                            [A1 |A0 |3V3|GND] |
 * | D20  SIG=GP20                                     A2  SIG=GP28 |
 * | [D20|D21|VCC|GND]                            [A2 |A1 |3V3|GND] |
 * +-------------------[ DEBUG: SWCLK GND SWDIO ]-------------------+
 *
 *   Socket  Getter                            Pico pin
 *   D16     getD16()                          GP16
 *   D18     getD18()                          GP18
 *   D20     getD20()                          GP20
 *   A0      getA0()                           GP26 (ADC0)
 *   A1      getA1()                           GP27 (ADC1)
 *   A2      getA2()                           GP28 (ADC2)
 *   I2C0    getI2c0() bus, getI2c0Sda/Scl()   GP8, GP9
 *   I2C1    getI2c1Sda(), getI2c1Scl()        GP6, GP7
 *   UART0    getUart0Rx()/getUart0Tx()        GP1, GP0
 *   UART1    getUart1Rx()/getUart1Tx()        GP5, GP4
 *   SPI0     getSpi0Miso()/getSpi0Mosi()      GP16, GP19
 *            getSpi0Sck()/getSpi0Cs()        GP18, GP17
 * </pre>
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
    private final int uart0Rx;
    private final int uart0Tx;
    private final int uart1Rx;
    private final int uart1Tx;
    private final int spi0Miso;
    private final int spi0Mosi;
    private final int spi0Sck;
    private final int spi0Cs;

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
        uart0Rx = gpio.digitalPin("GP1");
        uart0Tx = gpio.digitalPin("GP0");
        uart1Rx = gpio.digitalPin("GP5");
        uart1Tx = gpio.digitalPin("GP4");
        spi0Miso = gpio.digitalPin("GP16");
        spi0Mosi = gpio.digitalPin("GP19");
        spi0Sck = gpio.digitalPin("GP18");
        spi0Cs = gpio.digitalPin("GP17");
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

    /**
     * The I2C bus behind the I2C0 socket (SDA GP8, SCL GP9), to pass to a Grove I2C driver such as
     * {@code GroveOledSsd1315}. It is the Pico's {@code Wire} bus, the same instance {@code RaspberryPico2.getI2c()}
     * returns, on every call.
     */
    public I2c getI2c0() {
        return NativeI2c.i2c();
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

    /** The RX pin of the UART0 socket: GP1. */
    public int getUart0Rx() {
        return uart0Rx;
    }

    /** The TX pin of the UART0 socket: GP0. */
    public int getUart0Tx() {
        return uart0Tx;
    }

    /** The RX pin of the UART1 socket: GP5. */
    public int getUart1Rx() {
        return uart1Rx;
    }

    /** The TX pin of the UART1 socket: GP4. */
    public int getUart1Tx() {
        return uart1Tx;
    }

    /** The MISO/RX pin of the SPI0 socket: GP16. */
    public int getSpi0Miso() {
        return spi0Miso;
    }

    /** The MOSI/TX pin of the SPI0 socket: GP19. */
    public int getSpi0Mosi() {
        return spi0Mosi;
    }

    /** The clock pin of the SPI0 socket: GP18. */
    public int getSpi0Sck() {
        return spi0Sck;
    }

    /** The chip-select pin of the SPI0 socket: GP17. */
    public int getSpi0Cs() {
        return spi0Cs;
    }
}
