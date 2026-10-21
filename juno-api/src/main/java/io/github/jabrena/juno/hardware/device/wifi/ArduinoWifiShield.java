package io.github.jabrena.juno.hardware.device.wifi;

import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.net.Wifi;

/**
 * The retired Arduino WiFi Shield, the UNO R3's only way onto a Wi-Fi network: the R3 has no radio of its own, so
 * {@link io.github.jabrena.juno.hardware.MCU#getWifi()} is rejected there and the program takes its {@link Wifi} from
 * this shield instead. The shield talks to the R3 over SPI through the legacy {@code WiFi} library; it reserves D4
 * (SD card chip select), D7 (handshake) and D10 (Wi-Fi chip select), so it cannot be stacked with a shield that uses
 * those pins, such as the LCD Keypad Shield. It joins networks with {@link Wifi#begin} only: no access point and no
 * HTTPS.
 *
 * @see <a href="https://docs.arduino.cc/retired/shields/arduino-wifi-shield/">Arduino WiFi Shield</a>
 */
public final class ArduinoWifiShield {

    public ArduinoWifiShield() {
    }

    /** The shield's Wi-Fi radio. */
    public Wifi getWifi() {
        return NativeWifi.wifi();
    }
}
