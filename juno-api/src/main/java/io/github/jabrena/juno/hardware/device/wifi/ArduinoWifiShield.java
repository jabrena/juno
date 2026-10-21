package io.github.jabrena.juno.hardware.device.wifi;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.net.Wifi;
import org.jspecify.annotations.Nullable;

/**
 * The retired Arduino WiFi Shield, the UNO R3's only way onto a Wi-Fi network: the R3 has no radio of its own, so
 * {@link io.github.jabrena.juno.hardware.MCU#getWifi()} is rejected there and the program takes its {@link Wifi} from
 * this shield instead. The shield talks to the R3 over SPI through the legacy {@code WiFi} library; it reserves D4
 * (SD card chip select), D7 (handshake) and D10 (Wi-Fi chip select), so it cannot be stacked with a shield that uses
 * those pins, such as the LCD Keypad Shield. It joins networks with {@link Wifi#begin} only: no access point and no
 * HTTPS. Its microSD socket, on the same SPI bus with chip select on D4, is {@link #getSdCard()}.
 *
 * @see <a href="https://docs.arduino.cc/retired/shields/arduino-wifi-shield/">Arduino WiFi Shield</a>
 */
public final class ArduinoWifiShield {
    /** The microSD socket's chip select. */
    private static final int SD_CHIP_SELECT = Gpio.getD4();

    private @Nullable SdCard sdCard;

    public ArduinoWifiShield() {
    }

    /** The shield's Wi-Fi radio. */
    public Wifi getWifi() {
        return NativeWifi.wifi();
    }

    /**
     * The card in the shield's microSD socket; its {@link SdCard#begin()} selects it on D4. The same instance every
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
