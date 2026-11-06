package io.github.jabrena.juno.remote.lego.poweredup;

import io.github.jabrena.juno.hardware.net.BluetoothLe;

/**
 * The commands a program sends the hub, each framed as one LEGO Wireless Protocol message and written to the radio when
 * the hub is connected. A command sent while disconnected is dropped, not queued. The frame is built in a buffer allocated
 * once, here.
 */
final class LegoCommands {
    static final int MESSAGE_HUB_ACTION = 0x02;
    static final int MESSAGE_HUB_PROPERTIES = 0x01;
    static final int OPERATION_SUBSCRIBE = 0x02;
    static final int OPERATION_REQUEST = 0x05;

    private static final int MESSAGE_PORT_INPUT_FORMAT = 0x41;
    private static final int MESSAGE_PORT_OUTPUT = 0x81;
    private static final int MESSAGE_VIRTUAL_PORT = 0x61;

    /** Output command feedback flag, and the speed-control, power and direct-mode subcommands. */
    private static final int FEEDBACK = 0x11;
    private static final int SUBCOMMAND_START_SPEED = 0x07;
    private static final int SUBCOMMAND_START_POWER = 0x02;
    private static final int SUBCOMMAND_WRITE_DIRECT = 0x51;
    private static final int HOLD_SPEED = 100;
    private static final int BRAKE = 127;
    private static final int MAX_POWER = 100;

    private static final int OUTBOX_BYTES = 16;
    private static final int NOT_SET = -1;

    private final BluetoothLe radio;
    private final byte[] outbox = new byte[OUTBOX_BYTES];
    private int outgoingLength;
    private int ledMode = NOT_SET;

    LegoCommands(BluetoothLe radio) {
        this.radio = radio;
    }

    /** Forgets the LED's input mode, after a disconnect or a new connection: the next LED command sets it up again. */
    void resetLedMode() {
        ledMode = NOT_SET;
    }

    /** Drives a motor at {@code powerPercent}, clamped to plus or minus 100. */
    void motorPower(int port, int powerPercent) {
        writeMode0(port, clampPower(powerPercent));
    }

    void brake(int port) {
        writeMode0(port, BRAKE);
    }

    /** StartSpeed of 0: the motor's speed control keeps it where it is, so it resists being turned. */
    void hold(int port) {
        begin(MESSAGE_PORT_OUTPUT);
        put(port);
        put(FEEDBACK);
        put(SUBCOMMAND_START_SPEED);
        put(0x00);
        put(HOLD_SPEED);
        put(0x00);
        send();
    }

    void ledColor(int ledPort, int color) {
        useLedMode(ledPort, 0);
        writeMode0(ledPort, color);
    }

    void ledRgb(int ledPort, int red, int green, int blue) {
        useLedMode(ledPort, 1);
        begin(MESSAGE_PORT_OUTPUT);
        put(ledPort);
        put(FEEDBACK);
        put(SUBCOMMAND_WRITE_DIRECT);
        put(0x01);
        put(red);
        put(green);
        put(blue);
        send();
    }

    /** Subscribes {@code port} to {@code mode}: the hub reports every change (delta 1), with notifications on. */
    void enableSensor(int port, int mode) {
        begin(MESSAGE_PORT_INPUT_FORMAT);
        put(port);
        put(mode);
        put(0x01);
        put(0x00);
        put(0x00);
        put(0x00);
        put(0x01);
        send();
    }

    void requestProperty(int property, int operation) {
        begin(MESSAGE_HUB_PROPERTIES);
        put(property);
        put(operation);
        send();
    }

    /** Pairs two motor ports into one virtual port; the hub announces its id in a Hub Attached I/O message. */
    void link(int portA, int portB) {
        begin(MESSAGE_VIRTUAL_PORT);
        put(0x01);
        put(portA);
        put(portB);
        send();
    }

    void unlink(int virtualPort) {
        begin(MESSAGE_VIRTUAL_PORT);
        put(0x00);
        put(virtualPort);
        send();
    }

    /** StartPower with two powers: one message drives both motors of a virtual port. */
    void linkedPower(int virtualPort, int first, int second) {
        writeLinkedPower(virtualPort, clampPower(first), clampPower(second));
    }

    void brakeLinked(int virtualPort) {
        writeLinkedPower(virtualPort, BRAKE, BRAKE);
    }

    void switchOff() {
        begin(MESSAGE_HUB_ACTION);
        put(0x01);
        send();
        ledMode = NOT_SET;
    }

    /** Port output command executed at once with feedback: WriteDirectModeData of one signed byte in mode 0. */
    private void writeMode0(int port, int value) {
        begin(MESSAGE_PORT_OUTPUT);
        put(port);
        put(FEEDBACK);
        put(SUBCOMMAND_WRITE_DIRECT);
        put(0x00);
        put(value);
        send();
    }

    private void writeLinkedPower(int virtualPort, int first, int second) {
        begin(MESSAGE_PORT_OUTPUT);
        put(virtualPort);
        put(FEEDBACK);
        put(SUBCOMMAND_START_POWER);
        put(first);
        put(second);
        send();
    }

    /** Port input format setup: puts the LED in color-index (0) or RGB (1) mode, without notifications. */
    private void useLedMode(int port, int mode) {
        if (ledMode != mode) {
            begin(MESSAGE_PORT_INPUT_FORMAT);
            put(port);
            put(mode);
            put(0x01);
            put(0x00);
            put(0x00);
            put(0x00);
            put(0x00);
            send();
            ledMode = radio.isConnected() ? mode : NOT_SET;
        }
    }

    /** Starts a message of {@code type} for the hub; its payload follows through {@link #put}. */
    private void begin(int type) {
        outbox[1] = 0x00;
        outbox[2] = (byte) type;
        outgoingLength = 3;
    }

    private void put(int value) {
        outbox[outgoingLength] = (byte) value;
        outgoingLength = outgoingLength + 1;
    }

    /** Writes the message begun by {@link #begin} when the hub is connected; its length byte leads the frame. */
    private void send() {
        if (!radio.isConnected()) {
            return;
        }
        outbox[0] = (byte) outgoingLength;
        radio.write(outbox, outgoingLength);
    }

    private static int clampPower(int power) {
        return power > MAX_POWER ? MAX_POWER : (power < -MAX_POWER ? -MAX_POWER : power);
    }
}
