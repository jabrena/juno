package io.github.jabrena.juno.hardware.device.sd;

import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import org.jspecify.annotations.Nullable;

/**
 * The AZDelivery Data Logger Module Data Recorder Shield: an SD card socket on the UNO's SPI pins, chip select on D10
 * (MOSI D11, MISO D12, SCK D13), plus a DS1307 real-time clock that this class does not drive. Format the card as
 * FAT16 or FAT32.
 *
 * <p>Its D10 chip select is also the LCD Keypad Shield's backlight line: isolate or reroute that pin before stacking
 * the two, as {@link io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield#getSdCard()} describes.
 *
 * @see <a href="https://www.az-delivery.de/en/products/datenlogger-modul">AZ-Delivery data logger module</a>
 */
public final class DataLoggerShield {
    /**
     * The SD socket's chip select. A compile-time constant, so a card taken in another class's static initializer
     * never reads it before this class's own initializer has run.
     */
    private static final int SD_CHIP_SELECT = 10;

    private @Nullable SdCard sdCard;

    /** A shield on the board's own pins. Create it once, at startup, and share it. */
    public DataLoggerShield() {
    }

    /** The SD socket's chip select, D10. */
    public int getSdChipSelect() {
        return SD_CHIP_SELECT;
    }

    /**
     * The card in the shield's SD socket; its {@link SdCard#begin()} selects it on D10. The same instance every
     * call.
     */
    public SdCard getSdCard() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(SD_CHIP_SELECT);
            sdCard = card;
        }
        return card;
    }
}
