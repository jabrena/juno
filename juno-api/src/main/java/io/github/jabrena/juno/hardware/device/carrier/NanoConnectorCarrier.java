package io.github.jabrena.juno.hardware.device.carrier;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import org.jspecify.annotations.Nullable;

/**
 * Pin map for the passive Arduino Nano Connector Carrier (ASX00061): a microSD card slot on SPI, four Grove
 * sockets and one Qwiic connector, all wired to ordinary Nano header pins. Like
 * {@link io.github.jabrena.juno.hardware.device.grove.GroveBaseShield} it adds no protocol or runtime object, so
 * these pins can be passed directly to a Grove driver or to {@link Gpio}; the microSD slot's card is
 * {@link #getSdCard()}.
 *
 * <p>The carrier seen from its connector side, USB cable at the bottom; {@code *} marks each Grove socket's yellow
 * signal wire (Grove pin 1):
 * <pre>
 *  +---------------------------------------------+
 *  |  Grove I2C                      Grove UART  |
 *  |  [ GND  ]                       [ RX * ]    |
 *  |  [ VCC  ]                       [ TX   ]    |
 *  |  [ SDA  ] A4                    [ VCC  ]    |
 *  |  [ SCL *] A5                    [ GND  ]    |
 *  |                 +-------------+             |
 *  |  Qwiic          |   microSD   |             |
 *  |  [ I2C  ]       |   slot      |             |
 *  |  A4/A5, 3.3 V   |   CS D4     |             |
 *  |                 +-------------+             |
 *  |  Grove A0/A1                    Grove A2/A3 |
 *  |  [ GND  ]                       [ A2 * ]    |
 *  |  [ VCC  ]                       [ A3   ]    |
 *  |  [ A1   ]                       [ VCC  ]    |
 *  |  [ A0 * ]                       [ GND  ]    |
 *  +-------------------[ USB ]-------------------+
 * </pre>
 *
 * <p>For a single-signal Grove module, plug it in and pass the socket's getter to the driver, as with
 * {@code GroveBaseShield}: {@code pir.begin(carrier.getGroveA2())}, {@code light.rawValue(carrier.getGroveA0())}.
 * The module's {@code GND} and {@code VCC} wires are supplied by the socket.
 * <ul>
 *   <li>{@link #getGroveA0()} and {@link #getGroveA2()}: direct to the Nano's pins, digital or analog. The second
 *       (white) wires are {@link #getA1()} and {@link #getA3()}.</li>
 *   <li>{@link #getGroveUart()}: RX on D0; TX on D1 is {@link #getUartTx()}. These are the Nano's hardware serial
 *       pins, free on the Nano ESP32, whose {@code Serial} is USB.</li>
 *   <li>Grove I2C and Qwiic: one bus, SDA on A4 and SCL on A5, reached through {@code MCU.getI2c()}. Use it for I2C
 *       modules only: the bus pull-ups distort a digital module's output, and a Grove PIR measured about 1.4 V at
 *       rest here, neither low nor high. Plug such a module into an analog socket instead.</li>
 * </ul>
 * The Grove sockets' VCC is 3.3 V or 5 V, chosen by the carrier's input selector switch; the Qwiic connector and the
 * microSD slot are always 3.3 V. Set the switch to 3.3 V for a 3.3 V board such as the Nano ESP32.
 *
 * <p>{@link #getSdCard()} selects the card on D4, the slot's chip select, rather than the UNO shields' D10; after
 * moving the slot's solder jumper, pass {@link #getSdChipSelectJumperD2()} or {@link #getSdChipSelectJumperD3()} to
 * {@link SdCard#begin(int)} instead. Its SPI clock is D13, which the Nano ESP32 shares with its built-in LED, so a
 * program reading the card should not blink that LED.
 *
 * @see <a href="https://docs.arduino.cc/resources/datasheets/ASX00061-datasheet.pdf">Nano Connector Carrier datasheet</a>
 * @see <a href="https://docs.arduino.cc/tutorials/nano-connector-carrier/getting-started-nano-connector-carrier/">Getting started with the Nano Connector Carrier</a>
 */
