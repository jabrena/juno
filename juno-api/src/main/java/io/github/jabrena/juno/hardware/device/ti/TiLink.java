package io.github.jabrena.juno.hardware.device.ti;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.usb.UsbHostShield;
import io.github.jabrena.juno.hardware.time.Clock;

import static io.github.jabrena.juno.hardware.device.ti.DusbTransport.DATA;
import static io.github.jabrena.juno.hardware.device.ti.DusbTransport.putInt;
import static io.github.jabrena.juno.hardware.device.ti.DusbTransport.putShort;

/**
 * A TI-84 Plus family calculator on the USB Host Shield, reached with its ordinary computer cable: the board plays the
 * part of TI Connect and sends variables to the calculator over TI's USB link protocol ("DUSB"), so a program can put
 * sensor readings into the lists {@code L1} to {@code L6} for a STAT PLOT.
 *
 * <pre>{@code
 * TiLink calculator = new TiLink();               // once, at startup
 * int[] temperatures = new int[TiLink.MAX_LIST_LENGTH];
 * if (calculator.connect()) {
 *     calculator.sendList(2, temperatures, count, 0);   // L2 = temperatures[0..count), no decimals
 * }
 * }</pre>
 *
 * <p>{@link DusbTransport} carries the packets; this class speaks the commands: mode set, request to send (name, size
 * and the type, archived and version attributes), variable contents and end of transmission. The calculator answers
 * a request with a data acknowledgement, or delay acknowledgements first while it is busy. It takes variables at the
 * home screen and in most menus; an app such as EasyData may keep it busy until it is closed.
 *
 * <p>Allocation: the constructor allocates the transport's buffers; {@link #connect()} and {@link #sendList} are
 * allocation-free. Calls never throw: they return {@code false} and {@link #lastError()} says which step failed.
 */
public final class TiLink {
    public static final int VENDOR_ID = 0x0451;
    /** The TI-84 Plus family on DUSB: the TI-84 Plus, Silver Edition, C Silver Edition and CE. */
    public static final int PRODUCT_ID_TI84_PLUS = 0xE008;
    /** The most values {@link #sendList} sends, so a whole list fits one raw packet. */
    public static final int MAX_LIST_LENGTH = 100;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_NO_SHIELD = -1;
    public static final int ERROR_NO_DEVICE = -2;
    public static final int ERROR_NOT_A_TI84 = -3;
    public static final int ERROR_USB = -4;
    public static final int ERROR_TIMEOUT = -5;
    /** The calculator answered with a packet this driver did not expect: see {@link #lastPacketType()}. */
    public static final int ERROR_PROTOCOL = -6;
    /** The calculator refused the variable: see {@link #lastCalculatorError()}. */
    public static final int ERROR_REJECTED = -7;
    public static final int ERROR_ARGUMENT = -8;

    static final int VPKT_PING = 0x0001;
    static final int VPKT_RTS = 0x000B;
    static final int VPKT_VAR_CONTENTS = 0x000D;
    static final int VPKT_MODE_SET = 0x0012;
    static final int VPKT_DATA_ACK = 0xAA00;
    static final int VPKT_DELAY_ACK = 0xBB00;
    static final int VPKT_EOT = 0xDD00;
    static final int VPKT_ERROR = 0xEE00;

    static final int TYPE_REAL_LIST = 0x01;
    /**
     * The second byte of the variable-type attribute on the TI-83/84 Plus CE (libticalcs sends 0x0B for the older USB
     * models and 0x07 for the rest); with another value the CE rejects the name.
     */
    static final byte TYPE_HEADER_CE = 0x0F;

    private static final int REQUESTED_BUFFER = 1024;
    /** Delay acknowledgements tolerated in a row before giving up: the calculator is busy. */
    private static final int DELAY_LIMIT = 20;
    /** Mode "normal": the {mode, submode, flags, reserved, timeout} words TI Connect sends before a transfer. */
    private static final byte[] MODE_NORMAL = {0, 3, 0, 1, 0, 0, 0, 0, 0x07, (byte) 0xD0};

