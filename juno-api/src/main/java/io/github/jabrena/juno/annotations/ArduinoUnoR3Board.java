package io.github.jabrena.juno.annotations;

/**
 * Selects the classic Arduino UNO R3 as {@link Board @Board}'s target: Juno programs run on its ATmega328P, an 8-bit
 * AVR with 2 KB SRAM and 32 KB flash, under Arduino's {@code arduino:avr} core. It supports the basic GPIO, analog,
 * serial, I2C, and SPI APIs that fit its memory; its {@code java.math} support is bounded by the 1 KiB runtime arena.
 * It has no LED matrix, native USB HID, task scheduler or onboard radio, so {@code ArduinoUno.getWifi()} does not
 * compile for it. With the retired Arduino WiFi Shield stacked on it, the {@code Wifi} from
 * {@code new ArduinoWifiShield().getWifi()} ({@code begin},
 * {@code status}, {@code localIP}, {@code gatewayIP}, {@code macAddress}), {@code Udp}, {@code Tcp} and
 * plain-HTTP {@code HttpClient} requests run through Arduino's legacy {@code WiFi} library; there is no access point
 * or HTTPS, the shield uses D10
 * (Wi-Fi), D4 (its SD card) and D7 (handshake), and a shield that does not answer makes {@code Wifi.status()} report
 * {@code Wifi.STATUS_NO_SHIELD}. Only small network programs fit: build them with {@code -Djuno.Xmx=512}.
 */
public final class ArduinoUnoR3Board implements BoardSupported {
    private ArduinoUnoR3Board() {
    }
}
