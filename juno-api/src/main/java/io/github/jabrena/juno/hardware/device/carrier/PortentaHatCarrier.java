package io.github.jabrena.juno.hardware.device.carrier;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import org.jspecify.annotations.Nullable;

/**
 * Pin map for the Arduino Portenta Hat Carrier (ASX00049) with a Portenta H7 on it. The carrier's 40-pin header uses
 * the Raspberry Pi physical numbering and carries the H7's SPI bus on pins 19, 21, 23 and 24, so {@code MCU.getSpi()}
 * reaches a HAT's SPI device with no extra wiring. Pins here are the Juno names the H7 accepts:
 *
 * <ul>
 *   <li>Pin 19 MOSI, 21 MISO, 23 SCLK and 24 CE0: the SPI bus, D8, D10, D9 and D7 ({@code MCU.getSpi()}).</li>
 *   <li>Pin 18, the data/command line of a HAT display: {@link #getLcdDataCommand()}, Portenta pin PI7, which has no
 *       Arduino number and is reached through the H7 core's raw pin index 161.</li>
 *   <li>Pin 22, a display's reset: {@link #getLcdReset()}, D5.</li>
 *   <li>Pin 24, the display's chip select: {@link #getLcdChipSelect()}, D7.</li>
 *   <li>Pin 26, a touch controller's chip select: {@link #getTouchChipSelect()}, D4.</li>
 * </ul>
 *
 * <p>The carrier's microSD slot is not on the header: it is the H7's SDMMC2 bus, which needs no Juno pin name, and
 * {@link #getSdCard()} is its card.
 *
 * <p>Create it once, at startup, and pass it to the HAT's driver.
 *
 * @see <a href="https://docs.arduino.cc/resources/datasheets/ASX00049-datasheet.pdf">Portenta Hat Carrier datasheet</a>
 */
public final class PortentaHatCarrier {
    /** The SDMMC slot is selected by its bus, not a pin; the SD runtime ignores this value on the Portenta. */
    private static final int SDMMC_NO_CHIP_SELECT = 0;

    private final int lcdChipSelect;
    private final int lcdDataCommand;
    private final int lcdReset;
    private final int touchChipSelect;
    private @Nullable SdCard sdCard;

    /** Creates the carrier handle on the board's own GPIO pins ({@link MCU#getGpio()}). */
    public PortentaHatCarrier() {
        this(MCU.getGpio());
    }

    /** Creates the carrier handle for a board GPIO context. Create it once, at startup, and reuse it. */
    public PortentaHatCarrier(Gpio gpio) {
        lcdChipSelect = gpio.digitalPin("D7");
        lcdDataCommand = gpio.digitalPin("GPIO161");
        lcdReset = gpio.digitalPin("D5");
        touchChipSelect = gpio.digitalPin("D4");
    }

    /**
     * The card in the carrier's own microSD slot (J7), not the H7's: the slot is on the SDMMC2 bus, so it has no chip
     * select and does not share the header's SPI bus with a HAT display. The same instance every call; its
     * {@link SdCard#begin()} mounts the card, and {@code java.io.RandomAccessFile} reads files from it.
     */
    public SdCard getSdCard() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(SDMMC_NO_CHIP_SELECT);
            sdCard = card;
        }
        return card;
    }

    /** The chip select of a display on the header's SPI bus, pin 24 (D7). */
    public int getLcdChipSelect() {
        return lcdChipSelect;
    }

    /** The data/command line of a display on the header, pin 18 (PI7, raw index 161). */
    public int getLcdDataCommand() {
        return lcdDataCommand;
    }

    /** The reset line of a display on the header, pin 22 (D5). */
    public int getLcdReset() {
        return lcdReset;
    }

    /** The chip select of a touch controller on the header's SPI bus, pin 26 (D4). */
    public int getTouchChipSelect() {
        return touchChipSelect;
    }
}
