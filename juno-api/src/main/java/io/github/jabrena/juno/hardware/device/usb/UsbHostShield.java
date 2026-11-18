package io.github.jabrena.juno.hardware.device.usb;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.util.Delay;

import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HCTL;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HCTL_BUSRST;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HCTL_RCVTOG0;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HCTL_RCVTOG1;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HCTL_SAMPLEBUS;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HCTL_SNDTOG0;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HCTL_SNDTOG1;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HIRQ;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HIRQ_FRAMEIRQ;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HIRQ_RCVDAVIRQ;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HRSL;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HRSL_JSTATUS;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HRSL_KSTATUS;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HRSL_RCVTOGRD;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.HRSL_SNDTOGRD;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.MODE;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.MODE_HOST_IDLE;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.MODE_LOWSPEED;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.MODE_SOFKAENAB;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.PERADDR;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.RCVBC;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.RCVFIFO;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.REVISION;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.SNDBC;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.SNDFIFO;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.SUDFIFO;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.TOKEN_IN;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.TOKEN_IN_HANDSHAKE;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.TOKEN_OUT;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.TOKEN_OUT_HANDSHAKE;
import static io.github.jabrena.juno.hardware.device.usb.Max3421e.TOKEN_SETUP;

/**
 * The Arduino USB Host Shield: a Maxim MAX3421E USB host controller driven over {@link Spi} as plain Java, so it needs
 * no Arduino library. It hosts one full- or low-speed device plugged straight into the shield's USB-A socket (no hubs),
 * gives it address 1, and offers the control, bulk IN and bulk OUT transfers a device driver such as
 * {@link Cp210xSerial} builds on.
 *
 * <pre>{@code
 * UsbHostShield usb = new UsbHostShield();   // once, at startup
 * if (usb.begin() && usb.attach()) {
 *     // usb.vendorId(), usb.productId(), usb.controlIn(...), usb.bulkIn(...)
 * }
 * }</pre>
 *
 * <p>The shield reaches the controller through the ICSP header's SPI pins and selects it with D10; its interrupt line
 * on D9 is not used (the driver polls), so D9 stays free, but D10 to D13 do not. The device's 5 V comes from the
 * board: a device that draws a lot (a spinning lidar, a hard disk) may need the board powered from an external supply.
 *
 * <p>Allocation: the constructor allocates the 8-byte setup packet and two 16-entry toggle tables; every transfer is
 * allocation-free and writes into caller-owned buffers. Transfers return a byte count, or a negative {@code ERROR_*}
 * code; they never throw.
 *
 * @see <a href="https://www.analog.com/media/en/technical-documentation/user-guides/max3421e-programming-guide.pdf">
 *     MAX3421E programming guide</a>
 */
public final class UsbHostShield {
    /** The peripheral answered NAK more often than the transfer's limit allows. */
    public static final int ERROR_NAK = -4;
    /** The peripheral answered STALL: it does not support the request or the endpoint is halted. */
    public static final int ERROR_STALL = -5;
    /** The peripheral did not answer at all, three times in a row. */
    public static final int ERROR_TIMEOUT = -14;
    /** The controller never reported the transfer as done: no device, or the SPI link is broken. */
    public static final int ERROR_NO_RESPONSE = -16;
    /** An IN transfer completed without data in the receive FIFO. */
    public static final int ERROR_NO_DATA = -17;
    /** No device is attached and enumerated: call {@link #attach()} first. */
    public static final int ERROR_NOT_ATTACHED = -18;

    /** Standard request {@code bmRequestType} of a device-to-host (IN) standard request to the device. */
    public static final int REQUEST_TYPE_STANDARD_IN = 0x80;
    /** Standard request {@code bmRequestType} of a host-to-device (OUT) standard request to the device. */
    public static final int REQUEST_TYPE_STANDARD_OUT = 0x00;
    public static final int REQUEST_GET_DESCRIPTOR = 0x06;
    public static final int REQUEST_SET_ADDRESS = 0x05;
    public static final int REQUEST_SET_CONFIGURATION = 0x09;
    public static final int DESCRIPTOR_DEVICE = 0x01;
    public static final int DESCRIPTOR_CONFIGURATION = 0x02;

    /** The address this host gives the one device it supports. */
    public static final int DEVICE_ADDRESS = 1;

