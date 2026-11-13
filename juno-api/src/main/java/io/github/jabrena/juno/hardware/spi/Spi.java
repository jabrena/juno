package io.github.jabrena.juno.hardware.spi;

/**
 * Full-duplex SPI on the board's primary bus (SCK, MOSI and MISO). The bus has no chip select of its own: a device
 * is selected by driving its chip-select pin low through {@link io.github.jabrena.juno.hardware.gpio.Gpio} around
 * each {@link #transfer(int)} sequence. {@link io.github.jabrena.juno.hardware.MCU#getSpi()} is the board's own
 * bus; a unit test passes {@code mock(Spi.class)} instead. Get the bus once, at startup, and pass it to the classes
 * that need it.
 */
public interface Spi {

    /** Starts the bus as a controller in SPI mode 0, most significant bit first, at the core's default clock. */
    void begin();

    /** Clocks {@code value} (low 8 bits) out and returns the byte clocked in, {@code 0..255}. */
    int transfer(int value);
}
