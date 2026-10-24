package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * The RCX infrared framing: a message is {@code 55 FF 00} followed by the opcode, its arguments and a checksum, each
 * byte followed by its complement. Plain Java on top of {@link Infrared}.
 */
public final class RcxLink {
    private final Clock clock;

    /** A link on the board's own timer. Create it once, at startup, and share it with every class that talks to the brick. */
    public RcxLink() {
        this(ArduinoUno.getClock());
    }

    /** A link that times its replies on {@code clock}: a simulated one in a unit test. */
    public RcxLink(Clock clock) {
        this.clock = clock;
    }

    static final int BAUD = 2400;
    static final int OPCODE_REMOTE = 0xD2;
    static final int OPCODE_MESSAGE = 0xF7;
    /** Bit 3 of a repeated opcode toggles, so a brick can tell a retry from a new command. */
    private static final int OPCODE_TOGGLE = 0x08;
    /** A frame's bytes follow each other within a few bit times; a longer silence ends it. */
    private static final int GAP_MICROS = 3000;
    private static final int FIRST_BYTE_MICROS = 1000;
    /** A brick answers a direct command within a few tens of milliseconds. */
    private static final int REPLY_TIMEOUT_MILLIS = 150;
    /** Without a receiver to hear the acknowledgement, leave the brick this long before the next command. */
    private static final int COMMAND_GAP_MILLIS = 60;

    /** The opcode (toggle bit cleared) and arguments of the last frame {@link #receive} accepted. */
    int opcode;
    int argument0;
    int argument1;
    private int lastCommand = -1;
    private boolean receiveEnabled;
    /** The opcode, up to ten arguments and the checksum: the longest reply decoded, the brick's version report. */
    private static final int FRAME_VALUES = 12;
    /**
     * The frame being decoded, allocated once: allocating it while a frame arrives delayed the first byte's
     * decoding past its start bit on the slower boards, so every reply was lost.
     */
    private static final int[] FRAME = new int[FRAME_VALUES];
    /** How long {@link #awaitReply} lets one wait for a start bit run before it checks its deadline again. */
    private static final int REPLY_POLL_MICROS = 20000;

    public void begin(int receivePin, int transmitPin) {
        receiveEnabled = receivePin >= 0;
        Infrared.begin(receivePin, transmitPin, BAUD);
    }

    /**
     * Reads one frame if one is arriving and stores it in the fields above. Returns its argument
     * count, or {@code -1} when there was no valid frame.
     */
    int receive() {
        if (!Infrared.receiving()) {
            return -1;
        }
        return receiveFrame(FIRST_BYTE_MICROS);
    }

    /**
     * Waits up to {@code waitMicros} for a frame's first start bit, then reads the frame as {@link #receive} does.
     * Waiting inside {@link Infrared#readByte} catches the start bit's edge, so the bits are sampled in their middle.
     */
    private int receiveFrame(int waitMicros) {
        int[] data = FRAME;
        int count = 0;
        if (Infrared.readByte(waitMicros) != 0x55 || Infrared.readByte(GAP_MICROS) != 0xFF
                || Infrared.readByte(GAP_MICROS) != 0x00) {
            return -1;
        }
        while (count < FRAME_VALUES) {
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

    /** Argument {@code index} (from 0) of the last frame {@link #receive} or {@link #awaitReply} accepted. */
    int argument(int index) {
        return FRAME[index + 1];
    }

    /** Transmits {@code opcode} with {@code arguments} (0..2) arguments {@code first} and {@code second}. */
    public void send(int opcode, int first, int second, int arguments) {
        send(opcode, first, second, 0, arguments);
    }

    /** Transmits a remote-control command with {@code buttonMask}, {@code RemoteRcx}'s button bits, as the RCX Remote Control does. */
    public void sendButtons(int buttonMask) {
        sendFrame(OPCODE_REMOTE, (buttonMask >> 8) & 0xFF, buttonMask & 0xFF, 0, 2);
    }

    /** Transmits an RCX {@code message} command carrying the low 8 bits of {@code value}. */
    public void sendMessage(int value) {
        send(OPCODE_MESSAGE, value & 0xFF, 0, 1);
    }

    /** Transmits {@code opcode} with {@code arguments} (0..3) arguments {@code first}, {@code second} and {@code third}. */
    void send(int opcode, int first, int second, int third, int arguments) {
        int code = opcode;
        if (code == lastCommand) {
            code ^= OPCODE_TOGGLE;
        }
        lastCommand = code;
        sendFrame(code, first, second, third, arguments);
    }

    private void sendFrame(int code, int first, int second, int third, int arguments) {
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
    void command(int opcode, int first, int second, int third, int arguments) {
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
    int request(int command, int first, int second, int arguments) {
        return request(command, first, second, 0, arguments);
    }

    /** Like the two-argument request, for commands with up to three arguments. */
    int request(int command, int first, int second, int third, int arguments) {
        send(command, first, second, third, arguments);
        return awaitReply(command, REPLY_TIMEOUT_MILLIS);
    }

    /**
     * Waits up to {@code timeoutMillis} for the reply to {@code command}, whose opcode is the command's complement,
     * skipping other frames; returns its argument count, with the arguments in {@link #argument0} and
     * {@link #argument1}, or {@code -1}.
     */
    int awaitReply(int command, int timeoutMillis) {
        int replyOpcode = ~command & 0xFF & ~OPCODE_TOGGLE;
        int started = clock.millis();
        while (clock.millis() - started < timeoutMillis) {
            int count = receiveFrame(REPLY_POLL_MICROS);
            if (count >= 0 && opcode == replyOpcode) {
                return count;
            }
        }
        return -1;
    }

    private void sendPair(int value) {
        Infrared.writeByte(value);
        Infrared.writeByte(~value & 0xFF);
    }
}
