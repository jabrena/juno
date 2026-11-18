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

    /**
     * Sets the clock of the transfers that follow, in hertz; the board rounds it down to a rate it can make. The mode
     * stays 0 and the bit order most significant first. Devices sharing the bus at different speeds each set their
     * own clock when they select themselves: a display at tens of megahertz, a touch controller at two.
     */
    void setClock(int frequencyHz);

    /**
     * Clocks out the first {@code length} bytes of {@code buffer} in one call, replacing each with the byte clocked in
     * at the same time. This is the fast way to send a run of data such as a row of pixels; refill the buffer before
     * sending it again.
     */
    void transfer(byte[] buffer, int length);

    /**
     * Clocks out the first {@code length} bytes of {@code buffer} and discards the bytes clocked in, leaving the
     * buffer unchanged: the fast way to send data to a device that only listens, such as a display. A board whose SPI
     * peripheral can be fed faster than its core's transfer call sends it that way.
     */
    void write(byte[] buffer, int length);
}
