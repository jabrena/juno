package io.github.jabrena.juno.hardware.device.ti;

import io.github.jabrena.juno.hardware.device.usb.UsbHostShield;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * The two lower layers of TI's USB link protocol (DUSB) over the USB Host Shield's bulk endpoints. Raw packets are a
 * 4-byte big-endian length, a type byte and the payload; every data chunk is answered by a raw acknowledgement.
 * Virtual packets, the commands, are a 4-byte data length, a 2-byte type and the data, split over raw data chunks of
 * at most the negotiated buffer size. Every method returns {@link TiLink#ERROR_NONE} or a negative {@code TiLink}
 * error code. Allocation-free after construction.
 */
final class DusbTransport {
    static final int RAW_BUFFER_SIZE_REQUEST = 1;
    static final int RAW_BUFFER_SIZE_ALLOCATION = 2;
    static final int RAW_DATA = 3;
    static final int RAW_DATA_LAST = 4;
    static final int RAW_DATA_ACK = 5;

    static final int RAW_HEADER = 5;
    static final int VIRTUAL_HEADER = 6;
    /** Where a virtual packet's data starts in the transmit buffer. */
    static final int DATA = RAW_HEADER + VIRTUAL_HEADER;
    /** The largest virtual packet data: a whole list, its 2-byte count and its reals. */
    static final int MAX_DATA = 2 + TiLink.MAX_LIST_LENGTH * TiReal.BYTES;

    private static final int ENDPOINT_IN = 1;
    private static final int ENDPOINT_OUT = 2;
    private static final int USB_PACKET = 64;
    private static final int TIMEOUT_MILLIS = 3000;

    private final UsbHostShield usb;
    private final Clock clock;
    private final byte[] transmit = new byte[DATA + MAX_DATA];
    private final byte[] receive = new byte[USB_PACKET * 4];
    private final byte[] packet = new byte[USB_PACKET];
    private final byte[] ack = {0, 0, 0, 2, RAW_DATA_ACK, (byte) 0xE0, 0x00};
    private int bufferSize;
    private int receivedType;
    private int receivedLength;
    private int virtualType;

    DusbTransport(UsbHostShield usb, Clock clock, int bufferSize) {
        this.usb = usb;
        this.clock = clock;
        this.bufferSize = bufferSize;
    }

    /** The buffer the caller fills with a virtual packet's data from {@link #DATA} before {@link #sendVirtual}. */
    byte[] transmitBuffer() {
        return transmit;
    }

    int bufferSize() {
        return bufferSize;
    }

    /** Asks for raw packets of up to {@code requested} bytes and keeps the smaller of that and what is granted. */
    int negotiateBufferSize(int requested) {
        putInt(transmit, RAW_HEADER, requested);
        int result = sendRaw(0, RAW_BUFFER_SIZE_REQUEST, 4);
        if (result == TiLink.ERROR_NONE) {
            result = receiveRaw();
        }
        if (result != TiLink.ERROR_NONE) {
            return result;
        }
        if (receivedType != RAW_BUFFER_SIZE_ALLOCATION || receivedLength < RAW_HEADER + 4) {
            virtualType = receivedType;
            return TiLink.ERROR_PROTOCOL;
        }
        int granted = getInt(receive, RAW_HEADER);
        bufferSize = granted < requested ? granted : requested;
        return TiLink.ERROR_NONE;
    }

    /**
     * Sends the virtual packet of {@code type} whose {@code dataLength} bytes already sit in the transmit buffer from
     * {@link #DATA}, in raw chunks of at most {@link #bufferSize()}, each acknowledged. A chunk's raw header is written
     * over the 5 bytes before it, which belong to the chunk already sent.
     */
    int sendVirtual(int type, int dataLength) {
        putInt(transmit, RAW_HEADER, dataLength);
        transmit[RAW_HEADER + 4] = (byte) (type >> 8);
        transmit[RAW_HEADER + 5] = (byte) type;
        int remaining = VIRTUAL_HEADER + dataLength;
        int start = RAW_HEADER;
        while (remaining > 0) {
            int chunk = remaining < bufferSize ? remaining : bufferSize;
            int result = sendRaw(start - RAW_HEADER, chunk == remaining ? RAW_DATA_LAST : RAW_DATA, chunk);
            if (result == TiLink.ERROR_NONE) {
                result = awaitRawAck();
            }
            if (result != TiLink.ERROR_NONE) {
                return result;
            }
            start += chunk;
            remaining -= chunk;
        }
        return TiLink.ERROR_NONE;
    }

    /**
     * Receives one virtual packet, acknowledging each raw chunk; its type is then {@link #virtualType()} and the start
     * of its data is readable with {@link #receivedShort(int)}.
     */
    int receiveVirtual() {
        boolean first = true;
        int type = -1;
        while (true) {
            int result = receiveRaw();
            if (result != TiLink.ERROR_NONE) {
                return result;
            }
            if (receivedType != RAW_DATA && receivedType != RAW_DATA_LAST) {
                virtualType = receivedType;
                return TiLink.ERROR_PROTOCOL;
            }
            if (first && receivedLength >= DATA) {
                type = getShort(receive, RAW_HEADER + 4);
            }
            first = false;
            result = send(ack, 0, ack.length);
            if (result != TiLink.ERROR_NONE) {
                return result;
            }
            if (receivedType == RAW_DATA_LAST) {
                virtualType = type;
                return TiLink.ERROR_NONE;
            }
        }
    }

    /** The type of the last virtual packet received, or of the raw packet that arrived in its place. */
    int virtualType() {
        return virtualType;
    }

    /** The 2-byte big-endian value at {@code offset} into the last virtual packet's data, 0 past its end. */
    int receivedShort(int offset) {
        int at = DATA + offset;
        return at + 2 <= receivedLength ? getShort(receive, at) : 0;
    }

    private int awaitRawAck() {
        int result = receiveRaw();
        if (result != TiLink.ERROR_NONE) {
            return result;
        }
        if (receivedType != RAW_DATA_ACK) {
            virtualType = receivedType;
            return TiLink.ERROR_PROTOCOL;
        }
        return TiLink.ERROR_NONE;
    }

    /** Sends the raw packet of {@code type} whose {@code length} payload bytes follow the header at {@code at}. */
    private int sendRaw(int at, int type, int length) {
        putInt(transmit, at, length);
        transmit[at + 4] = (byte) type;
        return send(transmit, at, RAW_HEADER + length);
    }

    private int send(byte[] data, int at, int length) {
        return usb.bulkOut(ENDPOINT_OUT, data, at, length, USB_PACKET) == length ? TiLink.ERROR_NONE
                : TiLink.ERROR_USB;
    }

    /**
     * Receives one raw packet: its header and as much of its payload as the receive buffer holds; the rest is read
     * and dropped.
     */
    private int receiveRaw() {
        int kept = 0;
        int total = -1;
        int read = 0;
        int started = clock.millis();
        while (total < 0 || read < total) {
            int size = usb.bulkIn(ENDPOINT_IN, packet, 0, USB_PACKET);
            if (size < 0) {
                return TiLink.ERROR_USB;
            }
            if (size == 0 && clock.millis() - started > TIMEOUT_MILLIS) {
                return TiLink.ERROR_TIMEOUT;
            }
            for (int i = 0; i < size && kept < receive.length; i++) {
                receive[kept++] = packet[i];
            }
            read += size;
            if (total < 0 && kept >= RAW_HEADER) {
                total = RAW_HEADER + getInt(receive, 0);
            }
        }
        receivedType = receive[4] & 0xFF;
        receivedLength = kept;
        return TiLink.ERROR_NONE;
    }

    static void putInt(byte[] target, int at, int value) {
        target[at] = (byte) (value >> 24);
        target[at + 1] = (byte) (value >> 16);
        target[at + 2] = (byte) (value >> 8);
        target[at + 3] = (byte) value;
    }

    static int putShort(byte[] target, int at, int value) {
        target[at] = (byte) (value >> 8);
        target[at + 1] = (byte) value;
        return at + 2;
    }

    private static int getInt(byte[] source, int at) {
        return (source[at] & 0xFF) << 24 | (source[at + 1] & 0xFF) << 16 | (source[at + 2] & 0xFF) << 8
                | (source[at + 3] & 0xFF);
    }

    private static int getShort(byte[] source, int at) {
        return (source[at] & 0xFF) << 8 | (source[at + 1] & 0xFF);
    }
}
