package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.internal.NativeDisplay;
import io.github.jabrena.juno.hardware.internal.NativeKeyboard;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.net.Wifi;
import org.jspecify.annotations.Nullable;

/**
 * The M5Stack Cardputer's built-in hardware. Each getter returns the same instance every call, backed by the
 * compiler's intrinsics. Get each device once at startup and pass it to the classes that need it; a unit test passes a
 * mock of the device interface instead.
 */
public final class M5StackCardputer {
    private static final int SD_CHIP_SELECT = 12;

    private static @Nullable SdCard sdCard;

    private M5StackCardputer() {
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

    /** The Cardputer's built-in ESP32-S3 Wi-Fi radio. */
    public static Wifi getWifi() {
        return NativeWifi.wifi();
    }

    /** The Cardputer's 240 by 135 pixel color display. */
    public static Display getDisplay() {
        return NativeDisplay.display();
    }

    /** The Cardputer's built-in keyboard. */
    public static Keyboard getKeyboard() {
        return NativeKeyboard.keyboard();
    }
}