    /** NAKs a control transfer tolerates; a device answers a control request in well under a second. */
    private static final int CONTROL_NAK_LIMIT = 20_000;
    private static final int TOGGLE_RETRY_LIMIT = 3;
    /** USB 2.0 asks the host to wait 100 ms after connection before resetting the bus. */
    private static final int ATTACH_SETTLE_MILLIS = 200;
    private static final int RESET_RECOVERY_MILLIS = 20;
    private static final int SET_ADDRESS_RECOVERY_MILLIS = 20;
    private static final int DEFAULT_MAX_PACKET_0 = 8;
    private static final int ENDPOINTS = 16;
    private static final int DEVICE_DESCRIPTOR_LENGTH = 18;

    private final Max3421e chip;
    private final byte[] setupPacket = new byte[8];
    private final byte[] descriptor = new byte[DEVICE_DESCRIPTOR_LENGTH];
    /** The DATA0/DATA1 toggle each IN endpoint expects next. */
    private final int[] inToggles = new int[ENDPOINTS];
    /** The DATA0/DATA1 toggle each OUT endpoint sends next. */
    private final int[] outToggles = new int[ENDPOINTS];
    private int address;
    private int maxPacket0 = DEFAULT_MAX_PACKET_0;
    private int vendorId;
    private int productId;
    private boolean attached;
    private boolean lowSpeed;

    /** A shield on the board's own SPI bus, selected by D10. Create it once, at startup. */
    public UsbHostShield() {
        this(MCU.getGpio(), MCU.getSpi());
    }

    /** A shield selected through {@code gpio} and reached over {@code spi}: fakes in a unit test. */
    public UsbHostShield(Gpio gpio, Spi spi) {
        chip = new Max3421e(gpio, spi, gpio.digitalPin("D10"));
    }

    /**
     * Starts the bus, resets the controller and puts it in host mode. Returns {@code false} when the controller does
     * not come back from its reset or does not identify itself: the shield is missing or not seated on the ICSP header.
     */
    public boolean begin() {
        attached = false;
        if (!chip.reset()) {
            return false;
        }
        chip.write(MODE, MODE_HOST_IDLE);
        return true;
    }

    /** The controller's silicon revision: {@code 0x12} or {@code 0x13} on a working shield, anything else when not. */
    public int revision() {
        return chip.read(REVISION);
    }

    /** Samples the bus and reports whether a device holds one of its data lines up, connected or not enumerated. */
    public boolean isDeviceConnected() {
        return busState() != 0;
    }

    /**
     * Waits for the bus to settle, resets it, and enumerates the connected device: reads its device descriptor, gives
     * it {@link #DEVICE_ADDRESS}, and reads the descriptor again for {@link #vendorId()} and {@link #productId()}.
     * Returns {@code false} when no device is connected or it does not answer. Takes about a quarter of a second.
     */
    public boolean attach() {
        attached = false;
        address = 0;
        maxPacket0 = DEFAULT_MAX_PACKET_0;
        if (!isDeviceConnected()) {
            return false;
        }
        Delay.millis(ATTACH_SETTLE_MILLIS);
        resetBus();
        clearToggles();
        attached = true;
        if (controlIn(REQUEST_TYPE_STANDARD_IN, REQUEST_GET_DESCRIPTOR, DESCRIPTOR_DEVICE << 8, 0, descriptor, 8) < 8) {
            attached = false;
            return false;
        }
        maxPacket0 = descriptor[7] & 0xFF;
        if (controlOut(REQUEST_TYPE_STANDARD_OUT, REQUEST_SET_ADDRESS, DEVICE_ADDRESS, 0, descriptor, 0) < 0) {
            attached = false;
            return false;
        }
        Delay.millis(SET_ADDRESS_RECOVERY_MILLIS);
        address = DEVICE_ADDRESS;
        if (controlIn(REQUEST_TYPE_STANDARD_IN, REQUEST_GET_DESCRIPTOR, DESCRIPTOR_DEVICE << 8, 0, descriptor,
                DEVICE_DESCRIPTOR_LENGTH) < DEVICE_DESCRIPTOR_LENGTH) {
            attached = false;
            return false;
        }
        vendorId = word(descriptor, 8);
        productId = word(descriptor, 10);
        return true;
    }

    /** Whether {@link #attach()} enumerated a device. */
    public boolean isAttached() {
        return attached;
    }

    /** Whether the attached device is a low-speed (1.5 Mbit/s) one rather than full speed (12 Mbit/s). */
    public boolean isLowSpeed() {
        return lowSpeed;
    }

    /** The attached device's USB vendor ID, from its device descriptor. */
    public int vendorId() {
        return vendorId;
    }

    /** The attached device's USB product ID, from its device descriptor. */
    public int productId() {
        return productId;
    }

