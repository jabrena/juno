package io.github.jabrena.juno.remote.lego.poweredup;

/**
 * The hub's properties (Hub Properties messages): battery, button, signal strength, firmware, hardware and name. A value
 * is unknown until the hub answers, so each starts at -1 or empty. The name is kept up to 20 bytes.
 */
final class LegoProperties {
    static final int NAME_BYTES = 20;
    private static final int UNKNOWN = -1;

    static final int PROPERTY_NAME = 0x01;
    static final int PROPERTY_BUTTON = 0x02;
    static final int PROPERTY_FIRMWARE = 0x03;
    static final int PROPERTY_HARDWARE = 0x04;
    static final int PROPERTY_RSSI = 0x05;
    static final int PROPERTY_BATTERY = 0x06;

    private final byte[] name = new byte[NAME_BYTES];
    private int nameLength;
    private int battery;
    private int button;
    private int rssi;
    private int firmware;
    private int hardware;
    private boolean requested;

    LegoProperties() {
        clear();
    }

    void clear() {
        battery = UNKNOWN;
        button = 0;
        rssi = 0;
        firmware = UNKNOWN;
        hardware = UNKNOWN;
        nameLength = 0;
        requested = false;
    }

    /** Whether the properties are still to be asked for: true once, the first time, and false afterwards. */
    boolean takeRequest() {
        if (requested) {
            return false;
        }
        requested = true;
        return true;
    }

    /** Whether every property has been answered, so a wait for them can end. */
    boolean complete() {
        return battery >= 0 && firmware >= 0 && hardware >= 0 && nameLength > 0;
    }

    /** Applies one Hub Properties update: {@code size} value bytes start at {@code offset} in {@code message}. */
    void receive(byte[] message, int offset, int size, int property) {
        switch (property) {
            case PROPERTY_NAME -> {
                nameLength = size < NAME_BYTES ? size : NAME_BYTES;
                for (int i = 0; i < nameLength; i++) {
                    name[i] = message[offset + i];
                }
            }
            case PROPERTY_BUTTON -> {
                if (size >= 1) {
                    button = message[offset] != 0 ? 1 : 0;
                }
            }
            case PROPERTY_FIRMWARE -> {
                if (size >= 4) {
                    firmware = LegoBytes.valueAt(message, offset, 4);
                }
            }
            case PROPERTY_HARDWARE -> {
                if (size >= 4) {
                    hardware = LegoBytes.valueAt(message, offset, 4);
                }
            }
            case PROPERTY_RSSI -> {
                if (size >= 1) {
                    rssi = message[offset];
                }
            }
            case PROPERTY_BATTERY -> {
                if (size >= 1) {
                    battery = LegoBytes.unsigned(message[offset]);
                }
            }
            default -> {
            }
        }
    }

    int battery() {
        return battery;
    }

    boolean button() {
        return button != 0;
    }

    int rssi() {
        return rssi;
    }

    int firmware() {
        return firmware;
    }

    int hardware() {
        return hardware;
    }

    /** Copies at most {@code room} bytes of the name into {@code buffer} and returns how many were copied. */
    int copyName(byte[] buffer, int room) {
        int count = nameLength < room ? nameLength : room;
        for (int i = 0; i < count; i++) {
            buffer[i] = name[i];
        }
        return count;
    }
}