    private final UsbHostShield usb;
    private final DusbTransport transport;
    private final TiReal real = new TiReal();
    private boolean connected;
    private int lastError;
    private int lastCalculatorError;

    /** A calculator on the board's USB Host Shield, timed by the board's clock. Create it once, at startup. */
    public TiLink() {
        this(new UsbHostShield(), MCU.getClock());
    }

    /** A calculator on {@code usb}, timed by {@code clock}: fakes in a unit test. */
    public TiLink(UsbHostShield usb, Clock clock) {
        this.usb = usb;
        this.transport = new DusbTransport(usb, clock, REQUESTED_BUFFER);
    }

    /**
     * Starts the shield, enumerates the calculator, checks it is a TI-84 Plus family model, selects its configuration,
     * agrees on the raw packet size and puts it in normal mode. Returns {@code false} with {@link #lastError()} set
     * when any step fails.
     */
    public boolean connect() {
        connected = false;
        if (!usb.begin()) {
            return fail(ERROR_NO_SHIELD);
        }
        if (!usb.attach()) {
            return fail(ERROR_NO_DEVICE);
        }
        if (usb.vendorId() != VENDOR_ID || usb.productId() != PRODUCT_ID_TI84_PLUS) {
            return fail(ERROR_NOT_A_TI84);
        }
        if (usb.setConfiguration(1) < 0) {
            return fail(ERROR_USB);
        }
        int result = transport.negotiateBufferSize(REQUESTED_BUFFER);
        if (result == ERROR_NONE) {
            byte[] buffer = transport.transmitBuffer();
            for (int i = 0; i < MODE_NORMAL.length; i++) {
                buffer[DATA + i] = MODE_NORMAL[i];
            }
            result = transport.sendVirtual(VPKT_PING, MODE_NORMAL.length);
        }
        if (result == ERROR_NONE) {
            result = expect(VPKT_MODE_SET);
        }
        if (result != ERROR_NONE) {
            return fail(result);
        }
        connected = true;
        lastError = ERROR_NONE;
        return true;
    }

