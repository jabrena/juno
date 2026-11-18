package io.github.jabrena.juno.hardware.device.usb;

/**
 * A Silicon Labs CP210x USB-to-UART bridge (CP2102, CP2104, ...) attached to a {@link UsbHostShield}: the chip on the
 * RoboPeak RPLIDAR adapter and on many ESP32 and sensor boards. It opens the bridge at 8N1 and a chosen baud rate,
 * moves bytes over the bridge's bulk endpoints, and drives its DTR and RTS lines.
 *
 * <pre>{@code
 * UsbHostShield usb = new UsbHostShield();
 * Cp210xSerial serial = new Cp210xSerial(usb);       // once, at startup
 * if (usb.begin() && serial.begin(115_200)) {
 *     int value = serial.read();                      // -1 when nothing arrived
 * }
 * }</pre>
 *
 * <p>Allocation: the constructor allocates a 64-byte configuration buffer, a 64-byte receive buffer and a 4-byte request
 * buffer; reading and writing are allocation-free. Nothing arrives unless the program calls {@link #read()} (or
 * {@link #available()}) often enough: the bridge only buffers about 576 bytes, half a second at 115200 baud.
 */
public final class Cp210xSerial {
    /** Silicon Labs' USB vendor ID, shared by every CP210x. */
    public static final int VENDOR_ID = 0x10C4;

    /** Vendor request to the interface, host to device: every CP210x configuration request. */
    private static final int REQUEST_TYPE_VENDOR_OUT = 0x41;
    private static final int IFC_ENABLE = 0x00;
    private static final int SET_LINE_CTL = 0x03;
    private static final int SET_MHS = 0x07;
    private static final int SET_BAUDRATE = 0x1E;
    private static final int PURGE = 0x12;
    private static final int UART_ENABLE = 0x0001;
    /** Eight data bits (bits 15..8), no parity (7..4), one stop bit (3..0). */
    private static final int LINE_8N1 = 0x0800;
    private static final int PURGE_ALL = 0x000F;
    private static final int MHS_DTR = 0x0001;
    private static final int MHS_RTS = 0x0002;
    private static final int MHS_DTR_MASK = 0x0100;
    private static final int MHS_RTS_MASK = 0x0200;

    private static final int DESCRIPTOR_ENDPOINT = 0x05;
    private static final int ENDPOINT_IN = 0x80;
    private static final int TRANSFER_BULK = 0x02;
    private static final int CONFIGURATION_LENGTH = 64;
    private static final int PACKET_LENGTH = 64;

    private final UsbHostShield usb;
    private final byte[] configuration = new byte[CONFIGURATION_LENGTH];
    private final byte[] packet = new byte[PACKET_LENGTH];
    private final byte[] request = new byte[4];
    private int interfaceNumber;
    private int inEndpoint;
    private int outEndpoint;
    private int inPacketSize = PACKET_LENGTH;
    private int outPacketSize = PACKET_LENGTH;
    /** The next unread byte of {@link #packet}, and one past the last. */
    private int packetPosition;
    private int packetLimit;
    private int lastError;
    private boolean open;

    /** A bridge reached through {@code usb}, which must be started ({@link UsbHostShield#begin()}) before use. */
    public Cp210xSerial(UsbHostShield usb) {
        this.usb = usb;
    }

    /**
     * Enumerates the device on the shield, checks it is a CP210x, selects its configuration, and opens its UART at
     * {@code baudRate}, 8N1, with DTR and RTS low. Returns {@code false} when no device is connected, it is not a CP210x,
     * it has no bulk endpoints, or it refuses a request; {@link #lastError()} then holds the failing code.
     */
    public boolean begin(int baudRate) {
        open = false;
        packetPosition = 0;
        packetLimit = 0;
        if (!usb.attach()) {
            return fail(UsbHostShield.ERROR_NOT_ATTACHED);
        }
        if (usb.vendorId() != VENDOR_ID) {
            return fail(UsbHostShield.ERROR_STALL);
        }
        int length = usb.configurationDescriptor(configuration, CONFIGURATION_LENGTH);
        if (length < 9) {
            return fail(length < 0 ? length : UsbHostShield.ERROR_NO_DATA);
        }
        if (!findEndpoints(length)) {
            return fail(UsbHostShield.ERROR_NO_DATA);
        }
        int result = usb.setConfiguration(configuration[5] & 0xFF);
        if (result < 0) {
            return fail(result);
        }
        result = vendorRequest(IFC_ENABLE, UART_ENABLE);
        if (result >= 0) {
            result = sendBaudRate(baudRate);
        }
        if (result >= 0) {
            result = vendorRequest(SET_LINE_CTL, LINE_8N1);
        }
        if (result >= 0) {
            result = vendorRequest(SET_MHS, MHS_DTR_MASK | MHS_RTS_MASK);
        }
        if (result >= 0) {
            result = vendorRequest(PURGE, PURGE_ALL);
        }
        if (result < 0) {
            return fail(result);
        }
        open = true;
        lastError = 0;
        return true;
    }

    /**
     * Whether {@link #begin(int)} opened the bridge and no transfer has failed since: a failed bulk transfer (the
     * device unplugged, or it stopped answering) closes it, and {@link #begin(int)} opens it again.
     */
    public boolean isOpen() {
        return open;
    }

