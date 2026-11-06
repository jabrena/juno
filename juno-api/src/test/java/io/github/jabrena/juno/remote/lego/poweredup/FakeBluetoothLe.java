package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.net.BluetoothLe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A fake radio that plays one advertising hub: {@link #connect} succeeds with the hub's manufacturer data, every write is
 * recorded as a hex frame, and {@link #notify} queues a notification for the next {@link #read}.
 */
final class FakeBluetoothLe implements BluetoothLe {
    /** A City Hub's manufacturer data: LEGO company id 0x0397, button state 0, system type 0x41, then padding. */
    static final byte[] CITY_HUB_ADVERTISEMENT = {(byte) 0x97, 0x03, 0x00, 0x41, 0x06, 0x00, 0x41, 0x00};

    private final Deque<byte[]> notifications = new ArrayDeque<>();
    private byte[] advertisement = CITY_HUB_ADVERTISEMENT;
    private final List<String> frames = new ArrayList<>();
    private boolean advertising = true;
    private boolean connected;
    private int connects;

    /** Makes the hub advertise (the default) or go out of range, so {@link #connect} times out. */
    void setAdvertising(boolean advertising) {
        this.advertising = advertising;
    }

    /** Sets the manufacturer data the hub advertises from now on, e.g. a Duplo Train Hub's type. */
    void setAdvertisement(int... bytes) {
        advertisement = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            advertisement[i] = (byte) bytes[i];
        }
    }

    /** Queues one notification, as the hub sends it. */
    void notify(int... bytes) {
        byte[] message = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            message[i] = (byte) bytes[i];
        }
        notifications.add(message);
    }

    /** Every frame written to the hub so far, each as space-separated hex bytes. */
    List<String> frames() {
        return frames;
    }

    /** How many times the radio has connected. */
    int connects() {
        return connects;
    }

    @Override
    public int connect(String serviceUuid, String characteristicUuid, int timeoutMillis, byte[] advertisement,
            int capacity) {
        if (!advertising) {
            return -1;
        }
        connected = true;
        connects++;
        int length = Math.min(capacity, this.advertisement.length);
        System.arraycopy(this.advertisement, 0, advertisement, 0, length);
        return length;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void write(byte[] message, int length) {
        if (!connected) {
            return;
        }
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                hex.append(' ');
            }
            hex.append(String.format("%02X", message[i] & 0xFF));
        }
        frames.add(hex.toString());
    }

    @Override
    public int read(byte[] buffer, int capacity) {
        byte[] next = notifications.poll();
        if (next == null) {
            return 0;
        }
        int count = Math.min(next.length, Math.min(capacity, buffer.length));
        System.arraycopy(next, 0, buffer, 0, count);
        return count;
    }

    @Override
    public void disconnect() {
        connected = false;
    }
}