public final class NanoConnectorCarrier {
    private final int sdChipSelect;
    private final int sdChipSelectJumperD2;
    private final int sdChipSelectJumperD3;
    private final int spiMosi;
    private final int spiMiso;
    private final int spiSck;
    private final int a0;
    private final int a1;
    private final int a2;
    private final int a3;
    private final int i2cSda;
    private final int i2cScl;
    private final int uartRx;
    private final int uartTx;
    private @Nullable SdCard sdCard;

    /** Creates the carrier handle on the board's own GPIO pins ({@link MCU#getGpio()}). Create it once, at startup. */
    public NanoConnectorCarrier() {
        this(MCU.getGpio());
    }

    /** Creates the carrier handle for a board GPIO context. Create it once, at startup, and reuse it. */
    public NanoConnectorCarrier(Gpio gpio) {
        sdChipSelect = gpio.digitalPin("D4");
        sdChipSelectJumperD2 = gpio.digitalPin("D2");
        sdChipSelectJumperD3 = gpio.digitalPin("D3");
        spiMosi = gpio.digitalPin("D11");
        spiMiso = gpio.digitalPin("D12");
        spiSck = gpio.digitalPin("D13");
        a0 = gpio.analogPin("A0");
        a1 = gpio.analogPin("A1");
        a2 = gpio.analogPin("A2");
        a3 = gpio.analogPin("A3");
        i2cSda = gpio.analogPin("A4");
        i2cScl = gpio.analogPin("A5");
        uartRx = gpio.digitalPin("D0");
        uartTx = gpio.digitalPin("D1");
    }

    /**
     * The card in the microSD slot; its {@link SdCard#begin()} selects it on D4. The same
     * instance every call.
     */
    public SdCard getSdCard() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(sdChipSelect);
            sdCard = card;
        }
        return card;
    }

    /** The signal pin of a module in the Grove A0/A1 socket: A0. */
    public int getGroveA0() {
        return a0;
    }

    /** The signal pin of a module in the Grove A2/A3 socket: A2. */
    public int getGroveA2() {
        return a2;
    }

    /** The signal pin of a module in the Grove UART socket: RX, D0. */
    public int getGroveUart() {
        return uartRx;
    }

    /** The microSD slot's chip select, D4. */
    public int getSdChipSelect() {
        return sdChipSelect;
    }

    /** The microSD slot's chip select once its solder jumper is moved to D2. */
    public int getSdChipSelectJumperD2() {
        return sdChipSelectJumperD2;
    }

    /** The microSD slot's chip select once its solder jumper is moved to D3. */
    public int getSdChipSelectJumperD3() {
        return sdChipSelectJumperD3;
    }

    /** The microSD slot's SPI MOSI pin, D11. */
    public int getSpiMosi() {
        return spiMosi;
    }

    /** The microSD slot's SPI MISO pin, D12. */
    public int getSpiMiso() {
        return spiMiso;
    }

    /** The microSD slot's SPI clock, D13, which is also the Nano's built-in LED. */
    public int getSpiSck() {
        return spiSck;
    }

    /** The Grove A0/A1 socket's signal (yellow) wire, A0; the same pin as {@link #getGroveA0()}. */
    public int getA0() {
        return a0;
    }

    /** The Grove A0/A1 socket's second (white) wire, A1. */
    public int getA1() {
        return a1;
    }

    /** The Grove A2/A3 socket's signal (yellow) wire, A2; the same pin as {@link #getGroveA2()}. */
    public int getA2() {
        return a2;
    }

    /** The Grove A2/A3 socket's second (white) wire, A3. */
    public int getA3() {
        return a3;
    }

    /** SDA, A4, shared by the Grove I2C socket and the Qwiic connector. */
    public int getI2cSda() {
        return i2cSda;
    }

    /** SCL, A5, shared by the Grove I2C socket and the Qwiic connector. */
    public int getI2cScl() {
        return i2cScl;
    }

    /** The Grove UART socket's RX, D0; the same pin as {@link #getGroveUart()}. */
    public int getUartRx() {
        return uartRx;
    }

    /** The Grove UART socket's TX, D1. */
    public int getUartTx() {
        return uartTx;
    }
}
