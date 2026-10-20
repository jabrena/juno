package io.github.jabrena.juno.annotations;

/**
 * Selects the classic Arduino UNO R3 as {@link Board @Board}'s target: Juno programs run on its ATmega328P, an 8-bit
 * AVR with 2 KB SRAM and 32 KB flash, under Arduino's {@code arduino:avr} core. It supports the basic GPIO, analog,
 * serial, I2C, SPI and shield APIs that fit its memory; it has no LED matrix, native USB HID, arbitrary precision
 * numbers or task scheduler. With the retired Arduino WiFi Shield stacked on it, {@code Wifi} ({@code begin},
 * {@code status}, {@code localIP}, {@code gatewayIP}, {@code macAddress}), {@code Udp}, {@code Tcp} and
 * plain-HTTP {@code HttpClient} requests run through Arduino's legacy {@code WiFi} library; there is no access point
 * or HTTPS, the shield uses D10
 * (Wi-Fi), D4 (its SD card) and D7 (handshake), and a shield that does not answer makes {@code Wifi.status()} report
 * {@code Wifi.STATUS_NO_SHIELD}. Only small network programs fit: build them with {@code -Djuno.Xmx=512}.
 */
public final class ArduinoUnoR3 implements ArduinoBoard {
    private ArduinoUnoR3() {
    }
}