    /** The last negative {@link UsbHostShield} code a call failed with, or 0. */
    public int lastError() {
        return lastError;
    }

    /** Changes the UART's baud rate. Returns {@code false} when the bridge refuses it. */
    public boolean setBaudRate(int baudRate) {
        return sendBaudRate(baudRate) >= 0;
    }

    /** Drives the DTR line: {@code true} is the asserted (active, electrically low) state. */
    public boolean setDtr(boolean asserted) {
        return vendorRequest(SET_MHS, MHS_DTR_MASK | (asserted ? MHS_DTR : 0)) >= 0;
    }

    /** Drives the RTS line: {@code true} is the asserted (active, electrically low) state. */
    public boolean setRts(boolean asserted) {
        return vendorRequest(SET_MHS, MHS_RTS_MASK | (asserted ? MHS_RTS : 0)) >= 0;
    }

    /** Drops every byte the bridge and this driver hold, in both directions. */
    public void flush() {
        packetPosition = 0;
        packetLimit = 0;
        vendorRequest(PURGE, PURGE_ALL);
    }

    /**
     * The bytes ready to {@link #read()} without asking the bridge again: when none are buffered, polls the bridge for
     * one packet first. Returns 0 when nothing arrived, or a negative code when the device stopped answering (which
     * closes the bridge).
     */
    public int available() {
        if (packetPosition < packetLimit) {
            return packetLimit - packetPosition;
        }
        if (!open) {
            return UsbHostShield.ERROR_NOT_ATTACHED;
        }
        int received = usb.bulkIn(inEndpoint, packet, 0, inPacketSize);
        packetPosition = 0;
        if (received < 0) {
            packetLimit = 0;
            lastError = received;
            open = false;
            return received;
        }
        packetLimit = received;
        return received;
    }

    /** The next received byte, {@code 0..255}, or -1 when nothing arrived (or the device stopped answering). */
    public int read() {
        if (available() <= 0) {
            return -1;
        }
        int value = packet[packetPosition] & 0xFF;
        packetPosition++;
        return value;
    }

    /**
     * Copies up to {@code length} received bytes into {@code buffer[offset]}, polling the bridge at most once.
     * Returns the bytes copied (0 when nothing arrived), or a negative code when the device stopped answering.
     */
    public int read(byte[] buffer, int offset, int length) {
        int ready = available();
        if (ready <= 0) {
            return ready;
        }
        int copied = ready < length ? ready : length;
        for (int i = 0; i < copied; i++) {
            buffer[offset + i] = packet[packetPosition + i];
        }
        packetPosition += copied;
        return copied;
    }

    /** Sends one byte. Returns {@code false} when the device refused it. */
    public boolean write(int value) {
        request[0] = (byte) value;
        return write(request, 0, 1);
    }

    /** Sends {@code length} bytes from {@code data[offset]}. Returns {@code false} when the device refused them. */
    public boolean write(byte[] data, int offset, int length) {
        if (!open) {
            return false;
        }
        int sent = usb.bulkOut(outEndpoint, data, offset, length, outPacketSize);
        if (sent < 0) {
            lastError = sent;
            open = false;
            return false;
        }
        return true;
    }

    /** Walks the configuration descriptor for the first interface's bulk IN and bulk OUT endpoints. */
    private boolean findEndpoints(int length) {
        inEndpoint = 0;
        outEndpoint = 0;
        int at = 0;
        boolean interfaceSeen = false;
        while (at + 1 < length) {
            int descriptorLength = configuration[at] & 0xFF;
            if (descriptorLength == 0) {
                break;
            }
            int type = configuration[at + 1] & 0xFF;
            if (type == 0x04 && !interfaceSeen && at + 2 < length) {
                interfaceSeen = true;
                interfaceNumber = configuration[at + 2] & 0xFF;
            } else if (type == DESCRIPTOR_ENDPOINT && at + 5 < length
                    && (configuration[at + 3] & 0x03) == TRANSFER_BULK) {
                int endpointAddress = configuration[at + 2] & 0xFF;
                int size = (configuration[at + 4] & 0xFF) | ((configuration[at + 5] & 0xFF) << 8);
                int packetSize = size < PACKET_LENGTH ? size : PACKET_LENGTH;
                if ((endpointAddress & ENDPOINT_IN) != 0 && inEndpoint == 0) {
                    inEndpoint = endpointAddress & 0x0F;
                    inPacketSize = packetSize;
                } else if ((endpointAddress & ENDPOINT_IN) == 0 && outEndpoint == 0) {
                    outEndpoint = endpointAddress & 0x0F;
                    outPacketSize = packetSize;
                }
            }
            at += descriptorLength;
        }
        return inEndpoint != 0 && outEndpoint != 0;
    }

    private int sendBaudRate(int baudRate) {
        request[0] = (byte) baudRate;
        request[1] = (byte) (baudRate >> 8);
        request[2] = (byte) (baudRate >> 16);
        request[3] = (byte) (baudRate >> 24);
        return usb.controlOut(REQUEST_TYPE_VENDOR_OUT, SET_BAUDRATE, 0, interfaceNumber, request, 4);
    }

    private int vendorRequest(int request, int value) {
        return usb.controlOut(REQUEST_TYPE_VENDOR_OUT, request, value, interfaceNumber, this.request, 0);
    }

    private boolean fail(int error) {
        lastError = error;
        return false;
    }
}
