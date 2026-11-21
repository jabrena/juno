package io.github.jabrena.juno.hardware.device.ti;

import io.github.jabrena.juno.hardware.device.usb.FakeUsbBus;
import io.github.jabrena.juno.hardware.device.usb.UsbHostShield;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A TI-84 Plus CE on a {@link FakeUsbBus}, so a real {@link UsbHostShield} enumerates it as {@code 0451:E008}: it
 * reassembles the raw packets the host writes to bulk OUT, answers them the way the real calculator did on hardware
 * (buffer size grant, raw acknowledgements, mode set, data acknowledgements) and queues each answer as its own bulk IN
 * transfers of up to 64 bytes. Tests read back the virtual packets it received.
 */
final class FakeCalculator {
    private static final int PACKET = 64;

    final FakeUsbBus bus = new FakeUsbBus();
    final UsbHostShield usb = new UsbHostShield(bus.gpio, bus);
    /** The raw packet size the calculator grants. */
    int grantedBuffer = 1023;
    /** Answer a request to send with this error code instead of a data acknowledgement (0 = accept). */
    int rejectWith;
    /** Delay acknowledgements sent before each data acknowledgement. */
    int delays;
    /** Answer the mode set with a data acknowledgement instead of a mode set acknowledgement. */
    boolean wrongModeAck;
    /** Answer the buffer size request with a raw acknowledgement instead of an allocation. */
    boolean wrongBufferAnswer;
    /** Stop answering altogether. */
    boolean silent;

    private final ByteArrayOutputStream fromHost = new ByteArrayOutputStream();
    private final ByteArrayOutputStream virtualData = new ByteArrayOutputStream();
    private final List<int[]> virtualTypes = new ArrayList<>();
    private final List<byte[]> virtualPackets = new ArrayList<>();

    FakeCalculator() {
        bus.vendorId = TiLink.VENDOR_ID;
        bus.productId = TiLink.PRODUCT_ID_TI84_PLUS;
        bus.bulkOutByte = value -> {
            fromHost.write(value);
            consume();
        };
    }

    /** The virtual packets the host sent, in order: their types. */
    List<Integer> virtualTypesReceived() {
        return virtualTypes.stream().map(type -> type[0]).toList();
    }

    /** The data of the {@code index}-th virtual packet the host sent. */
    byte[] virtualPacket(int index) {
        return virtualPackets.get(index);
    }

    /** The data of the last virtual packet of {@code type} the host sent. */
    byte[] lastVirtualPacket(int type) {
        for (int i = virtualTypes.size() - 1; i >= 0; i--) {
            if (virtualTypes.get(i)[0] == type) {
                return virtualPackets.get(i);
            }
        }
        throw new AssertionError("no virtual packet of type " + type);
    }

    /** Parses every complete raw packet the host has written so far. */
    private void consume() {
        byte[] bytes = fromHost.toByteArray();
        int at = 0;
        while (bytes.length - at >= 5) {
            int size = getInt(bytes, at);
            if (bytes.length - at < 5 + size) {
                break;
            }
            int type = bytes[at + 4] & 0xFF;
            raw(type, Arrays.copyOfRange(bytes, at + 5, at + 5 + size));
            at += 5 + size;
        }
        fromHost.reset();
        fromHost.write(bytes, at, bytes.length - at);
    }

    private void raw(int type, byte[] payload) {
        if (silent) {
            return;
        }
        switch (type) {
            case DusbTransport.RAW_BUFFER_SIZE_REQUEST -> {
                if (wrongBufferAnswer) {
                    queueRaw(DusbTransport.RAW_DATA_ACK, new byte[] {(byte) 0xE0, 0});
                } else {
                    queueRaw(DusbTransport.RAW_BUFFER_SIZE_ALLOCATION, intBytes(grantedBuffer));
                }
            }
            case DusbTransport.RAW_DATA, DusbTransport.RAW_DATA_LAST -> {
                queueRaw(DusbTransport.RAW_DATA_ACK, new byte[] {(byte) 0xE0, 0});
                virtualData.writeBytes(payload);
                if (type == DusbTransport.RAW_DATA_LAST) {
                    byte[] whole = virtualData.toByteArray();
                    virtualData.reset();
                    virtual(getShort(whole, 4), Arrays.copyOfRange(whole, 6, whole.length));
                }
            }
            default -> {
                // raw acknowledgements of the calculator's own packets need no answer
            }
        }
    }

    private void virtual(int type, byte[] data) {
        virtualTypes.add(new int[] {type});
        virtualPackets.add(data);
        switch (type) {
            case TiLink.VPKT_PING -> queueVirtual(wrongModeAck ? TiLink.VPKT_DATA_ACK : TiLink.VPKT_MODE_SET,
                    new byte[] {0, 0, 0, 0});
            case TiLink.VPKT_RTS -> {
                if (rejectWith != 0) {
                    queueVirtual(TiLink.VPKT_ERROR, new byte[] {(byte) (rejectWith >> 8), (byte) rejectWith});
                } else {
                    acknowledge();
                }
            }
            case TiLink.VPKT_VAR_CONTENTS -> acknowledge();
            default -> {
                // end of transmission: nothing to answer
            }
        }
    }

    private void acknowledge() {
        for (int i = 0; i < delays; i++) {
            queueVirtual(TiLink.VPKT_DELAY_ACK, new byte[] {0, 0, 0, 0});
        }
        queueVirtual(TiLink.VPKT_DATA_ACK, new byte[] {0, 1});
    }

    private void queueVirtual(int type, byte[] data) {
        byte[] payload = new byte[6 + data.length];
        System.arraycopy(intBytes(data.length), 0, payload, 0, 4);
        payload[4] = (byte) (type >> 8);
        payload[5] = (byte) type;
        System.arraycopy(data, 0, payload, 6, data.length);
        queueRaw(DusbTransport.RAW_DATA_LAST, payload);
    }

    private void queueRaw(int type, byte[] payload) {
        byte[] packet = new byte[5 + payload.length];
        System.arraycopy(intBytes(payload.length), 0, packet, 0, 4);
        packet[4] = (byte) type;
        System.arraycopy(payload, 0, packet, 5, payload.length);
        for (int at = 0; at < packet.length; at += PACKET) {
            int end = Math.min(packet.length, at + PACKET);
            int[] chunk = new int[end - at];
            for (int i = at; i < end; i++) {
                chunk[i - at] = packet[i] & 0xFF;
            }
            bus.receivePacket(chunk);
        }
    }

    private static byte[] intBytes(int value) {
        return new byte[] {(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    private static int getInt(byte[] source, int at) {
        return (source[at] & 0xFF) << 24 | (source[at + 1] & 0xFF) << 16 | (source[at + 2] & 0xFF) << 8
                | (source[at + 3] & 0xFF);
    }

    private static int getShort(byte[] source, int at) {
        return (source[at] & 0xFF) << 8 | (source[at + 1] & 0xFF);
    }
}
