package io.github.jabrena.juno.annotations;

/**
 * Selects the classic Arduino UNO R3 as {@link Board @Board}'s target: Juno programs run on its ATmega328P, an 8-bit
 * AVR with 2 KB SRAM and 32 KB flash, under Arduino's {@code arduino:avr} core. It supports the basic GPIO, analog,
 * serial, I2C, SPI and shield APIs that fit its memory; it has no onboard Wi-Fi, LED matrix, native USB HID, arbitrary
 * precision numbers or task scheduler.
 */
public final class ArduinoUnoR3 implements ArduinoBoard {
    private ArduinoUnoR3() {
    }
}