    /** The largest packet the attached device's control endpoint takes, from its device descriptor. */
    public int maxPacketSize0() {
        return maxPacket0;
    }

    /**
     * Reads the device's configuration descriptor, with its interfaces and endpoints, into {@code buffer}: up to
     * {@code length} bytes. Returns the bytes read, or a negative {@code ERROR_*} code.
     */
    public int configurationDescriptor(byte[] buffer, int length) {
        return controlIn(REQUEST_TYPE_STANDARD_IN, REQUEST_GET_DESCRIPTOR, DESCRIPTOR_CONFIGURATION << 8, 0, buffer,
                length);
    }

    /** Selects configuration {@code value} and restarts every endpoint's data toggle. Returns 0 or an error code. */
    public int setConfiguration(int value) {
        int result = controlOut(REQUEST_TYPE_STANDARD_OUT, REQUEST_SET_CONFIGURATION, value, 0, setupPacket, 0);
        clearToggles();
        return result;
    }

    /**
     * A control transfer that reads up to {@code length} bytes from the device into {@code buffer}: setup, data IN
     * and status stages. Returns the bytes read, or a negative {@code ERROR_*} code.
     */
    public int controlIn(int requestType, int request, int value, int index, byte[] buffer, int length) {
        if (!attached) {
            return ERROR_NOT_ATTACHED;
        }
        chip.write(PERADDR, address);
        int result = setup(requestType, request, value, index, length);
        if (result < 0) {
            return result;
        }
        inToggles[0] = 1;
        int received = receive(0, buffer, 0, length, maxPacket0, CONTROL_NAK_LIMIT);
        if (received < 0) {
            return received;
        }
        int status = chip.dispatch(TOKEN_OUT_HANDSHAKE, 0, CONTROL_NAK_LIMIT, 0, 0);
        return status < 0 ? status : received;
    }

    /**
     * A control transfer that sends the first {@code length} bytes of {@code data} to the device (none when
     * {@code length} is 0): setup, data OUT and status stages. Returns 0, or a negative {@code ERROR_*} code.
     */
    public int controlOut(int requestType, int request, int value, int index, byte[] data, int length) {
        if (!attached) {
            return ERROR_NOT_ATTACHED;
        }
        chip.write(PERADDR, address);
        int result = setup(requestType, request, value, index, length);
        if (result < 0) {
            return result;
        }
        if (length > 0) {
            outToggles[0] = 1;
            result = send(0, data, 0, length, maxPacket0, CONTROL_NAK_LIMIT);
            if (result < 0) {
                return result;
            }
        }
        result = chip.dispatch(TOKEN_IN_HANDSHAKE, 0, CONTROL_NAK_LIMIT, 0, 0);
        return result < 0 ? result : 0;
    }

    /**
     * Asks bulk IN {@code endpoint} (its number, 1 to 15, without the direction bit) for one packet and stores it at
     * {@code buffer[offset]}. Does not wait: returns 0 when the device has nothing to send (it answered NAK), the bytes
     * received, or a negative {@code ERROR_*} code. {@code buffer} needs room for {@code maxPacketSize} bytes from
     * {@code offset}; a longer packet is cut.
     */
    public int bulkIn(int endpoint, byte[] buffer, int offset, int maxPacketSize) {
        if (!attached) {
            return ERROR_NOT_ATTACHED;
        }
        chip.write(PERADDR, address);
        int received = receive(endpoint, buffer, offset, maxPacketSize, maxPacketSize, 1);
        return received == ERROR_NAK ? 0 : received;
    }

    /**
     * Sends {@code length} bytes from {@code data[offset]} to bulk OUT {@code endpoint}, in packets of up to
     * {@code maxPacketSize} bytes. Returns the bytes sent, or a negative {@code ERROR_*} code.
     */
    public int bulkOut(int endpoint, byte[] data, int offset, int length, int maxPacketSize) {
        if (!attached) {
            return ERROR_NOT_ATTACHED;
        }
        chip.write(PERADDR, address);
        return send(endpoint, data, offset, length, maxPacketSize, CONTROL_NAK_LIMIT);
    }

    /** Resets the bus, then switches on SOF (or low-speed keep-alive) generation for the detected speed. */
    private void resetBus() {
        chip.write(HCTL, HCTL_BUSRST);
        for (int poll = 0; poll < Max3421e.POLLS && (chip.read(HCTL) & HCTL_BUSRST) != 0; poll++) {
            // the controller clears BUSRST after 50 ms
        }
        int state = busState();
        lowSpeed = state == HRSL_KSTATUS;
        chip.write(MODE, MODE_HOST_IDLE | MODE_SOFKAENAB | (lowSpeed ? MODE_LOWSPEED : 0));
        chip.write(HIRQ, HIRQ_FRAMEIRQ);
        chip.waitFor(HIRQ, HIRQ_FRAMEIRQ);
        Delay.millis(RESET_RECOVERY_MILLIS);
    }

