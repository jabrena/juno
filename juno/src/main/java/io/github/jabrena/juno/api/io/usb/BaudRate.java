package io.github.jabrena.juno.api.io.usb;

/**
 * Common baud rates exposed as named values by Juno's USB serial API. Use one of them with
 * {@link Serial#begin(BaudRate)} and configure {@code arduino-cli monitor} with the matching
 * numeric rate.
 *
 * @see <a href="https://docs.arduino.cc/arduino-cli/pluggable-monitor-specification">
 *     Arduino CLI pluggable monitor specification</a>
 */
public enum BaudRate {
    /** 9,600 bits per second. */
    BAUD_9600(9600),
    /** 19,200 bits per second. */
    BAUD_19200(19200),
    /** 38,400 bits per second. */
    BAUD_38400(38400),
    /** 57,600 bits per second. */
    BAUD_57600(57600),
    /** 115,200 bits per second. */
    BAUD_115200(115200);

    private final int bitsPerSecond;

    BaudRate(int bitsPerSecond) {
        this.bitsPerSecond = bitsPerSecond;
    }

    /**
     * Returns the numeric data rate represented by this value.
     *
     * @return the rate in bits per second
     */
    public int bitsPerSecond() {
        return bitsPerSecond;
    }
}