    /** Whether {@link #connect()} succeeded and no transfer has failed since. */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sends {@code count} values as the real list {@code L<list>} (1 to 6), replacing it: value {@code i} is
     * {@code values[i] / 10^decimals}, so {@code decimals} 1 sends 235 as 23.5. Returns {@code false} with
     * {@link #lastError()} set when the calculator does not take it.
     */
    public boolean sendList(int list, int[] values, int count, int decimals) {
        if (!beginList(list, count, values.length, decimals)) {
            return false;
        }
        byte[] buffer = transport.transmitBuffer();
        for (int i = 0; i < count; i++) {
            real.put(buffer, DATA + 2 + i * TiReal.BYTES, values[i], decimals);
        }
        return finishList(count);
    }

    /**
     * {@link #sendList(int, int[], int, int)} for values past an {@code int}, up to the calculator's fourteen digits:
     * a {@code YYYYMMDDHHMMSS} time stamp such as {@code 20261121163005L} arrives exactly.
     */
    public boolean sendList(int list, long[] values, int count, int decimals) {
        if (!beginList(list, count, values.length, decimals)) {
            return false;
        }
        byte[] buffer = transport.transmitBuffer();
        for (int i = 0; i < count; i++) {
            real.put(buffer, DATA + 2 + i * TiReal.BYTES, values[i], decimals);
        }
        return finishList(count);
    }

    /** Checks the arguments and sends the request to send; the caller then writes the reals after the count. */
    private boolean beginList(int list, int count, int available, int decimals) {
        if (list < 1 || list > 6 || count < 0 || count > MAX_LIST_LENGTH || count > available || decimals < 0) {
            lastError = ERROR_ARGUMENT;
            return false;
        }
        if (!connected) {
            return fail(ERROR_NO_DEVICE);
        }
        int result = transport.sendVirtual(VPKT_RTS, requestToSend(list, contentLength(count)));
        if (result == ERROR_NONE) {
            result = awaitDataAck();
        }
        if (result != ERROR_NONE) {
            return fail(result);
        }
        byte[] buffer = transport.transmitBuffer();
        buffer[DATA] = (byte) count;
        buffer[DATA + 1] = (byte) (count >> 8);
        return true;
    }

    /** Sends the list contents the caller wrote, waits for their acknowledgement and ends the transfer. */
    private boolean finishList(int count) {
        int result = transport.sendVirtual(VPKT_VAR_CONTENTS, contentLength(count));
        if (result == ERROR_NONE) {
            result = awaitDataAck();
        }
        if (result == ERROR_NONE) {
            result = transport.sendVirtual(VPKT_EOT, 0);
        }
        if (result != ERROR_NONE) {
            return fail(result);
        }
        lastError = ERROR_NONE;
        return true;
    }

    private static int contentLength(int count) {
        return 2 + count * TiReal.BYTES;
    }

    /** What the last failed call ran into: one of the {@code ERROR_*} codes, or {@link #ERROR_NONE}. */
    public int lastError() {
        return lastError;
    }

    /** The type of the last virtual packet the calculator sent, or of the raw packet it sent in its place. */
    public int lastPacketType() {
        return transport.virtualType();
    }

    /** The code of the calculator's last error packet, 0 when it sent none. */
    public int lastCalculatorError() {
        return lastCalculatorError;
    }

    /** The raw packet size the calculator granted in {@link #connect()}. */
    public int bufferSize() {
        return transport.bufferSize();
    }

    /**
     * Writes the request to send {@code L<list>} into the transmit buffer and returns its data length: the (empty)
     * folder, the name, the size, and the type, archived and version attributes.
     */
    private int requestToSend(int list, int contentLength) {
        byte[] buffer = transport.transmitBuffer();
        int at = DATA;
        // No folder: an empty folder is just its 0 length byte.
        buffer[at++] = 0;
        // Over USB the CE names variables in UTF-8, not with tokens: L1 is "L" and subscript one (U+2081).
        buffer[at++] = 4;
        buffer[at++] = 'L';
        buffer[at++] = (byte) 0xE2;
        buffer[at++] = (byte) 0x82;
        buffer[at++] = (byte) (0x80 + list);
        buffer[at++] = 0;
        putInt(buffer, at, contentLength);
        at += 4;
        buffer[at++] = 0x01;
        at = putShort(buffer, at, 3);
        at = attribute(buffer, at, 0x0002, 4);
        buffer[at++] = (byte) 0xF0;
        buffer[at++] = TYPE_HEADER_CE;
        buffer[at++] = 0x00;
        buffer[at++] = TYPE_REAL_LIST;
        at = attribute(buffer, at, 0x0003, 1);
        buffer[at++] = 0;
        at = attribute(buffer, at, 0x0008, 4);
        putInt(buffer, at, 0);
        return at + 4 - DATA;
    }

    private static int attribute(byte[] buffer, int at, int id, int size) {
        return putShort(buffer, putShort(buffer, at, id), size);
    }

    /** Waits for the calculator's data acknowledgement, through any delay acknowledgements it sends first. */
    private int awaitDataAck() {
        for (int delays = 0; delays <= DELAY_LIMIT; delays++) {
            int result = transport.receiveVirtual();
            if (result != ERROR_NONE) {
                return result;
            }
            int type = transport.virtualType();
            if (type == VPKT_DATA_ACK) {
                return ERROR_NONE;
            }
            if (type == VPKT_ERROR) {
                lastCalculatorError = transport.receivedShort(0);
                return ERROR_REJECTED;
            }
            if (type != VPKT_DELAY_ACK) {
                return ERROR_PROTOCOL;
            }
        }
        return ERROR_TIMEOUT;
    }

    private int expect(int type) {
        int result = transport.receiveVirtual();
        if (result != ERROR_NONE) {
            return result;
        }
        if (transport.virtualType() == VPKT_ERROR) {
            lastCalculatorError = transport.receivedShort(0);
            return ERROR_REJECTED;
        }
        return transport.virtualType() == type ? ERROR_NONE : ERROR_PROTOCOL;
    }

    private boolean fail(int error) {
        lastError = error;
        connected = false;
        return false;
    }
}
