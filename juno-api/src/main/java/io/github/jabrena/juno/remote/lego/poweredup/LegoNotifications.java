package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.net.BluetoothLe;

/**
 * Reads the hub's notifications off the radio and decodes each one into the state it belongs to: a port value into
 * {@link LegoSensors}, a property update into {@link LegoProperties}, a virtual port announcement into
 * {@link LegoVirtualPorts}, and a device type on a port into this class's own table. The read buffer is allocated once.
 */
final class LegoNotifications {
    private static final int EXTERNAL_PORTS = 4;
    private static final int MESSAGE_BYTES = 32;
    private static final int MESSAGE_HUB_ATTACHED_IO = 0x04;
    private static final int MESSAGE_PORT_VALUE = 0x45;
    private static final int UPDATE_OPERATION = 0x06;

    private final BluetoothLe radio;
    private final LegoSensors sensors;
    private final LegoProperties properties;
    private final LegoVirtualPorts virtualPorts;
    private final byte[] inbox = new byte[MESSAGE_BYTES];
    private final int[] portDevices = new int[EXTERNAL_PORTS];

    LegoNotifications(BluetoothLe radio, LegoSensors sensors, LegoProperties properties,
            LegoVirtualPorts virtualPorts) {
        this.radio = radio;
        this.sensors = sensors;
        this.properties = properties;
        this.virtualPorts = virtualPorts;
    }

    /** Forgets every device type, before a new connection. */
    void clearDevices() {
        for (int i = 0; i < EXTERNAL_PORTS; i++) {
            portDevices[i] = 0;
        }
    }

    /** The device type on external port {@code port}, 0 when none is attached or the port is not one of A to D. */
    int portDevice(int port) {
        return port >= 0 && port < EXTERNAL_PORTS ? portDevices[port] : 0;
    }

    /** Reads every notification waiting on the radio and decodes it; the radio yields when none is waiting. */
    void drain() {
        int length;
        while ((length = radio.read(inbox, MESSAGE_BYTES)) > 0) {
            receive(length);
        }
    }

    /** Decodes one upstream message: [length (1 or 2 bytes), hub id, type, ...]. */
    private void receive(int length) {
        int header = length > 0 && (inbox[0] & 0x80) != 0 ? 2 : 1;
        if (length >= header + 4 && inbox[header + 1] == MESSAGE_HUB_ATTACHED_IO) {
            receiveAttached(length, header);
        } else if (length >= header + 4 && inbox[header + 1] == LegoCommands.MESSAGE_HUB_PROPERTIES
                && inbox[header + 3] == UPDATE_OPERATION) {
            properties.receive(inbox, header + 4, length - header - 4, LegoBytes.unsigned(inbox[header + 2]));
        } else if (length >= header + 4 && inbox[header + 1] == MESSAGE_PORT_VALUE) {
            sensors.report(LegoBytes.unsigned(inbox[header + 2]), inbox, header + 3, length - header - 3);
        }
    }

    /** Hub Attached I/O: the device type on a port, and the virtual ports the hub creates and removes. */
    private void receiveAttached(int length, int header) {
        int port = LegoBytes.unsigned(inbox[header + 2]);
        int event = LegoBytes.unsigned(inbox[header + 3]);
        if (port < EXTERNAL_PORTS) {
            portDevices[port] = event == 0x01 && length >= header + 6
                    ? LegoBytes.unsigned(inbox[header + 4]) | (LegoBytes.unsigned(inbox[header + 5]) << 8) : 0;
        }
        if (event == 0x00) {
            virtualPorts.forget(port);
        } else if (event == 0x02 && length >= header + 8) {
            virtualPorts.announce(port, LegoBytes.unsigned(inbox[header + 6]), LegoBytes.unsigned(inbox[header + 7]));
        }
    }
}