    /**
     * The bus lines in full-speed terms: {@code JSTATUS} for an idle full-speed device, {@code KSTATUS}
     * for an idle low-speed one, 0 for none. Before enumeration it is sampled with the low-speed bit cleared, so J and
     * K are not swapped; once a device is attached the mode (and its start-of-frame packets) is left alone.
     */
    private int busState() {
        if (!attached) {
            chip.write(MODE, MODE_HOST_IDLE);
        }
        chip.write(HCTL, HCTL_SAMPLEBUS);
        Delay.millis(1);
        int state = chip.read(HRSL) & (HRSL_JSTATUS | HRSL_KSTATUS);
        return state == (HRSL_JSTATUS | HRSL_KSTATUS) ? 0 : state;
    }

    private void clearToggles() {
        for (int endpoint = 0; endpoint < ENDPOINTS; endpoint++) {
            inToggles[endpoint] = 0;
            outToggles[endpoint] = 0;
        }
    }

    private int setup(int requestType, int request, int value, int index, int length) {
        setupPacket[0] = (byte) requestType;
        setupPacket[1] = (byte) request;
        setupPacket[2] = (byte) value;
        setupPacket[3] = (byte) (value >> 8);
        setupPacket[4] = (byte) index;
        setupPacket[5] = (byte) (index >> 8);
        setupPacket[6] = (byte) length;
        setupPacket[7] = (byte) (length >> 8);
        chip.writeFifo(SUDFIFO, setupPacket, 0, 8);
        return chip.dispatch(TOKEN_SETUP, 0, CONTROL_NAK_LIMIT, 0, 0);
    }

    /** Reads packets from IN {@code endpoint} until a short packet or {@code length} bytes; tracks the data toggle. */
    private int receive(int endpoint, byte[] buffer, int offset, int length, int maxPacket, int nakLimit) {
        int received = 0;
        int toggleRetries = 0;
        while (true) {
            chip.write(HCTL, inToggles[endpoint] != 0 ? HCTL_RCVTOG1 : HCTL_RCVTOG0);
            int result = chip.dispatch(TOKEN_IN, endpoint, nakLimit, 0, 0);
            if (result == -Max3421e.RESULT_TOGERR && toggleRetries < TOGGLE_RETRY_LIMIT) {
                toggleRetries++;
                inToggles[endpoint] = 1 - inToggles[endpoint];
                continue;
            }
            if (result < 0) {
                return result;
            }
            if ((chip.read(HIRQ) & HIRQ_RCVDAVIRQ) == 0) {
                return ERROR_NO_DATA;
            }
            int size = chip.read(RCVBC);
            int room = length - received;
            int copied = size < room ? size : room;
            chip.readFifo(RCVFIFO, buffer, offset + received, copied);
            chip.write(HIRQ, HIRQ_RCVDAVIRQ);
            received += copied;
            inToggles[endpoint] = (chip.read(HRSL) & HRSL_RCVTOGRD) != 0 ? 1 : 0;
            if (size < maxPacket || received >= length) {
                return received;
            }
        }
    }

    /** Writes {@code length} bytes to OUT {@code endpoint} in packets; tracks the data toggle. */
    private int send(int endpoint, byte[] data, int offset, int length, int maxPacket, int nakLimit) {
        chip.write(HCTL, outToggles[endpoint] != 0 ? HCTL_SNDTOG1 : HCTL_SNDTOG0);
        int sent = 0;
        while (sent < length) {
            int remaining = length - sent;
            int chunk = remaining < maxPacket ? remaining : maxPacket;
            chip.writeFifo(SNDFIFO, data, offset + sent, chunk);
            chip.write(SNDBC, chunk);
            int result = chip.dispatch(TOKEN_OUT, endpoint, nakLimit, data[offset + sent] & 0xFF, chunk);
            if (result < 0) {
                return result;
            }
            sent += chunk;
        }
        outToggles[endpoint] = (chip.read(HRSL) & HRSL_SNDTOGRD) != 0 ? 1 : 0;
        return sent;
    }

    private static int word(byte[] data, int at) {
        return (data[at] & 0xFF) | ((data[at + 1] & 0xFF) << 8);
    }
}
