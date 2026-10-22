package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import org.jspecify.annotations.Nullable;

/**
 * The M5Stack Cardputer's built-in hardware. Each getter returns the same instance every call, backed by the
 * compiler's intrinsics. Get each device once at startup and pass it to the classes that need it; a unit test passes a
 * mock of the device interface instead.
 */
public final class M5 {
    private static final int SD_CHIP_SELECT = 12;

    private static @Nullable SdCard sdCard;

    private M5() {
    }

    /** The Cardputer's GPIO pins, including GPIO1 and GPIO2 on its HY2.0-4P port. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /**
     * The card in the Cardputer's microSD socket. Its {@link SdCard#begin()} selects GPIO12 and uses the board's
     * built-in SPI bus on GPIO14 (MOSI), GPIO39 (MISO), and GPIO40 (clock). The same instance is returned every call.
     */
    public static SdCard getSd() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(SD_CHIP_SELECT);
            sdCard = card;
        }
        return card;
    }
}
