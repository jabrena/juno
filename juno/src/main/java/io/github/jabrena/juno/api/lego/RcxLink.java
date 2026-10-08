package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.ir.Infrared;

/**
 * The RCX family's infrared framing, shared by {@link RcxRemote} and {@link ScoutRemote}: a message is
 * {@code 55 FF 00} followed by the opcode, its arguments and a checksum, each byte followed by its
 * complement. Plain Java on top of {@link Infrared}.
 */
final class RcxLink {
    private RcxLink() {
    }

    static final int BAUD = 2400;
    static final int OPCODE_REMOTE = 0xD2;
    static final int OPCODE_MESSAGE = 0xF7;
    /** Bit 3 of an opcode toggles on each message, so a brick can tell a repeat from a new command. */
    private static final int OPCODE_TOGGLE = 0x08;
    /** A frame's bytes follow each other within a few bit times; a longer silence ends it. */
    private static final int GAP_MICROS = 3000;
    private static final int FIRST_BYTE_MICROS = 1000;
    /** A brick answers a direct command within a few tens of milliseconds. */
    private static final int REPLY_TIMEOUT_MILLIS = 150;
    /** Without a receiver to hear the acknowledgement, leave the brick this long before the next command. */
    private static final int COMMAND_GAP_MILLIS = 60;

    /** The opcode (toggle bit cleared) and arguments of the last frame {@link #receive} accepted. */
    static int opcode;
    static int argument0;
    static int argument1;
    private static boolean toggle;
    private static boolean receiveEnabled;

    static void begin(int receivePin, int transmitPin) {
        receiveEnabled = receivePin >= 0;
        Infrared.begin(receivePin, transmitPin, BAUD);
    }

    /**
     * Reads one frame if one is arriving and stores it in the fields above. Returns its argument
     * count, or {@code -1} when there was no valid frame.
     */
    static int receive() {
        if (!Infrared.receiving()) {
            return -1;
        }
        int[] data = new int[8];
        int count = 0;
        if (Infrared.readByte(FIRST_BYTE_MICROS) != 0x55 || Infrared.readByte(GAP_MICROS) != 0xFF
                || Infrared.readByte(GAP_MICROS) != 0x00) {
            return -1;
        }
        while (count < 8) {
            int value = Infrared.readByte(GAP_MICROS);
            if (value < 0) {
                break;
            }
            int complement = Infrared.readByte(GAP_MICROS);
            if (complement < 0 || (value ^ complement) != 0xFF) {
                return -1;
            }
            data[count] = value;
            count++;
        }
        if (count < 2) {
            return -1;
        }
        int sum = 0;
        for (int i = 0; i < count - 1; i++) {
            sum += data[i];
        }
        if ((sum & 0xFF) != data[count - 1]) {
            return -1;
        }
        opcode = data[0] & ~OPCODE_TOGGLE;
        argument0 = count > 2 ? data[1] : 0;
        argument1 = count > 3 ? data[2] : 0;
        return count - 2;
    }

    /** Transmits {@code opcode} with {@code arguments} (0..2) arguments {@code first} and {@code second}. */
    static void send(int opcode, int first, int second, int arguments) {
        send(opcode, first, second, 0, arguments);
    }

    /** Transmits {@code opcode} with {@code arguments} (0..3) arguments {@code first}, {@code second} and {@code third}. */
    static void send(int opcode, int first, int second, int third, int arguments) {
        toggle = !toggle;
        int code = toggle ? opcode | OPCODE_TOGGLE : opcode;
        int sum = code;
        Infrared.writeByte(0x55);
        Infrared.writeByte(0xFF);
        Infrared.writeByte(0x00);
        sendPair(code);
        if (arguments > 0) {
            sum += first;
            sendPair(first);
        }
        if (arguments > 1) {
            sum += second;
            sendPair(second);
        }
        if (arguments > 2) {
            sum += third;
            sendPair(third);
        }
        sendPair(sum & 0xFF);
    }

    /**
     * Sends a command whose reply the caller does not need. With the receiver wired it waits for the brick's
     * acknowledgement, so the brick's answer and the next transmission do not collide on the air; without a
     * receiver it leaves a short gap instead.
     */
    static void command(int opcode, int first, int second, int third, int arguments) {
        if (receiveEnabled) {
            request(opcode, first, second, third, arguments);
        } else {
            send(opcode, first, second, third, arguments);
            Delay.millis(COMMAND_GAP_MILLIS);
        }
    }

    /**
     * Sends a direct command and waits for the brick's reply, whose opcode is the complement of the
     * command's. The receiver module also hears the vehicle's own LED, so frames with other opcodes (the
     * echo of the command itself) are skipped. Returns the reply's argument count, with the arguments in
     * {@link #argument0} and {@link #argument1}, or {@code -1} if no reply came.
     */
    static int request(int command, int first, int second, int arguments) {
        return request(command, first, second, 0, arguments);
    }

    /** Like the two-argument request, for commands with up to three arguments. */
    static int request(int command, int first, int second, int third, int arguments) {
        send(command, first, second, third, arguments);
        int replyOpcode = ~command & 0xFF & ~OPCODE_TOGGLE;
        int started = Clock.millis();
        while (Clock.millis() - started < REPLY_TIMEOUT_MILLIS) {
            int count = receive();
            if (count >= 0 && opcode == replyOpcode) {
                return count;
            }
        }
        return -1;
    }

    private static void sendPair(int value) {
        Infrared.writeByte(value);
        Infrared.writeByte(~value & 0xFF);
    }
}
