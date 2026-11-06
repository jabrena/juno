package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.net.BluetoothLe;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * A LEGO Powered Up hub's LEGO Wireless Protocol 3.0, spoken over the board's {@link BluetoothLe} radio. It connects to
 * the first hub advertising the service, decodes each notification into the state {@link PoweredUpPort} reads, and
 * delegates the commands to {@link LegoCommands}, the sensor ports to {@link LegoSensors}, the properties to
 * {@link LegoProperties} and the virtual ports to {@link LegoVirtualPorts}. It knows the radio only through
 * {@link BluetoothLe}, so a unit test drives it with a fake radio. Every buffer is allocated once and reused.
 */
final class LegoWirelessPort implements PoweredUpPort {
    /** The hub's advertised service and its one characteristic, which carries every command and notification. */
    private static final String SERVICE_UUID = "00001623-1212-efde-1623-785feabcd123";
    private static final String CHARACTERISTIC_UUID = "00001624-1212-efde-1623-785feabcd123";

    /** Manufacturer data of an advertising hub: the LEGO company id (little endian), button state, system type id. */
    private static final int LEGO_COMPANY_LOW = 0x97;
    private static final int LEGO_COMPANY_HIGH = 0x03;
    private static final int ADVERTISEMENT_BYTES = 4;

    /** Upstream message types. */
    private static final int EXTERNAL_PORTS = 4;
    private static final int PROPERTIES_TIMEOUT_MILLIS = 300;
    private static final int LINK_TIMEOUT_MILLIS = 500;

    /** The hub types whose LED is on a different port number. */
    private static final int TYPE_DUPLO_TRAIN_HUB = 0x20;
    private static final int TYPE_REMOTE_CONTROL = 0x42;
    private static final int LED_PORT_DUPLO = 0x11;
    private static final int LED_PORT_REMOTE = 0x34;
    private static final int LED_PORT_DEFAULT = 0x32;

    private final BluetoothLe radio;
    private final Clock clock;
    private final LegoCommands commands;
    private final LegoSensors sensors = new LegoSensors();
    private final LegoProperties properties = new LegoProperties();
    private final LegoVirtualPorts virtualPorts = new LegoVirtualPorts();
    private final LegoNotifications notifications;
    private final byte[] advertisement = new byte[ADVERTISEMENT_BYTES];
    private int hubType;

    /** A port over {@code radio}, timing its waits on {@code clock}. Create it once. */
    LegoWirelessPort(BluetoothLe radio, Clock clock) {
        this.radio = radio;
        this.clock = clock;
        this.commands = new LegoCommands(radio);
        this.notifications = new LegoNotifications(radio, sensors, properties, virtualPorts);
    }

    @Override
    public boolean connect(int timeoutMillis) {
        if (isConnected()) {
            return true;
        }
        sensors.clear();
        properties.clear();
        virtualPorts.clear();
        notifications.clearDevices();
        commands.resetLedMode();
        int length = radio.connect(SERVICE_UUID, CHARACTERISTIC_UUID, timeoutMillis, advertisement,
                ADVERTISEMENT_BYTES);
        if (length < 0) {
            return false;
        }
        hubType = length >= ADVERTISEMENT_BYTES
                && LegoBytes.unsigned(advertisement[0]) == LEGO_COMPANY_LOW
                && LegoBytes.unsigned(advertisement[1]) == LEGO_COMPANY_HIGH
                ? LegoBytes.unsigned(advertisement[3]) : 0;
        return true;
    }

    @Override
    public boolean isConnected() {
        return radio.isConnected();
    }

    @Override
    public int hubType() {
        return hubType;
    }

    @Override
    public void setMotorPower(int port, int powerPercent) {
        commands.motorPower(port, powerPercent);
    }

    @Override
    public void brakeMotor(int port) {
        commands.brake(port);
    }

    @Override
    public void holdMotor(int port) {
        commands.hold(port);
    }

    @Override
    public void setLedColor(int color) {
        commands.ledColor(ledPort(), color);
    }

    @Override
    public void setLedRgb(int red, int green, int blue) {
        commands.ledRgb(ledPort(), red, green, blue);
    }

    @Override
    public void enableSensor(int port, int mode) {
        int portId = port & 0xFF;
        int slot = sensors.assign(portId);
        if (slot < 0 || !isConnected()) {
            return;
        }
        sensors.enable(slot, portId);
        commands.enableSensor(portId, mode);
    }

