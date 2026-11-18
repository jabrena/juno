package io.github.jabrena.juno.hardware.device.usb;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;

/**
 * The MAX3421E's registers over SPI and its host transaction engine: launching a token and retrying it on NAK and
 * time-outs. {@link UsbHostShield} builds transfers and enumeration on top. Allocation-free.
 */
final class Max3421e {
    // Registers, already shifted into the command byte's register field (bits 7..3); bit 1 marks a write.
    static final int RCVFIFO = 1 << 3;
    static final int SNDFIFO = 2 << 3;
    static final int SUDFIFO = 4 << 3;
    static final int RCVBC = 6 << 3;
    static final int SNDBC = 7 << 3;
    static final int USBIRQ = 13 << 3;
    static final int USBCTL = 15 << 3;
    static final int PINCTL = 17 << 3;
    static final int REVISION = 18 << 3;
    static final int HIRQ = 25 << 3;
    static final int MODE = 27 << 3;
    static final int PERADDR = 28 << 3;
    static final int HCTL = 29 << 3;
    static final int HXFR = 30 << 3;
    static final int HRSL = 31 << 3;
    static final int WRITE = 0x02;

    static final int USBCTL_CHIPRES = 0x20;
    static final int USBIRQ_OSCOKIRQ = 0x01;
    /** Full-duplex SPI, level-active interrupt, and GPX reporting the SOF marker. */
    static final int PINCTL_FDUPSPI_INTLEVEL_GPXB = 0x10 | 0x08 | 0x02;
    static final int MODE_DPPULLDN = 0x80;
    static final int MODE_DMPULLDN = 0x40;
    static final int MODE_SOFKAENAB = 0x08;
    static final int MODE_LOWSPEED = 0x02;
    static final int MODE_HOST = 0x01;
    static final int MODE_HOST_IDLE = MODE_DPPULLDN | MODE_DMPULLDN | MODE_HOST;
    static final int HIRQ_HXFRDNIRQ = 0x80;
    static final int HIRQ_FRAMEIRQ = 0x40;
    static final int HIRQ_RCVDAVIRQ = 0x04;
    static final int HCTL_SNDTOG1 = 0x80;
    static final int HCTL_SNDTOG0 = 0x40;
    static final int HCTL_RCVTOG1 = 0x20;
    static final int HCTL_RCVTOG0 = 0x10;
    static final int HCTL_SAMPLEBUS = 0x04;
    static final int HCTL_BUSRST = 0x01;
    static final int HRSL_JSTATUS = 0x80;
    static final int HRSL_KSTATUS = 0x40;
    static final int HRSL_SNDTOGRD = 0x20;
    static final int HRSL_RCVTOGRD = 0x10;
    static final int HRSL_RESULT = 0x0F;

    static final int TOKEN_SETUP = 0x10;
    static final int TOKEN_IN = 0x00;
    static final int TOKEN_OUT = 0x20;
    static final int TOKEN_IN_HANDSHAKE = 0x80;
    static final int TOKEN_OUT_HANDSHAKE = 0xA0;

    static final int RESULT_SUCCESS = 0;
    static final int RESULT_NAK = 4;
    static final int RESULT_TOGERR = 6;
    static final int RESULT_TIMEOUT = 14;

    /** Polls of a status register before giving up; one poll is a few microseconds of SPI. */
    static final int POLLS = 20_000;
    private static final int RETRY_LIMIT = 3;

    private final Gpio gpio;
    private final Spi spi;
    private final int chipSelectPin;

    Max3421e(Gpio gpio, Spi spi, int chipSelectPin) {
        this.gpio = gpio;
        this.spi = spi;
        this.chipSelectPin = chipSelectPin;
    }

    /** Starts the bus and resets the chip; returns whether its oscillator came up and it reports revision 0x12/0x13. */
    boolean reset() {
        gpio.pinMode(chipSelectPin, gpio.getOutput());
        gpio.digitalWrite(chipSelectPin, true);
        spi.begin();
        write(PINCTL, PINCTL_FDUPSPI_INTLEVEL_GPXB);
        write(USBCTL, USBCTL_CHIPRES);
        write(USBCTL, 0);
        if (!waitFor(USBIRQ, USBIRQ_OSCOKIRQ)) {
            return false;
        }
        int revision = read(REVISION);
        return revision == 0x12 || revision == 0x13;
    }

    /**
     * Launches {@code token} to {@code endpoint} and retries it on NAK (up to {@code nakLimit}) and on time-outs
     * (three times). For an OUT token, a retry re-arms the send FIFO ({@code firstByte}, {@code length}) as the chip
     * frees it once sent, answered or not. Returns 0 or a negative {@link UsbHostShield} error code.
     */
    int dispatch(int token, int endpoint, int nakLimit, int firstByte, int length) {
        int naks = 0;
        int retries = 0;
        while (true) {
            write(HXFR, token | endpoint);
            if (!waitFor(HIRQ, HIRQ_HXFRDNIRQ)) {
                return UsbHostShield.ERROR_NO_RESPONSE;
            }
            write(HIRQ, HIRQ_HXFRDNIRQ);
            int result = read(HRSL) & HRSL_RESULT;
            if (result == RESULT_SUCCESS) {
                return 0;
            }
            boolean nak = result == RESULT_NAK;
            boolean retryable = result == RESULT_TIMEOUT || (token == TOKEN_OUT && result == RESULT_TOGERR);
            if (!nak && !retryable) {
                return -result;
            }
            naks += nak ? 1 : 0;
            retries += retryable ? 1 : 0;
            if (naks >= nakLimit || retries >= RETRY_LIMIT) {
                return -result;
            }
            if (token == TOKEN_OUT) {
                rearm(result, firstByte, length);
            }
        }
    }

    /** Polls {@code register} until {@code bit} is set; returns {@code false} after {@link #POLLS} reads. */
    boolean waitFor(int register, int bit) {
        for (int poll = 0; poll < POLLS; poll++) {
            if ((read(register) & bit) != 0) {
                return true;
            }
        }
        return false;
    }

    void write(int register, int value) {
        gpio.digitalWrite(chipSelectPin, false);
        spi.transfer(register | WRITE);
        spi.transfer(value);
        gpio.digitalWrite(chipSelectPin, true);
    }

    int read(int register) {
        gpio.digitalWrite(chipSelectPin, false);
        spi.transfer(register);
        int value = spi.transfer(0);
        gpio.digitalWrite(chipSelectPin, true);
        return value & 0xFF;
    }

    void writeFifo(int register, byte[] data, int offset, int length) {
        gpio.digitalWrite(chipSelectPin, false);
        spi.transfer(register | WRITE);
        for (int i = 0; i < length; i++) {
            spi.transfer(data[offset + i] & 0xFF);
        }
        gpio.digitalWrite(chipSelectPin, true);
    }

    void readFifo(int register, byte[] buffer, int offset, int length) {
        gpio.digitalWrite(chipSelectPin, false);
        spi.transfer(register);
        for (int i = 0; i < length; i++) {
            buffer[offset + i] = (byte) spi.transfer(0);
        }
        gpio.digitalWrite(chipSelectPin, true);
    }

    /** Re-arms the send FIFO with the same packet; after a toggle error, also flips the data toggle. */
    private void rearm(int result, int firstByte, int length) {
        if (result == RESULT_TOGERR) {
            write(HCTL, (read(HRSL) & HRSL_SNDTOGRD) != 0 ? HCTL_SNDTOG0 : HCTL_SNDTOG1);
        }
        write(SNDBC, 0);
        write(SNDFIFO, firstByte);
        write(SNDBC, length);
    }
}
