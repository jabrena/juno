package io.github.jabrena.juno.annotations;

/**
 * Selects the classic Arduino UNO R3 as {@link Board @Board}'s target: Juno programs run on its ATmega328P, an 8-bit
 * AVR with 2 KB SRAM and 32 KB flash, under Arduino's {@code arduino:avr} core. It supports the basic GPIO, analog,
 * serial, I2C, and SPI APIs that fit its memory; its {@code java.math} support is bounded by the 1 KiB runtime arena.
 * It has no LED matrix, native USB HID, task scheduler or onboard radio, and no network support: {@code Wifi},
 * {@code Udp}, {@code Tcp}, {@code HttpClient} and {@code LocalDateTime.now()} do not compile for it.
 */
public final class ArduinoUnoR3Board implements BoardSupported {
    private ArduinoUnoR3Board() {
    }
}