    @Override
    public int readSensor(int port) {
        notifications.drain();
        return sensors.value(port & 0xFF);
    }

    @Override
    public int batteryPercent() {
        ensureProperties();
        notifications.drain();
        return properties.battery();
    }

    @Override
    public boolean buttonPressed() {
        ensureProperties();
        notifications.drain();
        return properties.button();
    }

    @Override
    public int rssi() {
        ensureProperties();
        notifications.drain();
        return properties.rssi();
    }

    @Override
    public int firmwareVersion() {
        ensureProperties();
        notifications.drain();
        return properties.firmware();
    }

    @Override
    public int hardwareVersion() {
        ensureProperties();
        notifications.drain();
        return properties.hardware();
    }

    @Override
    public int portDevice(int port) {
        notifications.drain();
        return notifications.portDevice(port);
    }

    @Override
    public int hubName(byte[] buffer, int capacity) {
        ensureProperties();
        notifications.drain();
        int room = capacity < buffer.length ? capacity : buffer.length;
        return room <= 0 ? 0 : properties.copyName(buffer, room);
    }

    @Override
    public int readSensorValue(int port, int index, int bytesPerValue) {
        notifications.drain();
        return sensors.readValue(port & 0xFF, index, bytesPerValue);
    }

    @Override
    public int sensorReportCount(int port) {
        notifications.drain();
        return sensors.reportCount(port & 0xFF);
    }

    @Override
    public int sensorReportSize(int port) {
        notifications.drain();
        return sensors.reportSize(port & 0xFF);
    }

    /** Pairs two motor ports into one virtual port and waits for the hub to announce its id; -1 when it does not. */
    @Override
    public int linkMotors(int portA, int portB) {
        int a = portA & 0xFF;
        int b = portB & 0xFF;
        int existing = virtualPorts.find(a, b);
        if (existing >= 0) {
            return existing;
        }
        if (!isConnected()) {
            return -1;
        }
        commands.link(portA, portB);
        int started = clock.millis();
        while (true) {
            notifications.drain();
            int id = virtualPorts.find(a, b);
            if (id >= 0) {
                return id;
            }
            if (clock.millis() - started >= LINK_TIMEOUT_MILLIS) {
                return -1;
            }
        }
    }

    @Override
    public void unlinkMotors(int virtualPort) {
        commands.unlink(virtualPort);
        virtualPorts.forget(virtualPort & 0xFF);
    }

    @Override
    public void setLinkedMotorPower(int virtualPort, int first, int second) {
        commands.linkedPower(virtualPort, first, second);
    }

    @Override
    public void brakeLinkedMotors(int virtualPort) {
        commands.brakeLinked(virtualPort);
    }

    @Override
    public void disconnect() {
        radio.disconnect();
        commands.resetLedMode();
    }

    @Override
    public void switchOff() {
        commands.switchOff();
    }

    /** Asks the hub for its properties the first time one is needed, then gives the answers time to arrive. */
    private void ensureProperties() {
        if (!isConnected() || !properties.takeRequest()) {
            return;
        }
        commands.requestProperty(LegoProperties.PROPERTY_BUTTON, LegoCommands.OPERATION_SUBSCRIBE);
        commands.requestProperty(LegoProperties.PROPERTY_RSSI, LegoCommands.OPERATION_SUBSCRIBE);
        commands.requestProperty(LegoProperties.PROPERTY_BATTERY, LegoCommands.OPERATION_SUBSCRIBE);
        commands.requestProperty(LegoProperties.PROPERTY_NAME, LegoCommands.OPERATION_REQUEST);
        commands.requestProperty(LegoProperties.PROPERTY_FIRMWARE, LegoCommands.OPERATION_REQUEST);
        commands.requestProperty(LegoProperties.PROPERTY_HARDWARE, LegoCommands.OPERATION_REQUEST);
        int started = clock.millis();
        while (clock.millis() - started < PROPERTIES_TIMEOUT_MILLIS && !properties.complete()) {
            notifications.drain();
        }
    }

    private int ledPort() {
        return switch (hubType) {
            case TYPE_DUPLO_TRAIN_HUB -> LED_PORT_DUPLO;
            case TYPE_REMOTE_CONTROL -> LED_PORT_REMOTE;
            default -> LED_PORT_DEFAULT;
        };
    }
}
